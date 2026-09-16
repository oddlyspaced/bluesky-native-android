package io.nativestarter.showcase.bluesky.core.network

import io.nativestarter.showcase.bluesky.core.session.InMemorySessionStore
import io.nativestarter.showcase.bluesky.core.session.NotSignedInException
import io.nativestarter.showcase.bluesky.core.session.Session
import io.nativestarter.showcase.bluesky.core.session.SessionManager
import io.nativestarter.showcase.bluesky.core.session.SessionState
import io.nativestarter.showcase.bluesky.testing.Fixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/** Header split from RN src/state/session/clients.ts, token refresh, sign-out and account scoping. */
class XrpcClientTest {
    private val server = MockWebServer().apply { start() }
    private val proxy = "did:plc:bw7ad3erl7btq6qwf66yqiov#bsky_appview"
    private val client = XrpcClient(server.url("/").toString().trimEnd('/'), proxy)
    private val me = Session("did:plc:me", "alice.test", "A1", "R1")

    @After fun tearDown() = server.shutdown()

    private fun signedIn(session: Session = me, store: InMemorySessionStore = InMemorySessionStore(session)) =
        SessionManager(client, store).also { runBlocking { it.restore() } }

    @Test
    fun appviewReadsCarryProxyHeaderAndPdsWritesDoNot() = runBlocking {
        val repo = XrpcBskyRepository(client, signedIn(), accountDid = me.did)

        server.enqueue(MockResponse().setBody(Fixtures.text("getTimeline.json")))
        repo.timeline()
        val read = server.takeRequest()
        assertEquals("/xrpc/app.bsky.feed.getTimeline?limit=30", read.path)
        assertEquals(proxy, read.getHeader("atproto-proxy"))
        assertEquals("Bearer A1", read.getHeader("Authorization"))

        server.enqueue(MockResponse().setBody("""{"uri":"at://did:plc:me/app.bsky.feed.like/1","cid":"c"}"""))
        repo.like(StrongRef("at://p", "cid"))
        val write = server.takeRequest()
        assertEquals("/xrpc/com.atproto.repo.createRecord", write.path)
        assertNull(write.getHeader("atproto-proxy"))
        val body = write.body.readUtf8()
        assertTrue(body, body.contains("\"collection\":\"app.bsky.feed.like\""))
        assertTrue(body, body.contains("\"repo\":\"did:plc:me\""))
    }

    @Test
    fun restoreReadsTheStoreOnce() = runBlocking {
        val sessions = SessionManager(client, InMemorySessionStore(me))
        assertEquals(SessionState.Restoring, sessions.state.value)
        sessions.restore()
        assertEquals(SessionState.SignedIn(me), sessions.state.value)
        sessions.logout()
        sessions.restore()
        assertEquals("restore after sign-out must not bring the session back", SessionState.SignedOut, sessions.state.value)
    }

    @Test
    fun expiredTokenRefreshesOnceAndRetries() = runBlocking {
        val store = InMemorySessionStore(me.copy(accessJwt = "OLD"))
        val repo = XrpcBskyRepository(client, signedIn(store = store), accountDid = me.did)

        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"ExpiredToken","message":"Token has expired"}"""))
        server.enqueue(MockResponse().setBody("""{"accessJwt":"NEW","refreshJwt":"R2","did":"did:plc:me","handle":"alice.test"}"""))
        server.enqueue(MockResponse().setBody(Fixtures.text("getProfile.json")))

        assertEquals("Alice", repo.profile("alice.test").displayName)
        server.takeRequest()
        val refresh = server.takeRequest()
        assertEquals("/xrpc/com.atproto.server.refreshSession", refresh.path)
        assertEquals("Bearer R1", refresh.getHeader("Authorization"))
        assertEquals("Bearer NEW", server.takeRequest().getHeader("Authorization"))
        assertEquals("NEW", store.load()?.accessJwt)
    }

    @Test
    fun concurrentExpiredTokensShareOneRefresh() = runBlocking {
        val refreshes = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path!!.startsWith("/xrpc/com.atproto.server.refreshSession") -> {
                    refreshes.incrementAndGet()
                    // A slow refresh makes the other callers queue behind the lock.
                    MockResponse().setBodyDelay(200, TimeUnit.MILLISECONDS)
                        .setBody("""{"accessJwt":"NEW","refreshJwt":"R2","did":"did:plc:me","handle":"alice.test"}""")
                }
                request.getHeader("Authorization") == "Bearer OLD" ->
                    MockResponse().setResponseCode(400).setBody("""{"error":"ExpiredToken","message":"expired"}""")
                else -> MockResponse().setBody(Fixtures.text("getProfile.json"))
            }
        }
        val repo = XrpcBskyRepository(client, signedIn(me.copy(accessJwt = "OLD")), accountDid = me.did)
        val results = (1..4).map { async(Dispatchers.IO) { repo.profile("alice.test").displayName } }.awaitAll()
        assertEquals(List(4) { "Alice" }, results)
        assertEquals("the single-use refresh token must be spent once", 1, refreshes.get())
    }

    @Test
    fun terminalRefreshFailureSignsOut() = runBlocking {
        val store = InMemorySessionStore(me.copy(accessJwt = "OLD"))
        val sessions = signedIn(store = store)
        val repo = XrpcBskyRepository(client, sessions, accountDid = me.did)
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"ExpiredToken","message":"expired"}"""))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"ExpiredToken","message":"Token has been revoked"}"""))

        try {
            repo.profile("alice.test")
            fail("expected the refresh failure to propagate")
        } catch (e: XrpcException) {
            assertEquals("ExpiredToken", e.error)
        }
        assertEquals(SessionState.SignedOut, sessions.state.value)
        assertNull(store.load())
    }

    @Test
    fun networkFailureDuringRefreshDoesNotSignOut() = runBlocking {
        val sessions = signedIn(me.copy(accessJwt = "OLD"))
        val repo = XrpcBskyRepository(client, sessions, accountDid = me.did)
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"ExpiredToken","message":"expired"}"""))
        server.enqueue(MockResponse().setResponseCode(502).setBody("""{"error":"UpstreamFailure","message":"bad gateway"}"""))
        runCatching { repo.profile("alice.test") }
        assertTrue(sessions.state.value is SessionState.SignedIn)
    }

    @Test
    fun signOutDuringRefreshStaysSignedOut() = runBlocking {
        val paths = CopyOnWriteArrayList<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                paths += request.path!!.substringBefore('?') + " " + request.getHeader("Authorization")
                return when {
                    request.path!!.startsWith("/xrpc/com.atproto.server.refreshSession") -> MockResponse()
                        .setBodyDelay(300, TimeUnit.MILLISECONDS)
                        .setBody("""{"accessJwt":"NEW","refreshJwt":"R2","did":"did:plc:me","handle":"alice.test"}""")
                    request.path!!.startsWith("/xrpc/com.atproto.server.deleteSession") -> MockResponse().setBody("{}")
                    request.getHeader("Authorization") == "Bearer OLD" ->
                        MockResponse().setResponseCode(400).setBody("""{"error":"ExpiredToken","message":"expired"}""")
                    else -> MockResponse().setBody(Fixtures.text("getProfile.json"))
                }
            }
        }
        val store = InMemorySessionStore(me.copy(accessJwt = "OLD"))
        val sessions = signedIn(store = store)
        val repo = XrpcBskyRepository(client, sessions, accountDid = me.did)

        val call = launch(Dispatchers.IO) { runCatching { repo.profile("alice.test") } }
        withTimeout(5_000) { while (paths.none { it.contains("refreshSession") }) delay(10) }
        // Sign-out must not queue behind the in-flight refresh: the mutex guards the state, not the network
        // call (review B2). The refresh here is held for 300 ms, so a blocking sign-out would take about that.
        val signOutMs = measureTimeMillis { sessions.logout() }
        call.join()

        assertEquals(SessionState.SignedOut, sessions.state.value)
        assertTrue("sign-out waited $signOutMs ms for the in-flight refresh", signOutMs < 200)
        assertNull("a finished refresh must not write tokens after sign-out", store.load())
        // The session is revoked with the refresh token held at sign-out. The newer token the in-flight refresh
        // returns is dropped with the rest of its result, so it is never used here.
        assertTrue(paths.toString(), paths.any { it == "/xrpc/com.atproto.server.deleteSession Bearer R1" })
    }

    /**
     * `preferences()` and `pinnedFeeds()` read one document, so a screen that needs both issues one request
     * (review B7). A failed read caches nothing, so a retry still reaches the network (review B3).
     */
    @Test
    fun preferencesAndPinnedFeedsShareOneRequest() = runBlocking {
        val repo = XrpcBskyRepository(client, signedIn(), accountDid = me.did)
        server.enqueue(MockResponse().setBody(Fixtures.text("getPreferences.json")))

        assertEquals("top", repo.preferences().threadSort)
        assertEquals(listOf("Following"), repo.pinnedFeeds().map { it.displayName })

        assertEquals("one getPreferences for both reads", 1, server.requestCount)
        assertEquals("/xrpc/app.bsky.actor.getPreferences", server.takeRequest().path)
    }

    @Test
    fun aFailedPreferencesReadIsNotCached() = runBlocking {
        val repo = XrpcBskyRepository(client, signedIn(), accountDid = me.did)
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"InternalServerError"}"""))
        server.enqueue(MockResponse().setBody(Fixtures.text("getPreferences.json")))

        assertTrue(runCatching { repo.preferences() }.isFailure)
        assertEquals("top", repo.preferences().threadSort)
        assertEquals("the retry went to the network", 2, server.requestCount)
    }

    @Test
    fun callsForAnotherAccountFail() = runBlocking {
        val repo = XrpcBskyRepository(client, signedIn(), accountDid = "did:plc:someone-else")
        try {
            repo.profile("alice.test")
            fail("expected NotSignedInException")
        } catch (e: NotSignedInException) {
            assertEquals(0, server.requestCount)
        }
    }
}

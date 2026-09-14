package io.nativestarter.showcase.bluesky.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import io.nativestarter.showcase.bluesky.core.session.InMemorySessionStore
import io.nativestarter.showcase.bluesky.core.session.Session
import io.nativestarter.showcase.bluesky.core.session.SessionManager
import io.nativestarter.showcase.bluesky.testing.Fixtures

/** Checks the header split from RN src/state/session/clients.ts and token refresh. */
class XrpcClientTest {
    private val server = MockWebServer().apply { start() }
    private val proxy = "did:plc:bw7ad3erl7btq6qwf66yqiov#bsky_appview"
    private val client = XrpcClient(server.url("/").toString().trimEnd('/'), proxy)

    @After fun tearDown() = server.shutdown()

    @Test
    fun appviewReadsCarryProxyHeaderAndPdsWritesDoNot() = runTest {
        val sessions = SessionManager(client, InMemorySessionStore(Session("did:plc:me", "alice.test", "A1", "R1")))
        val repo = XrpcBskyRepository(client, sessions)

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
        assert(body.contains("\"collection\":\"app.bsky.feed.like\"")) { body }
        assert(body.contains("\"repo\":\"did:plc:me\"")) { body }
    }

    @Test
    fun expiredTokenRefreshesOnceAndRetries() = runTest {
        val store = InMemorySessionStore(Session("did:plc:me", "alice.test", "OLD", "R1"))
        val sessions = SessionManager(client, store)
        val repo = XrpcBskyRepository(client, sessions)

        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"ExpiredToken","message":"Token has expired"}"""))
        server.enqueue(MockResponse().setBody("""{"accessJwt":"NEW","refreshJwt":"R2","did":"did:plc:me","handle":"alice.test"}"""))
        server.enqueue(MockResponse().setBody(Fixtures.text("getProfile.json")))

        val profile = repo.profile("alice.test")
        assertEquals("Alice", profile.displayName)
        server.takeRequest()
        val refresh = server.takeRequest()
        assertEquals("/xrpc/com.atproto.server.refreshSession", refresh.path)
        assertEquals("Bearer R1", refresh.getHeader("Authorization"))
        assertEquals("Bearer NEW", server.takeRequest().getHeader("Authorization"))
        assertEquals("NEW", store.load()?.accessJwt)
    }
}

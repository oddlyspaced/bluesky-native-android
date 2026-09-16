package io.nativestarter.showcase.bluesky.core.session

import io.nativestarter.showcase.bluesky.core.common.runCatchingCancellable
import io.nativestarter.showcase.bluesky.core.network.SessionResponse
import io.nativestarter.showcase.bluesky.core.network.XrpcClient
import io.nativestarter.showcase.bluesky.core.network.XrpcException
import io.nativestarter.showcase.bluesky.core.network.XrpcJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

@Serializable
data class Session(val did: String, val handle: String, val accessJwt: String, val refreshJwt: String)

sealed interface SessionState {
    /** The persisted session is still being read. The UI shows nothing interactive yet. */
    data object Restoring : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val session: Session) : SessionState
}

class NotSignedInException : IllegalStateException("Not signed in")

/** Persists the signed-in session. Implementations do I/O, so every call suspends. */
interface SessionStore {
    suspend fun load(): Session?
    suspend fun save(session: Session)
    suspend fun clear()
}

class InMemorySessionStore(private var session: Session? = null) : SessionStore {
    override suspend fun load() = session
    override suspend fun save(session: Session) {
        this.session = session
    }
    override suspend fun clear() {
        session = null
    }
}

/**
 * Owns the session state.
 *
 * - Token refresh is single-flight: the first caller starts one [Deferred] and the others await it, so the
 *   single-use refresh token is spent once. A caller whose failed token is no longer current reuses the token
 *   another caller already refreshed.
 * - The mutex guards the session state only, never a network call (review B2). Holding it across the refresh
 *   made [logout] wait for the whole OkHttp timeout, so sign-out appeared to do nothing. Sign-out now takes the
 *   lock, marks the session signed out and bumps [generation]; an in-flight refresh that finishes afterwards
 *   sees the changed generation and drops its tokens instead of reviving the session.
 * - A terminal refresh failure (401, or 400 ExpiredToken / InvalidToken / AuthenticationRequired) signs out.
 * - Sign-out revokes the server session with `com.atproto.server.deleteSession` (best effort), using the refresh
 *   token held at sign-out. It does not wait for an in-flight refresh to publish a newer one.
 */
class SessionManager(
    private val client: XrpcClient,
    private val store: SessionStore,
    /** Owns the shared refresh, so no single caller's cancellation can cancel it for the others. */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Restoring)
    val state: StateFlow<SessionState> = _state.asStateFlow()
    private val mutex = Mutex()

    /** The refresh every waiting caller awaits, or null when none is running. Guarded by [mutex]. */
    private var refreshInFlight: Deferred<Session>? = null

    /** Bumped by every sign-in and sign-out. A refresh started under an older generation drops its result. */
    private var generation = 0

    val current: Session? get() = (_state.value as? SessionState.SignedIn)?.session

    suspend fun restore() {
        mutex.withLock {
            if (_state.value != SessionState.Restoring) return
            val stored = try {
                store.load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _state.value = if (stored != null) SessionState.SignedIn(stored) else SessionState.SignedOut
        }
    }

    suspend fun login(identifier: String, password: String): Session {
        val text = client.procedure(
            "com.atproto.server.createSession",
            XrpcClient.createSessionBody(identifier, password),
            accessJwt = null,
        )
        val response = XrpcJson.decodeFromString(SessionResponse.serializer(), text)
        val session = Session(response.did, response.handle, response.accessJwt, response.refreshJwt)
        mutex.withLock {
            generation++
            refreshInFlight = null
            store.save(session)
            _state.value = SessionState.SignedIn(session)
        }
        return session
    }

    suspend fun logout() {
        val old = mutex.withLock {
            val previous = current
            // Sign out first, under the lock and without waiting for any network call (review B2).
            generation++
            refreshInFlight = null
            _state.value = SessionState.SignedOut
            store.clear()
            previous
        }
        if (old != null) {
            runCatchingCancellable {
                client.procedure("com.atproto.server.deleteSession", JsonObject(emptyMap()), old.refreshJwt)
            }
        }
    }

    /**
     * Runs [block] with the current session. Refreshes once on `ExpiredToken` and retries.
     * [expectedDid] scopes a call to one account: if another account is signed in, the call fails.
     */
    suspend fun <T> authed(expectedDid: String? = null, block: suspend (Session) -> T): T {
        val session = current ?: throw NotSignedInException()
        if (expectedDid != null && session.did != expectedDid) throw NotSignedInException()
        return try {
            block(session)
        } catch (e: XrpcException) {
            if (!e.isExpiredToken()) throw e
            block(refresh(session))
        }
    }

    private suspend fun refresh(failed: Session): Session {
        val pending = mutex.withLock {
            val latest = current ?: throw NotSignedInException()
            if (latest.did != failed.did) throw NotSignedInException()
            // Another caller refreshed while this one was in its failed call: reuse the new token.
            if (latest.accessJwt != failed.accessJwt) return latest
            val gen = generation
            refreshInFlight ?: scope.async { exchange(latest, gen) }.also { refreshInFlight = it }
        }
        // Awaited outside the lock, so a sign-out can take it while the refresh is still on the network.
        return pending.await()
    }

    /** One `com.atproto.server.refreshSession` call and the state write that follows it. */
    private suspend fun exchange(latest: Session, gen: Int): Session {
        val text = try {
            client.procedure("com.atproto.server.refreshSession", JsonObject(emptyMap()), latest.refreshJwt)
        } catch (e: XrpcException) {
            mutex.withLock {
                refreshInFlight = null
                // Only sign out the session this refresh belonged to.
                if (generation == gen && e.isTerminalAuthFailure()) {
                    _state.value = SessionState.SignedOut
                    runCatchingCancellable { store.clear() }
                }
            }
            throw e
        }
        val obj = XrpcJson.parseToJsonElement(text).jsonObject
        return mutex.withLock {
            refreshInFlight = null
            // The user signed out, or another account signed in, while this refresh was on the network.
            if (generation != gen) throw NotSignedInException()
            val next = latest.copy(
                accessJwt = obj.getValue("accessJwt").jsonPrimitive.content,
                refreshJwt = obj.getValue("refreshJwt").jsonPrimitive.content,
            )
            store.save(next)
            _state.value = SessionState.SignedIn(next)
            next
        }
    }
}

private val TERMINAL_AUTH_ERRORS = setOf("ExpiredToken", "InvalidToken", "AuthenticationRequired")

fun XrpcException.isExpiredToken(): Boolean = status == 400 && error == "ExpiredToken"

fun XrpcException.isTerminalAuthFailure(): Boolean = status == 401 || (status == 400 && error in TERMINAL_AUTH_ERRORS)

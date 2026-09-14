package io.nativestarter.showcase.bluesky.core.session

import io.nativestarter.showcase.bluesky.core.common.runCatchingCancellable
import io.nativestarter.showcase.bluesky.core.network.SessionResponse
import io.nativestarter.showcase.bluesky.core.network.XrpcClient
import io.nativestarter.showcase.bluesky.core.network.XrpcException
import io.nativestarter.showcase.bluesky.core.network.XrpcJson
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
 * - Token refresh is single-flight: one [Mutex] serialises refreshes, and a caller whose failed token is
 *   no longer current reuses the token another caller already refreshed. Refresh tokens are single use.
 * - A refresh and a sign-out are serialised by the same mutex, so a refresh that finishes cannot bring back
 *   a session the user signed out of, and one account cannot overwrite another.
 * - A terminal refresh failure (401, or 400 ExpiredToken / InvalidToken / AuthenticationRequired) signs out.
 * - Sign-out revokes the server session with `com.atproto.server.deleteSession` (best effort).
 */
class SessionManager(private val client: XrpcClient, private val store: SessionStore) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Restoring)
    val state: StateFlow<SessionState> = _state.asStateFlow()
    private val mutex = Mutex()

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
            store.save(session)
            _state.value = SessionState.SignedIn(session)
        }
        return session
    }

    suspend fun logout() {
        val old = mutex.withLock {
            val previous = current
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

    private suspend fun refresh(failed: Session): Session = mutex.withLock {
        val latest = current ?: throw NotSignedInException()
        if (latest.did != failed.did) throw NotSignedInException()
        // Another caller refreshed while this one waited for the lock.
        if (latest.accessJwt != failed.accessJwt) return@withLock latest
        val text = try {
            client.procedure("com.atproto.server.refreshSession", JsonObject(emptyMap()), latest.refreshJwt)
        } catch (e: XrpcException) {
            if (e.isTerminalAuthFailure()) {
                _state.value = SessionState.SignedOut
                runCatchingCancellable { store.clear() }
            }
            throw e
        }
        val obj = XrpcJson.parseToJsonElement(text).jsonObject
        val next = latest.copy(
            accessJwt = obj.getValue("accessJwt").jsonPrimitive.content,
            refreshJwt = obj.getValue("refreshJwt").jsonPrimitive.content,
        )
        store.save(next)
        _state.value = SessionState.SignedIn(next)
        next
    }
}

private val TERMINAL_AUTH_ERRORS = setOf("ExpiredToken", "InvalidToken", "AuthenticationRequired")

fun XrpcException.isExpiredToken(): Boolean = status == 400 && error == "ExpiredToken"

fun XrpcException.isTerminalAuthFailure(): Boolean = status == 401 || (status == 400 && error in TERMINAL_AUTH_ERRORS)

package io.nativestarter.showcase.bluesky.core.session

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.nativestarter.showcase.bluesky.core.network.SessionResponse
import io.nativestarter.showcase.bluesky.core.network.XrpcClient
import io.nativestarter.showcase.bluesky.core.network.XrpcException
import io.nativestarter.showcase.bluesky.core.network.XrpcJson

data class Session(val did: String, val handle: String, val accessJwt: String, val refreshJwt: String)

/** Persists the signed-in session. Screens and view models never touch the platform store directly. */
interface SessionStore {
    fun load(): Session?
    fun save(session: Session)
    fun clear()
}

/**
 * Showcase storage for the local dev network only (seeded test accounts).
 * A production port would keep the refresh token in Android Keystore-backed storage;
 * see migration-pack skills/replace-expo-module for the MMKV/keychain recipe.
 */
class PrefsSessionStore(context: Context) : SessionStore {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    override fun load(): Session? {
        val did = prefs.getString("did", null) ?: return null
        return Session(
            did = did,
            handle = prefs.getString("handle", null) ?: return null,
            accessJwt = prefs.getString("accessJwt", null) ?: return null,
            refreshJwt = prefs.getString("refreshJwt", null) ?: return null,
        )
    }

    override fun save(session: Session) {
        prefs.edit()
            .putString("did", session.did)
            .putString("handle", session.handle)
            .putString("accessJwt", session.accessJwt)
            .putString("refreshJwt", session.refreshJwt)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}

class InMemorySessionStore(private var session: Session? = null) : SessionStore {
    override fun load() = session
    override fun save(session: Session) { this.session = session }
    override fun clear() { session = null }
}

/** Owns the session and retries a call once after refreshing an expired access token. */
class SessionManager(private val client: XrpcClient, private val store: SessionStore) {
    private val _session = MutableStateFlow(store.load())
    val session: StateFlow<Session?> = _session.asStateFlow()

    suspend fun login(identifier: String, password: String): Session {
        val text = client.procedure(
            "com.atproto.server.createSession",
            XrpcClient.createSessionBody(identifier, password),
            accessJwt = null,
        )
        val response = XrpcJson.decodeFromString(SessionResponse.serializer(), text)
        val session = Session(response.did, response.handle, response.accessJwt, response.refreshJwt)
        store.save(session)
        _session.value = session
        return session
    }

    fun logout() {
        store.clear()
        _session.value = null
    }

    /** Runs [block] with a valid access token. Refreshes and retries once on `ExpiredToken`. */
    suspend fun <T> authed(block: suspend (Session) -> T): T {
        val current = _session.value ?: throw IllegalStateException("Not signed in")
        return try {
            block(current)
        } catch (e: XrpcException) {
            if (e.status != 400 || e.error != "ExpiredToken") throw e
            block(refresh(current))
        }
    }

    private suspend fun refresh(current: Session): Session {
        val text = try {
            client.procedure("com.atproto.server.refreshSession", JsonObject(emptyMap()), current.refreshJwt)
        } catch (e: XrpcException) {
            logout()
            throw e
        }
        val obj = XrpcJson.parseToJsonElement(text) as JsonObject
        val next = current.copy(
            accessJwt = obj.getValue("accessJwt").jsonPrimitive.content,
            refreshJwt = obj.getValue("refreshJwt").jsonPrimitive.content,
        )
        store.save(next)
        _session.value = next
        return next
    }
}

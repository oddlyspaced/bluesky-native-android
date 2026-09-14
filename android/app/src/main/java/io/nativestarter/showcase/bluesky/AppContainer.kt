package io.nativestarter.showcase.bluesky

import android.app.Application
import android.content.Context
import io.nativestarter.showcase.bluesky.core.network.BskyRepository
import io.nativestarter.showcase.bluesky.core.network.PostInteractions
import io.nativestarter.showcase.bluesky.core.network.XrpcBskyRepository
import io.nativestarter.showcase.bluesky.core.network.XrpcClient
import io.nativestarter.showcase.bluesky.core.session.KeystoreSessionStore
import io.nativestarter.showcase.bluesky.core.session.SessionManager
import io.nativestarter.showcase.bluesky.core.session.SessionStore

/** Process-wide dependencies. One instance, owned by [BlueskyApp]. */
class AppContainer(
    context: Context,
    store: SessionStore = KeystoreSessionStore(context),
) {
    val client = XrpcClient(BuildConfig.PDS_URL, BuildConfig.APPVIEW_PROXY)
    val sessionManager = SessionManager(client, store)

    private var account: AccountScope? = null

    /** The dependencies of one signed-in account. A new account gets a new scope with empty post state. */
    @Synchronized
    fun accountFor(did: String): AccountScope =
        account?.takeIf { it.did == did } ?: AccountScope(did, client, sessionManager).also { account = it }
}

/** Created once per signed-in account and kept for the whole session, independent of recomposition. */
class AccountScope(val did: String, client: XrpcClient, sessionManager: SessionManager) {
    val repository: BskyRepository = XrpcBskyRepository(client, sessionManager, accountDid = did)
    val interactions = PostInteractions(repository)
}

class BlueskyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

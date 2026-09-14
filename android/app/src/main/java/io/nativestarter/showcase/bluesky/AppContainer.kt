package io.nativestarter.showcase.bluesky

import android.app.Application
import android.content.Context
import io.nativestarter.showcase.bluesky.core.network.BskyRepository
import io.nativestarter.showcase.bluesky.core.network.PostInteractions
import io.nativestarter.showcase.bluesky.core.network.XrpcBskyRepository
import io.nativestarter.showcase.bluesky.core.network.XrpcClient
import io.nativestarter.showcase.bluesky.core.session.PrefsSessionStore
import io.nativestarter.showcase.bluesky.core.session.SessionManager

/** Manual dependency graph. One instance per process, owned by [BlueskyApp]. */
class AppContainer(context: Context) {
    val client = XrpcClient(BuildConfig.PDS_URL, BuildConfig.APPVIEW_PROXY)
    val sessionManager = SessionManager(client, PrefsSessionStore(context))
    val repository: BskyRepository = XrpcBskyRepository(client, sessionManager)
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

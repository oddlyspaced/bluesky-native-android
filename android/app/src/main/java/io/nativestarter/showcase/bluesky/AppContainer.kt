package io.nativestarter.showcase.bluesky

import android.app.Application
import android.content.Context
import android.os.Process
import android.os.SystemClock
import io.nativestarter.showcase.bluesky.core.analytics.Analytics
import io.nativestarter.showcase.bluesky.core.analytics.JsonlEventLog
import io.nativestarter.showcase.bluesky.core.analytics.NoopAnalytics
import io.nativestarter.showcase.bluesky.core.network.BskyRepository
import io.nativestarter.showcase.bluesky.core.network.PostInteractions
import io.nativestarter.showcase.bluesky.core.network.XrpcBskyRepository
import io.nativestarter.showcase.bluesky.core.network.XrpcClient
import io.nativestarter.showcase.bluesky.core.platform.Haptics
import io.nativestarter.showcase.bluesky.core.platform.VibratorHaptics
import io.nativestarter.showcase.bluesky.core.session.KeystoreSessionStore
import io.nativestarter.showcase.bluesky.core.session.SessionManager
import io.nativestarter.showcase.bluesky.core.session.SessionStore
import io.nativestarter.showcase.bluesky.designsystem.Toaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

/** Process-wide dependencies. One instance, owned by [BlueskyApp]. */
class AppContainer(
    context: Context,
    store: SessionStore = KeystoreSessionStore(context),
    /** Debug builds write RN-named events to `files/events.jsonl`; release builds send nothing. */
    val analytics: Analytics = if (BuildConfig.DEBUG) {
        JsonlEventLog(File(context.filesDir, "events.jsonl"), CoroutineScope(SupervisorJob() + Dispatchers.IO))
    } else {
        NoopAnalytics
    },
    val haptics: Haptics = VibratorHaptics(context.applicationContext),
) {
    val client = XrpcClient(BuildConfig.PDS_URL, BuildConfig.APPVIEW_PROXY)
    val sessionManager = SessionManager(client, store)
    val toaster = Toaster()

    /** Process start in elapsed realtime, for the RN `init` event (`initMs`). */
    val processStartElapsedMs: Long = Process.getStartElapsedRealtime()
    fun millisSinceProcessStart(): Long = SystemClock.elapsedRealtime() - processStartElapsedMs

    private var account: AccountScope? = null

    /** The dependencies of one signed-in account. A new account gets a new scope with empty post state. */
    @Synchronized
    fun accountFor(did: String): AccountScope =
        account?.takeIf { it.did == did }
            ?: AccountScope(did, client, sessionManager, analytics).also { account = it }
}

/** Created once per signed-in account and kept for the whole session, independent of recomposition. */
class AccountScope(
    val did: String,
    client: XrpcClient,
    sessionManager: SessionManager,
    val analytics: Analytics,
) {
    val repository: BskyRepository = XrpcBskyRepository(client, sessionManager, accountDid = did)
    val interactions = PostInteractions(repository)
    val softReset = SoftResetBus()
}

/** RN `emitSoftReset` / `listenSoftReset` (src/state/events.ts), scoped to a tab route. */
class SoftResetBus {
    private val events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val resets: SharedFlow<String> = events.asSharedFlow()
    fun emit(route: String) {
        events.tryEmit(route)
    }
}

class BlueskyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

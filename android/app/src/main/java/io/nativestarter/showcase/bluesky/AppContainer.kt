package io.nativestarter.showcase.bluesky

import android.app.Application
import android.content.Context
import android.os.Process
import android.os.SystemClock
import io.nativestarter.showcase.bluesky.core.analytics.Analytics
import io.nativestarter.showcase.bluesky.core.analytics.JsonlEventLog
import io.nativestarter.showcase.bluesky.core.analytics.NoopAnalytics
import io.nativestarter.showcase.bluesky.core.common.runCatchingCancellable
import io.nativestarter.showcase.bluesky.core.network.BskyRepository
import io.nativestarter.showcase.bluesky.core.network.PostCache
import io.nativestarter.showcase.bluesky.core.network.PostSources
import io.nativestarter.showcase.bluesky.core.network.PostInteractions
import io.nativestarter.showcase.bluesky.core.network.ProfileViewDetailed
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    /** RN `TickEveryMinuteProvider`: one tick source for the process, started with the app (review P4). */
    val minuteTicker = io.nativestarter.showcase.bluesky.core.common.MinuteTicker(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    /** Process start in elapsed realtime, for the RN `init` event (`initMs`). */
    val processStartElapsedMs: Long = Process.getStartElapsedRealtime()
    fun millisSinceProcessStart(): Long = SystemClock.elapsedRealtime() - processStartElapsedMs

    private var account: AccountScope? = null

    /** The dependencies of one signed-in account. A new account gets a new scope with empty post state. */
    @Synchronized
    fun accountFor(did: String): AccountScope {
        account?.takeIf { it.did == did }?.let { return it }
        account?.close()
        return AccountScope(did, XrpcBskyRepository(client, sessionManager, accountDid = did), analytics).also { account = it }
    }
}

/**
 * Created once per signed-in account and kept for the whole session, independent of recomposition (review A9).
 * [scope] runs on the main thread because the toggle queues are single-threaded; it is cancelled on account change.
 */
class AccountScope(
    val did: String,
    val repository: BskyRepository,
    val analytics: Analytics,
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val _ownProfile = MutableStateFlow<ProfileViewDetailed?>(null)
    /** RN `useProfileQuery(currentAccount.did)`: the composer and bottom bar avatar and `likerClout` read it. */
    val ownProfile: StateFlow<ProfileViewDetailed?> = _ownProfile.asStateFlow()
    val interactions = PostInteractions(repository, scope, analytics) { _ownProfile.value?.followersCount }
    val softReset = SoftResetBus()
    /** Loaded posts, for thread placeholders (RN `getThreadPlaceholder`). */
    val postCache = PostCache()
    /** The feed each opened post came from (RN `setUnstablePostSource`). */
    val postSources = PostSources()

    fun loadOwnProfile() {
        scope.launch {
            runCatchingCancellable { repository.profile(did) }.onSuccess { _ownProfile.value = it }
        }
    }

    fun setOwnProfile(profile: ProfileViewDetailed) {
        if (profile.did == did) _ownProfile.value = profile
    }

    fun close() = scope.cancel()
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

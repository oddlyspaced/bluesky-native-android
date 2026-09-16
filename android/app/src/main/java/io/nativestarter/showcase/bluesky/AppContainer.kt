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
    /** One OkHttp client, so XRPC calls and image loads share one connection pool for the host (review B21). */
    private val http = okhttp3.OkHttpClient()
    val client = XrpcClient(BuildConfig.PDS_URL, BuildConfig.APPVIEW_PROXY, http)
    val sessionManager = SessionManager(client, store)
    val toaster = Toaster()

    /** Remote avatars and banners: memory cache and downsampling behind an interface (review A15). */
    val imageLoader: io.nativestarter.showcase.bluesky.core.image.ImageLoader<android.graphics.Bitmap> =
        io.nativestarter.showcase.bluesky.core.image.androidImageLoader(http)

    /** RN `TickEveryMinuteProvider`: one tick source for the process, started with the app (review P4). */
    val minuteTicker = io.nativestarter.showcase.bluesky.core.common.MinuteTicker(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    init {
        // Parse the Inter faces before the first screen composes, never during composition (review B10).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            io.nativestarter.showcase.bluesky.designsystem.text.prewarmFonts(context.applicationContext)
        }
    }

    /** Process start in elapsed realtime, for the RN `init` event (`initMs`). */
    val processStartElapsedMs: Long = Process.getStartElapsedRealtime()
    fun millisSinceProcessStart(): Long = SystemClock.elapsedRealtime() - processStartElapsedMs

    /** RN `onNavigationReady` events, once per account key (the tree remounts under `key={currentAccount?.did}`). */
    val navigationReady = io.nativestarter.showcase.bluesky.shell.NavigationReadyEvents(analytics, ::millisSinceProcessStart)

    private val accounts = AccountScopes { did ->
        AccountScope(did, XrpcBskyRepository(client, sessionManager, accountDid = did), analytics)
    }

    /** The dependencies of one signed-in account. A new account gets a new scope with empty post state. */
    fun accountFor(did: String): AccountScope = accounts.forDid(did)

    /** Sign-out ends the account's lifetime (review B1). */
    fun closeAccount() = accounts.close()
}

/**
 * Holds the scope of the signed-in account. One account is live at a time, and both signing in as a different
 * DID and signing out end the previous account's lifetime, so its PostCache, shadows, post sources, preferences,
 * own profile and coroutine scope are released. Closing on sign-out was missing, so signing back in as the same
 * DID reused stale state and the account's main-immediate scope stayed live (review B1).
 */
class AccountScopes(private val create: (String) -> AccountScope) {
    private var current: AccountScope? = null

    @Synchronized
    fun forDid(did: String): AccountScope {
        current?.takeIf { it.did == did }?.let { return it }
        current?.close()
        return create(did).also { current = it }
    }

    @Synchronized
    fun close() {
        current?.close()
        current = null
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
    /** RN `usePreferencesQuery` data, shared by the feed and thread screens. */
    val preferences = io.nativestarter.showcase.bluesky.core.network.PreferencesCache()

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

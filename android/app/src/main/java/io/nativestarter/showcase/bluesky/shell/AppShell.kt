package io.nativestarter.showcase.bluesky.shell

import io.nativestarter.showcase.bluesky.designsystem.IconSize
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.nativestarter.showcase.bluesky.core.navigation.AppNavigator
import io.nativestarter.showcase.bluesky.core.navigation.RouteEntry
import io.nativestarter.showcase.bluesky.core.navigation.TabBackStacks
import io.nativestarter.showcase.bluesky.feature.HomeRoute
import io.nativestarter.showcase.bluesky.feature.ProfileRoute
import io.nativestarter.showcase.bluesky.feature.ThreadRoute
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.nativestarter.showcase.bluesky.AccountScope
import io.nativestarter.showcase.bluesky.AppContainer
import io.nativestarter.showcase.bluesky.BuildConfig
import io.nativestarter.showcase.bluesky.R
import io.nativestarter.showcase.bluesky.core.analytics.LocalAnalytics
import io.nativestarter.showcase.bluesky.core.common.runCatchingCancellable
import io.nativestarter.showcase.bluesky.core.platform.LocalHaptics
import io.nativestarter.showcase.bluesky.core.session.Session
import io.nativestarter.showcase.bluesky.core.session.SessionState
import io.nativestarter.showcase.bluesky.designsystem.BskyIcon
import io.nativestarter.showcase.bluesky.designsystem.ComposeFab
import io.nativestarter.showcase.bluesky.designsystem.FontSize
import io.nativestarter.showcase.bluesky.designsystem.Hairline
import io.nativestarter.showcase.bluesky.designsystem.LocalAlf
import io.nativestarter.showcase.bluesky.designsystem.LocalNow
import io.nativestarter.showcase.bluesky.designsystem.LocalToaster
import io.nativestarter.showcase.bluesky.designsystem.NotInShowcase
import io.nativestarter.showcase.bluesky.designsystem.ProvideAlfTheme
import io.nativestarter.showcase.bluesky.designsystem.Radius
import io.nativestarter.showcase.bluesky.designsystem.ScreenHeader
import io.nativestarter.showcase.bluesky.designsystem.ShowcaseMark
import io.nativestarter.showcase.bluesky.designsystem.Space
import io.nativestarter.showcase.bluesky.designsystem.ToastHost
import io.nativestarter.showcase.bluesky.designsystem.Txt
import io.nativestarter.showcase.bluesky.designsystem.UserAvatar
import io.nativestarter.showcase.bluesky.designsystem.alfText
import io.nativestarter.showcase.bluesky.designsystem.pressable
import io.nativestarter.showcase.bluesky.designsystem.AlfButton
import io.nativestarter.showcase.bluesky.designsystem.ButtonColor
import io.nativestarter.showcase.bluesky.designsystem.ButtonSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Route names live in core/navigation so features can use them without the shell. */
typealias Routes = io.nativestarter.showcase.bluesky.core.navigation.Routes

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun BlueskyRoot(container: AppContainer) {
    val sessionState by container.sessionManager.state.collectAsStateWithLifecycle()
    val now by container.minuteTicker.now.collectAsStateWithLifecycle()
    // RN NavigationContainer onReady: `router:navigate {}` then `init {initMs}`.
    LaunchedEffect(Unit) {
        container.analytics.metric("router:navigate")
        container.analytics.metric("init", mapOf("initMs" to container.millisSinceProcessStart()))
    }
    ProvideAlfTheme {
        CompositionLocalProvider(
            LocalNow provides now,
            io.nativestarter.showcase.bluesky.core.image.LocalImageLoader provides container.imageLoader,
            LocalHaptics provides container.haptics,
            LocalToaster provides container.toaster,
            LocalAnalytics provides container.analytics,
        ) {
            // testTagsAsResourceId exposes Modifier.testTag as the resource id Maestro/UiAutomator match on.
            Box(Modifier.fillMaxSize().background(LocalAlf.current.bg).semantics { testTagsAsResourceId = true }) {
                when (val state = sessionState) {
                    SessionState.Restoring -> Unit
                    SessionState.SignedOut -> SignedOutScreen(container)
                    is SessionState.SignedIn -> {
                        val account = remember(state.session.did) { container.accountFor(state.session.did) }
                        SignedInShell(account, state.session)
                    }
                }
                ToastHost(container.toaster)
            }
        }
    }
}

/** RN `SplashScreen` (signed out) plus the debug-only e2e controls from `TestCtrls.e2e.tsx`. */
@Composable
private fun SignedOutScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val theme = LocalAlf.current
    var message by remember { mutableStateOf<String?>(null) }
    fun signIn(identifier: String) {
        scope.launch {
            runCatchingCancellable { container.sessionManager.login(identifier, "hunter2") }
                // RN TestCtrls calls login(..., 'LoginForm'), which records `account:loggedIn`.
                .onSuccess { container.analytics.metric("account:loggedIn", mapOf("logContext" to "LoginForm", "withPassword" to true)) }
                .onFailure { message = "Sign in failed: ${it.message}" }
        }
    }
    Box(Modifier.fillMaxSize().background(theme.bg).navigationBarsPadding()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = Space._2xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            ShowcaseMark(76.dp)
            Spacer(Modifier.height(Space.lg))
            Txt("Native showcase", alfText(FontSize._2xl, FontWeight.Bold), align = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth().testTag("signinOrCreateAccount"), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                // RN SplashScreen: "Create account" primary large, "Sign in" secondary large.
                AlfButton(
                    "Create account",
                    { message = "Create account: Not part of this showcase" },
                    Modifier.fillMaxWidth(),
                    size = ButtonSize.Large,
                    color = ButtonColor.Primary,
                    testTag = "createAccountButton",
                )
                AlfButton(
                    "Sign in",
                    {
                        container.analytics.metric("splash:signInPressed")
                        message = "Sign in form: Not part of this showcase. Debug builds sign in through the e2e control."
                    },
                    Modifier.fillMaxWidth(),
                    size = ButtonSize.Large,
                    color = ButtonColor.Secondary,
                    testTag = "signInButton",
                )
            }
            Txt(
                message.orEmpty(),
                alfText(FontSize.sm, color = theme.textContrastMedium),
                Modifier.padding(vertical = Space.lg).height(40.dp),
                align = TextAlign.Center,
            )
        }
        if (BuildConfig.DEBUG) {
            // RN TestCtrls: 1x1 pressables at top 100, right 0. Same testIDs so one Maestro flow drives both apps.
            Column(Modifier.align(Alignment.TopEnd).padding(top = 100.dp)) {
                Box(Modifier.size(1.dp).background(Color.Red).testTag("e2eSignInAlice").pressable("Sign in as Alice") { signIn("alice.test") })
                Box(Modifier.size(1.dp).background(Color.Red).testTag("e2eSignInBob").pressable("Sign in as Bob") { signIn("bob.test") })
            }
        }
    }
}

/**
 * Per-tab back stacks (review A16), held across recomposition and Activity recreation. Each back-stack entry has its
 * own ViewModelStore, cleared when the entry leaves its stack.
 */
class ShellRouterViewModel : ViewModel() {
    val stacks = TabBackStacks(Routes.TABS)
    private val stores = HashMap<Long, ViewModelStore>()

    /** Bumped on every stack change, so the shell recomposes. */
    var version by mutableIntStateOf(0)
        private set

    fun owner(entry: RouteEntry): ViewModelStoreOwner {
        val store = stores.getOrPut(entry.id) { ViewModelStore() }
        return object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }

    fun changed(removed: List<RouteEntry> = emptyList()) {
        removed.forEach { stores.remove(it.id)?.clear() }
        version++
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}

/**
 * The signed-in shell: bottom bar, unread polling, and the selected tab's current screen. It does not create screen
 * view models; the route factories in `feature/FeatureRoutes.kt` do, inside the entry's ViewModelStoreOwner.
 * Only the current entry is composed; the saved state of the other entries stays in a SaveableStateHolder.
 */
@Composable
private fun SignedInShell(account: AccountScope, session: Session) {
    val analytics = LocalAnalytics.current
    val router: ShellRouterViewModel = viewModel(key = "router-${session.did}")
    val saveable = rememberSaveableStateHolder()
    @Suppress("UNUSED_VARIABLE") val version = router.version
    val stacks = router.stacks
    val current = stacks.current

    val navigator = remember(router, saveable) {
        fun removed(entries: List<RouteEntry>) {
            entries.forEach { saveable.removeState(it.id) }
            router.changed(entries)
        }
        object : AppNavigator {
            override fun openThread(uri: String) {
                stacks.push(Routes.THREAD, uri)
                router.changed()
            }

            override fun openNotInShowcase(title: String) {
                stacks.push(Routes.NOT_IN_SHOWCASE, title)
                router.changed()
            }

            override fun selectTab(tab: String) {
                stacks.select(tab)
                router.changed()
            }

            override fun back() {
                when (val result = stacks.back()) {
                    is TabBackStacks.Back.Popped -> removed(listOf(result.removed))
                    TabBackStacks.Back.ToFirstTab -> router.changed()
                    TabBackStacks.Back.NotHandled -> Unit
                }
            }

            fun pressTab(tab: String) {
                when (val press = stacks.press(tab)) {
                    TabBackStacks.Press.Switched -> router.changed()
                    // InsideAtRoot: soft reset (scroll to top and refresh).
                    TabBackStacks.Press.SoftReset -> account.softReset.emit(tab)
                    // Inside: pop the tab's stack to its root.
                    is TabBackStacks.Press.PoppedToRoot -> removed(press.removed)
                }
            }
        }
    }
    BackHandler(enabled = stacks.canGoBack()) { navigator.back() }

    // RN onStateChange: `router:navigate {from: previousScreen}` on every navigation state change.
    var previousEntry by remember { mutableStateOf<RouteEntry?>(null) }
    LaunchedEffect(current.id) {
        val previous = previousEntry
        if (previous != null && previous.id != current.id) {
            analytics.metric("router:navigate", mapOf("from" to Routes.rnScreenName(previous.route)))
        }
        previousEntry = current
    }

    val unread: UnreadNotificationsViewModel = viewModel(
        key = "unread-${session.did}",
        factory = viewModelFactory { initializer { UnreadNotificationsViewModel(account.repository) } },
    )
    val badge by unread.badge.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(unread) {
        while (true) {
            delay(UnreadPolicy.UPDATE_INTERVAL_MS)
            // RN skips the check while `AppState.currentState !== 'active'`.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) unread.check(isPoll = true)
        }
    }
    LaunchedEffect(account) { account.loadOwnProfile() }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(current.id) {
                saveable.SaveableStateProvider(current.id) {
                    val owner = remember(current.id) { router.owner(current) }
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                        RouteContent(current, account, session, navigator)
                    }
                }
            }
            if (current.route == Routes.HOME || current.route == Routes.PROFILE) {
                ComposeFab(Modifier.align(Alignment.BottomEnd).padding(end = Space._2xl, bottom = Space.md).testTag("composeFAB")) {
                    navigator.openNotInShowcase("New post")
                }
            }
        }
        BottomBar(
            selected = stacks.selected,
            badge = badge,
            // ThreadComposePrompt hides the bottom bar border (useHideBottomBarBorderForScreen).
            hideBorder = current.route == Routes.THREAD,
            onSelect = { target ->
                analytics.metric("nav:click", mapOf("item" to Routes.navItem(target), "surface" to "bottomBar"))
                navigator.pressTab(target)
            },
        )
    }
}

/** Screen factory: one composable per route name. */
@Composable
private fun RouteContent(entry: RouteEntry, account: AccountScope, session: Session, navigator: AppNavigator) {
    when (entry.route) {
        Routes.HOME -> HomeRoute(account, session, navigator)
        Routes.SEARCH -> NotInShowcase("Search")
        Routes.MESSAGES -> NotInShowcase("Chat")
        Routes.NOTIFICATIONS -> NotInShowcase("Notifications")
        Routes.PROFILE -> ProfileRoute(account, session, navigator)
        Routes.THREAD -> ThreadRoute(account, session, entry.arg.orEmpty(), navigator)
        Routes.NOT_IN_SHOWCASE -> {
            val title = entry.arg.orEmpty()
            Column(
                Modifier.fillMaxSize().background(LocalAlf.current.bg)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
            ) {
                ScreenHeader(title, onBack = navigator::back)
                NotInShowcase(title, Modifier.weight(1f))
            }
        }
    }
}

/**
 * RN `BottomBar` (src/view/shell/bottom-bar/BottomBar.tsx): hairline top border (background colour when
 * [hideBorder]), paddingHorizontal 8, paddingBottom clamp(inset, 15, 60); each control flex 1 with paddingTop 13 /
 * paddingBottom 4, iconWidth 28 with per-icon adjustments; notification badge at left 52% / top 8 in primary_500,
 * label 12 semibold white, maxFontSizeMultiplier 1.5.
 *
 * Accessibility follows what RN actually exposes: `Btn` drops `accessibilityRole="tab"` and PressableScale sets
 * `button`, so every item is a button with a label; Notifications has the unread hint.
 */
@Composable
fun BottomBar(
    selected: String,
    badge: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    hideBorder: Boolean = false,
) {
    val theme = LocalAlf.current
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().coerceIn(15.dp, 60.dp)
    Column(modifier.fillMaxWidth().background(theme.bg)) {
        Hairline(if (hideBorder) theme.bg else theme.borderContrastLow)
        Row(Modifier.fillMaxWidth().padding(start = Space.sm, end = Space.sm, bottom = bottomInset)) {
            BarButton("bottomBarHomeBtn", "Home", Modifier.weight(1f), onClick = { onSelect(Routes.HOME) }) {
                BskyIcon(if (selected == Routes.HOME) R.drawable.ic_home_filled else R.drawable.ic_home, 29.dp, theme.text)
            }
            BarButton("bottomBarSearchBtn", "Search", Modifier.weight(1f), onClick = { onSelect(Routes.SEARCH) }) {
                BskyIcon(R.drawable.ic_search, 30.dp, theme.text, Modifier.offset(y = (-1).dp))
            }
            BarButton("bottomBarMessagesBtn", "Chat", Modifier.weight(1f), onClick = { onSelect(Routes.MESSAGES) }) {
                BskyIcon(R.drawable.ic_message, 27.dp, theme.text)
            }
            BarButton(
                "bottomBarNotificationsBtn",
                "Notifications",
                Modifier.weight(1f),
                badge = badge.takeIf { it.isNotEmpty() },
                hint = UnreadPolicy.hint(badge),
                onClick = { onSelect(Routes.NOTIFICATIONS) },
            ) {
                BskyIcon(if (selected == Routes.NOTIFICATIONS) R.drawable.ic_bell_filled else R.drawable.ic_bell, IconSize.xl, theme.text)
            }
            BarButton("bottomBarProfileBtn", "Profile", Modifier.weight(1f), onClick = { onSelect(Routes.PROFILE) }) {
                val atProfile = selected == Routes.PROFILE
                Box(
                    Modifier
                        .size(28.dp)
                        .then(if (atProfile) Modifier.border(1.dp, theme.text, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    UserAvatar(null, if (atProfile) 25.dp else 26.dp)
                }
            }
        }
    }
}

@Composable
private fun BarButton(
    tag: String,
    label: String,
    modifier: Modifier,
    badge: String? = null,
    hint: String = "",
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val theme = LocalAlf.current
    BoxWithConstraints(
        modifier
            .testTag(tag)
            .then(if (hint.isNotEmpty()) Modifier.semantics { stateDescription = hint } else Modifier)
            .pressable(label, onClick),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = BottomBarLayout.itemPaddingTop, bottom = Space.xs), contentAlignment = Alignment.Center) { icon() }
        if (badge != null) {
            Box(
                Modifier
                    .offset(x = maxWidth * 0.52f, y = 8.dp)
                    .clip(RoundedCornerShape(Radius.full))
                    .background(theme.primary)
                    .padding(start = BottomBarLayout.badgePaddingHorizontal, end = BottomBarLayout.badgePaddingHorizontal, top = BottomBarLayout.badgePaddingTop, bottom = Space._2xs)
                    .semantics { contentDescription = "" },
            ) {
                Txt(
                    badge,
                    alfText(12.sp, FontWeight.SemiBold, Color.White, includeFontPadding = false),
                    maxFontScale = 1.5f,
                )
            }
        }
    }
}

/** RN `BottomBarStyles` literals with no ALF space token: ctrl paddingTop 13; notificationCount paddingHorizontal 5, paddingTop 1. */
private object BottomBarLayout {
    val itemPaddingTop = 13.dp
    val badgePaddingHorizontal = 5.dp
    val badgePaddingTop = 1.dp
}

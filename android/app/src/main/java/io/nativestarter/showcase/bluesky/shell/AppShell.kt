package io.nativestarter.showcase.bluesky.shell

import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.nativestarter.showcase.bluesky.AccountScope
import io.nativestarter.showcase.bluesky.AppContainer
import io.nativestarter.showcase.bluesky.BuildConfig
import io.nativestarter.showcase.bluesky.R
import io.nativestarter.showcase.bluesky.core.analytics.LocalAnalytics
import io.nativestarter.showcase.bluesky.core.common.runCatchingCancellable
import io.nativestarter.showcase.bluesky.core.network.PostView
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
import io.nativestarter.showcase.bluesky.designsystem.post.PostActions
import io.nativestarter.showcase.bluesky.designsystem.pressable
import io.nativestarter.showcase.bluesky.designsystem.rememberMinuteTick
import io.nativestarter.showcase.bluesky.feature.feed.FeedViewModel
import io.nativestarter.showcase.bluesky.feature.feed.HomeScreen
import io.nativestarter.showcase.bluesky.feature.profile.ProfileScreen
import io.nativestarter.showcase.bluesky.feature.profile.ProfileViewModel
import io.nativestarter.showcase.bluesky.feature.thread.ThreadScreen
import io.nativestarter.showcase.bluesky.feature.thread.ThreadViewModel
import io.nativestarter.showcase.bluesky.feature.thread.ThreadCallbacks
import io.nativestarter.showcase.bluesky.core.network.PostSource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import io.nativestarter.showcase.bluesky.feature.feed.HomeCallbacks
import io.nativestarter.showcase.bluesky.designsystem.text.RichSegment
import io.nativestarter.showcase.bluesky.designsystem.text.RichTextModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val MESSAGES = "messages"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
    const val THREAD = "thread/{uri}"
    const val NOT_IN_SHOWCASE = "notInShowcase/{title}"

    val TABS = listOf(HOME, SEARCH, MESSAGES, NOTIFICATIONS, PROFILE)

    fun thread(uri: String) = "thread/" + Uri.encode(uri)
    fun notInShowcase(title: String) = "notInShowcase/" + Uri.encode(title)

    /** RN route names, as `router:navigate {from}` reports them (src/Navigation.tsx). */
    fun rnScreenName(route: String?): String? = when (route) {
        HOME -> "Home"
        SEARCH -> "Search"
        MESSAGES -> "Messages"
        NOTIFICATIONS -> "Notifications"
        PROFILE -> "MyProfile"
        THREAD -> "PostThread"
        NOT_IN_SHOWCASE -> "NotInShowcase"
        else -> null
    }

    /** RN `TAB_TO_NAV_ITEM` (src/lib/routes/tab-to-nav-item.ts). */
    fun navItem(tab: String): String = when (tab) {
        HOME -> "home"
        SEARCH -> "search"
        MESSAGES -> "chat"
        NOTIFICATIONS -> "notifications"
        else -> "profile"
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun BlueskyRoot(container: AppContainer) {
    val sessionState by container.sessionManager.state.collectAsStateWithLifecycle()
    val now = rememberMinuteTick()
    // RN NavigationContainer onReady: `router:navigate {}` then `init {initMs}`.
    LaunchedEffect(Unit) {
        container.analytics.metric("router:navigate")
        container.analytics.metric("init", mapOf("initMs" to container.millisSinceProcessStart()))
    }
    ProvideAlfTheme {
        CompositionLocalProvider(
            LocalNow provides now,
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
                PillButton("createAccountButton", "Create account", theme.primary, Color.White) {
                    message = "Create account: Not part of this showcase"
                }
                PillButton("signInButton", "Sign in", theme.bgContrast50, theme.textContrastMedium) {
                    container.analytics.metric("splash:signInPressed")
                    message = "Sign in form: Not part of this showcase. Debug builds sign in through the e2e control."
                }
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

@Composable
private fun PillButton(tag: String, label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clip(RoundedCornerShape(Radius.full))
            .background(bg)
            .pressable(label, onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(label, alfText(FontSize.md, FontWeight.Medium, fg))
    }
}

@Composable
private fun SignedInShell(account: AccountScope, session: Session) {
    val nav = rememberNavController()
    val analytics = LocalAnalytics.current
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    var lastTab by rememberSaveable { mutableStateOf(Routes.HOME) }
    if (route in Routes.TABS) lastTab = route!!

    // RN onStateChange: `router:navigate {from: previousScreen}` on every navigation state change.
    var previousRoute by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(route) {
        val previous = previousRoute
        if (route != null && previous != null && previous != route) {
            analytics.metric("router:navigate", mapOf("from" to Routes.rnScreenName(previous)))
        }
        if (route != null) previousRoute = route
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

    val openPost: (PostView) -> Unit = { nav.navigate(Routes.thread(it.uri)) }
    LaunchedEffect(account) { account.loadOwnProfile() }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) { HomeRoute(account, session, openPost, nav) }
                composable(Routes.SEARCH) { NotInShowcase("Search") }
                composable(Routes.MESSAGES) { NotInShowcase("Chat") }
                composable(Routes.NOTIFICATIONS) { NotInShowcase("Notifications") }
                composable(Routes.PROFILE) { ProfileRoute(account, session, openPost) }
                composable(Routes.THREAD) { entry ->
                    val uri = Uri.decode(entry.arguments?.getString("uri").orEmpty())
                    ThreadRoute(account, session, uri, nav, openPost)
                }
                composable(Routes.NOT_IN_SHOWCASE) { entry ->
                    val title = Uri.decode(entry.arguments?.getString("title").orEmpty())
                    Column(
                        Modifier.fillMaxSize().background(LocalAlf.current.bg)
                            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
                    ) {
                        ScreenHeader(title, onBack = { nav.popBackStack() })
                        NotInShowcase(title, Modifier.weight(1f))
                    }
                }
            }
            if (route == Routes.HOME || route == Routes.PROFILE) {
                ComposeFab(Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 12.dp).testTag("composeFAB")) {
                    nav.navigate(Routes.notInShowcase("New post"))
                }
            }
        }
        BottomBar(
            selected = lastTab,
            badge = badge,
            // ThreadComposePrompt hides the bottom bar border (useHideBottomBarBorderForScreen).
            hideBorder = route == Routes.THREAD,
            onSelect = { target ->
                analytics.metric("nav:click", mapOf("item" to Routes.navItem(target), "surface" to "bottomBar"))
                when {
                    // InsideAtRoot: soft reset (scroll to top and refresh).
                    route == target -> account.softReset.emit(target)
                    // Inside: pop the tab's stack to its root.
                    lastTab == target -> nav.popBackStack(target, inclusive = false)
                    else -> nav.selectTab(target)
                }
            },
        )
    }
}

private fun NavHostController.selectTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun HomeRoute(account: AccountScope, session: Session, openPost: (PostView) -> Unit, nav: NavHostController) {
    val analytics = LocalAnalytics.current
    val uriHandler = LocalUriHandler.current
    val vm: FeedViewModel = viewModel(
        key = "feed-${session.did}",
        factory = viewModelFactory { initializer { FeedViewModel(account.repository, account.interactions, account.analytics, session.did, account.postCache) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    // RN Home.tsx useFocusEffect: `home:feedDisplayed` with reason 'focus'.
    LifecycleResumeEffect(Unit) {
        analytics.metric(
            "home:feedDisplayed",
            mapOf("index" to state.selectedTab, "feedType" to "following", "feedUrl" to "following", "reason" to "focus"),
        )
        onPauseOrDispose { }
    }
    val softResets = remember(account) { account.softReset.resets.filter { it == Routes.HOME }.map { } }
    HomeScreen(
        state = state,
        softResets = softResets,
        currentAccountDid = session.did,
        callbacks = HomeCallbacks(
            onRefresh = { vm.refresh(FeedViewModel.REASON_PULL_TO_REFRESH) },
            onSelectTab = vm::selectTab,
            onRequestSoftReset = { account.softReset.emit(Routes.HOME) },
            onSoftReset = { vm.refresh(FeedViewModel.REASON_SOFT_RESET) },
            onNotInShowcase = { nav.navigate(Routes.notInShowcase(it)) },
            onEndReached = vm::onEndReached,
            onRetry = vm::retry,
            onRetryLoadMore = vm::retryLoadMore,
            onPostSeen = vm::onPostSeen,
            onComposerPressed = vm::onComposerPressed,
            onOpenFullThread = { nav.navigate(Routes.thread(it)) },
            postActions = { row ->
                val via = FeedViewModel.viaOf(row.reason)
                PostActions(
                    onOpen = {
                        vm.onPostPressed(it)
                        // RN PostFeedItem `onBeforePress`: stash the source feed for the thread (setUnstablePostSource).
                        account.postSources.set(it.uri, PostSource(feedDescriptor = "following", via = via))
                        openPost(it)
                    },
                    onToggleLike = { vm.toggleLike(it, via) },
                    onToggleRepost = { vm.toggleRepost(it, via) },
                    onOpenAuthor = {
                        vm.onAuthorPressed(it)
                        if (it.author.did == session.did) nav.selectTab(Routes.PROFILE) else nav.navigate(Routes.notInShowcase("Profile"))
                    },
                    // RN opens the composer, which is outside the slice.
                    onReply = {
                        vm.onReplyPressed(it)
                        nav.navigate(Routes.notInShowcase("Reply"))
                    },
                    onRichText = { segment -> openRichText(segment, uriHandler, nav) },
                    onOpenReposter = { nav.navigate(Routes.notInShowcase("Profile")) },
                    onQuote = { nav.navigate(Routes.notInShowcase("Quote post")) },
                )
            },
        ),
    )
}

/** RN RichText taps: links open through the proxy URL (`shouldProxyLinks`); mentions and tags open screens outside the slice. */
private fun openRichText(segment: RichSegment, uriHandler: UriHandler, nav: NavHostController) {
    when (segment) {
        is RichSegment.Link -> runCatching { uriHandler.openUri(RichTextModel.proxiedUrl(segment.uri)) }
        is RichSegment.Mention -> nav.navigate(Routes.notInShowcase("Profile"))
        is RichSegment.Tag -> nav.navigate(Routes.notInShowcase("Hashtag"))
        is RichSegment.Plain -> Unit
    }
}

@Composable
private fun ProfileRoute(account: AccountScope, session: Session, openPost: (PostView) -> Unit) {
    val vm: ProfileViewModel = viewModel(
        key = "profile-${session.did}",
        factory = viewModelFactory { initializer { ProfileViewModel(session.did, account.repository, account.interactions) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(account) {
        account.softReset.resets.collect { if (it == Routes.PROFILE) vm.refresh() }
    }
    ProfileScreen(
        state = state,
        actions = PostActions(openPost, vm::toggleLike, vm::toggleRepost),
        onRefresh = vm::refresh,
        onSelectTab = vm::selectTab,
    )
}

@Composable
private fun ThreadRoute(account: AccountScope, session: Session, uri: String, nav: NavHostController, openPost: (PostView) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val vm: ThreadViewModel = viewModel(
        key = "thread-$uri",
        factory = viewModelFactory {
            initializer {
                ThreadViewModel(
                    anchorUri = uri,
                    repository = account.repository,
                    interactions = account.interactions,
                    analytics = account.analytics,
                    userDid = session.did,
                    postCache = account.postCache,
                    source = account.postSources.consume(uri),
                )
            }
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val ownProfile by account.ownProfile.collectAsStateWithLifecycle()
    ThreadScreen(
        state = state,
        ownAvatar = ownProfile?.avatar,
        callbacks = ThreadCallbacks(
            onBack = { nav.popBackStack() },
            onThreadOptions = { nav.navigate(Routes.notInShowcase("Thread options")) },
            onRetry = vm::retry,
            onShowOtherReplies = vm::showOtherReplies,
            onOpenThread = { nav.navigate(Routes.thread(it)) },
            onAnchorLaidOut = vm::onAnchorLaidOut,
            onStartReached = vm::onStartReached,
            onEndReached = vm::onEndReached,
            onItemSeen = vm::onItemSeen,
            // The composer is outside the slice.
            onReplyToAnchor = { nav.navigate(Routes.notInShowcase("Reply")) },
            postActions = { item ->
                PostActions(
                    onOpen = { if (it.uri != uri) openPost(it) },
                    onToggleLike = vm::toggleLike,
                    onToggleRepost = vm::toggleRepost,
                    onOpenAuthor = {
                        if (item.depth == 0) vm.onAnchorAuthorPressed(it)
                        if (it.author.did == session.did) nav.selectTab(Routes.PROFILE) else nav.navigate(Routes.notInShowcase("Profile"))
                    },
                    onReply = {
                        vm.onReplyPressed(it)
                        nav.navigate(Routes.notInShowcase("Reply"))
                    },
                    onRichText = { segment -> openRichText(segment, uriHandler, nav) },
                    onOpenReposter = { nav.navigate(Routes.notInShowcase("Profile")) },
                    onQuote = { nav.navigate(Routes.notInShowcase("Quote post")) },
                )
            },
        ),
    )
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
                BskyIcon(if (selected == Routes.NOTIFICATIONS) R.drawable.ic_bell_filled else R.drawable.ic_bell, 28.dp, theme.text)
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
        Box(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 4.dp), contentAlignment = Alignment.Center) { icon() }
        if (badge != null) {
            Box(
                Modifier
                    .offset(x = maxWidth * 0.52f, y = 8.dp)
                    .clip(RoundedCornerShape(Radius.full))
                    .background(theme.primary)
                    .padding(start = 5.dp, end = 5.dp, top = 1.dp, bottom = 2.dp)
                    .semantics { contentDescription = "" },
            ) {
                Txt(
                    badge,
                    alfText(12.sp, FontWeight.SemiBold, Color.White)
                        .copy(lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified, lineHeightStyle = null),
                    maxFontScale = 1.5f,
                )
            }
        }
    }
}

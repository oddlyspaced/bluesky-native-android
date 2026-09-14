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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.nativestarter.showcase.bluesky.AppContainer
import io.nativestarter.showcase.bluesky.BuildConfig
import io.nativestarter.showcase.bluesky.R
import io.nativestarter.showcase.bluesky.core.network.BskyRepository
import io.nativestarter.showcase.bluesky.core.network.PostView
import io.nativestarter.showcase.bluesky.core.session.Session
import io.nativestarter.showcase.bluesky.designsystem.BskyIcon
import io.nativestarter.showcase.bluesky.designsystem.ComposeFab
import io.nativestarter.showcase.bluesky.designsystem.FontSize
import io.nativestarter.showcase.bluesky.designsystem.Hairline
import io.nativestarter.showcase.bluesky.designsystem.LocalNow
import io.nativestarter.showcase.bluesky.designsystem.NotInShowcase
import io.nativestarter.showcase.bluesky.designsystem.Palette
import io.nativestarter.showcase.bluesky.designsystem.Radius
import io.nativestarter.showcase.bluesky.designsystem.ScreenHeader
import io.nativestarter.showcase.bluesky.designsystem.ShowcaseMark
import io.nativestarter.showcase.bluesky.designsystem.Space
import io.nativestarter.showcase.bluesky.designsystem.Theme
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

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val MESSAGES = "messages"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
    const val THREAD = "thread/{uri}"
    const val NOT_IN_SHOWCASE = "notInShowcase/{title}"

    fun thread(uri: String) = "thread/" + Uri.encode(uri)
    fun notInShowcase(title: String) = "notInShowcase/" + Uri.encode(title)
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun BlueskyRoot(container: AppContainer) {
    val session by container.sessionManager.session.collectAsStateWithLifecycle()
    val now = rememberMinuteTick()
    CompositionLocalProvider(LocalNow provides now) {
        // testTagsAsResourceId exposes Modifier.testTag as the resource id Maestro/UiAutomator match on.
        Box(Modifier.fillMaxSize().background(Theme.bg).semantics { testTagsAsResourceId = true }) {
            val current = session
            if (current == null) {
                SignedOutScreen(container)
            } else {
                SignedInShell(container, current)
            }
        }
    }
}

/** RN `SplashScreen` (signed out) plus the debug-only e2e controls from `TestCtrls.e2e.tsx`. */
@Composable
private fun SignedOutScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(Theme.bg).navigationBarsPadding()) {
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
                PillButton("createAccountButton", "Create account", Palette.primary_500, Color.White) {
                    message = "Create account: Not part of this showcase"
                }
                PillButton("signInButton", "Sign in", Palette.contrast_50, Palette.contrast_700) {
                    message = "Sign in form: Not part of this showcase. Debug builds sign in through the e2e control."
                }
            }
            Txt(
                message.orEmpty(),
                alfText(FontSize.sm, color = Theme.textContrastMedium),
                Modifier.padding(vertical = Space.lg).height(40.dp),
                align = TextAlign.Center,
            )
        }
        if (BuildConfig.DEBUG) {
            // RN TestCtrls: 1x1 pressables at top 100, right 0. Same testIDs so one Maestro flow drives both apps.
            Column(Modifier.align(Alignment.TopEnd).padding(top = 100.dp)) {
                Box(
                    Modifier.size(1.dp).background(Color.Red).testTag("e2eSignInAlice").pressable("Sign in as Alice") {
                        scope.launch {
                            runCatching { container.sessionManager.login("alice.test", "hunter2") }
                                .onSuccess { container.interactions.reset() }
                                .onFailure { message = "Sign in failed: ${it.message}" }
                        }
                    },
                )
                Box(
                    Modifier.size(1.dp).background(Color.Red).testTag("e2eSignInBob").pressable("Sign in as Bob") {
                        scope.launch {
                            runCatching { container.sessionManager.login("bob.test", "hunter2") }
                                .onSuccess { container.interactions.reset() }
                                .onFailure { message = "Sign in failed: ${it.message}" }
                        }
                    },
                )
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

class ShellViewModel(private val repository: BskyRepository) : ViewModel() {
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.unreadNotificationCount() }.onSuccess { _unread.value = it }
        }
    }
}

@Composable
private fun SignedInShell(container: AppContainer, session: Session) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    var lastTab by rememberSaveable { mutableStateOf(Routes.HOME) }
    if (route in TAB_ROUTES) lastTab = route!!

    val shell: ShellViewModel = viewModel(
        key = "shell-${session.did}",
        factory = viewModelFactory { initializer { ShellViewModel(container.repository) } },
    )
    val unread by shell.unread.collectAsStateWithLifecycle()
    val openPost: (PostView) -> Unit = { nav.navigate(Routes.thread(it.uri)) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) { HomeRoute(container, session, openPost, nav) }
                composable(Routes.SEARCH) { NotInShowcase("Search") }
                composable(Routes.MESSAGES) { NotInShowcase("Chat") }
                composable(Routes.NOTIFICATIONS) { NotInShowcase("Notifications") }
                composable(Routes.PROFILE) { ProfileRoute(container, session, openPost) }
                composable(Routes.THREAD) { entry ->
                    val uri = Uri.decode(entry.arguments?.getString("uri").orEmpty())
                    ThreadRoute(container, uri, openPost, onBack = { nav.popBackStack() })
                }
                composable(Routes.NOT_IN_SHOWCASE) { entry ->
                    val title = Uri.decode(entry.arguments?.getString("title").orEmpty())
                    Column(Modifier.fillMaxSize().background(Theme.bg).padding(top = statusBarTop())) {
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
        BottomBar(selected = lastTab, unread = unread, onSelect = { target -> nav.selectTab(target) })
    }
}

@Composable
private fun statusBarTop() = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

private val TAB_ROUTES = setOf(Routes.HOME, Routes.SEARCH, Routes.MESSAGES, Routes.NOTIFICATIONS, Routes.PROFILE)

private fun NavHostController.selectTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun HomeRoute(container: AppContainer, session: Session, openPost: (PostView) -> Unit, nav: NavHostController) {
    val vm: FeedViewModel = viewModel(
        key = "feed-${session.did}",
        factory = viewModelFactory { initializer { FeedViewModel(container.repository, container.interactions) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        actions = PostActions(openPost, vm::toggleLike, vm::toggleRepost),
        onRefresh = vm::refresh,
        onSelectTab = vm::selectTab,
        onNotInShowcase = { nav.navigate(Routes.notInShowcase(it)) },
    )
}

@Composable
private fun ProfileRoute(container: AppContainer, session: Session, openPost: (PostView) -> Unit) {
    val vm: ProfileViewModel = viewModel(
        key = "profile-${session.did}",
        factory = viewModelFactory { initializer { ProfileViewModel(session.did, container.repository, container.interactions) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    ProfileScreen(
        state = state,
        actions = PostActions(openPost, vm::toggleLike, vm::toggleRepost),
        onRefresh = vm::refresh,
        onSelectTab = vm::selectTab,
    )
}

@Composable
private fun ThreadRoute(container: AppContainer, uri: String, openPost: (PostView) -> Unit, onBack: () -> Unit) {
    val vm: ThreadViewModel = viewModel(
        key = "thread-$uri",
        factory = viewModelFactory { initializer { ThreadViewModel(uri, container.repository, container.interactions) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    ThreadScreen(
        state = state,
        actions = PostActions(onOpen = { if (it.uri != uri) openPost(it) }, onToggleLike = vm::toggleLike, onToggleRepost = vm::toggleRepost),
        onBack = onBack,
        onRefresh = vm::refresh,
    )
}

/**
 * RN `BottomBar` (src/view/shell/bottom-bar/BottomBar.tsx): hairline top border, paddingHorizontal 8,
 * each control flex 1 with paddingTop 13 / paddingBottom 4, iconWidth 28 with per-icon adjustments,
 * notification badge at left 52% / top 8 in primary_500.
 */
@Composable
fun BottomBar(selected: String, unread: Int, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    // paddingBottom: clamp(safeAreaInsets.bottom, 15, 60).
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().coerceIn(15.dp, 60.dp)
    Column(modifier.fillMaxWidth().background(Theme.bg)) {
        Hairline()
        Row(Modifier.fillMaxWidth().padding(start = Space.sm, end = Space.sm, bottom = bottomInset)) {
            BarButton("bottomBarHomeBtn", "Home", Modifier.weight(1f), onClick = { onSelect(Routes.HOME) }) {
                BskyIcon(if (selected == Routes.HOME) R.drawable.ic_home_filled else R.drawable.ic_home, 29.dp, Theme.text)
            }
            BarButton("bottomBarSearchBtn", "Search", Modifier.weight(1f), onClick = { onSelect(Routes.SEARCH) }) {
                BskyIcon(R.drawable.ic_search, 30.dp, Theme.text, Modifier.offset(y = (-1).dp))
            }
            BarButton("bottomBarMessagesBtn", "Chat", Modifier.weight(1f), onClick = { onSelect(Routes.MESSAGES) }) {
                BskyIcon(R.drawable.ic_message, 27.dp, Theme.text)
            }
            BarButton(
                "bottomBarNotificationsBtn",
                "Notifications",
                Modifier.weight(1f),
                badge = if (unread > 0) unread.toString() else null,
                onClick = { onSelect(Routes.NOTIFICATIONS) },
            ) {
                BskyIcon(if (selected == Routes.NOTIFICATIONS) R.drawable.ic_bell_filled else R.drawable.ic_bell, 28.dp, Theme.text)
            }
            BarButton("bottomBarProfileBtn", "Profile", Modifier.weight(1f), onClick = { onSelect(Routes.PROFILE) }) {
                val atProfile = selected == Routes.PROFILE
                Box(
                    Modifier
                        .size(28.dp)
                        .then(if (atProfile) Modifier.border(1.dp, Theme.text, CircleShape) else Modifier),
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
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.testTag(tag).pressable(label, onClick)) {
        Box(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 4.dp), contentAlignment = Alignment.Center) { icon() }
        if (badge != null) {
            Box(
                Modifier
                    .offset(x = maxWidth * 0.52f, y = 8.dp)
                    .clip(RoundedCornerShape(Radius.full))
                    .background(Palette.primary_500)
                    .padding(start = 5.dp, end = 5.dp, top = 1.dp, bottom = 2.dp),
            ) {
                Txt(badge, alfText(12.sp, FontWeight.SemiBold, Color.White))
            }
        }
    }
}

package io.nativestarter.showcase.bluesky.feature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.nativestarter.showcase.bluesky.AccountScope
import io.nativestarter.showcase.bluesky.core.analytics.LocalAnalytics
import io.nativestarter.showcase.bluesky.core.navigation.AppNavigator
import io.nativestarter.showcase.bluesky.core.navigation.Routes
import io.nativestarter.showcase.bluesky.core.network.PostSource
import io.nativestarter.showcase.bluesky.core.session.Session
import io.nativestarter.showcase.bluesky.designsystem.post.PostActions
import io.nativestarter.showcase.bluesky.designsystem.text.RichSegment
import io.nativestarter.showcase.bluesky.designsystem.text.RichTextModel
import io.nativestarter.showcase.bluesky.feature.feed.FeedViewModel
import io.nativestarter.showcase.bluesky.feature.feed.HomeCallbacks
import io.nativestarter.showcase.bluesky.feature.feed.HomeScreen
import io.nativestarter.showcase.bluesky.feature.profile.ProfileCallbacks
import io.nativestarter.showcase.bluesky.feature.profile.ProfileScreen
import io.nativestarter.showcase.bluesky.feature.profile.ProfileViewModel
import io.nativestarter.showcase.bluesky.feature.thread.ThreadCallbacks
import io.nativestarter.showcase.bluesky.feature.thread.ThreadScreen
import io.nativestarter.showcase.bluesky.feature.thread.ThreadViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/*
 * Screen factories (review A16). Each creates its own view model in the ViewModelStoreOwner of its back-stack entry,
 * which the shell provides, and talks to navigation through [AppNavigator] only.
 */

/** RN RichText taps: links open through the proxy URL (`shouldProxyLinks`); mentions and tags open screens outside the slice. */
private fun openRichText(segment: RichSegment, uriHandler: UriHandler, navigator: AppNavigator) {
    when (segment) {
        is RichSegment.Link -> runCatching { uriHandler.openUri(RichTextModel.proxiedUrl(segment.uri)) }
        is RichSegment.Mention -> navigator.openNotInShowcase("Profile")
        is RichSegment.Tag -> navigator.openNotInShowcase("Hashtag")
        is RichSegment.Plain -> Unit
    }
}

@Composable
fun HomeRoute(account: AccountScope, session: Session, navigator: AppNavigator) {
    val analytics = LocalAnalytics.current
    val uriHandler = LocalUriHandler.current
    val vm: FeedViewModel = viewModel(
        key = "feed-${session.did}",
        factory = viewModelFactory { initializer { FeedViewModel(account.repository, account.interactions, account.analytics, session.did, account.postCache, account.preferences) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    // RN Home.tsx `HomeScreenReady` useFocusEffect: `home:feedDisplayed` with reason 'focus'. RN mounts
    // HomeScreenReady only after preferences and pinned feeds load, so the event waits for both.
    val homeReady by vm.homeReady.collectAsStateWithLifecycle()
    if (homeReady) {
        LifecycleResumeEffect(Unit) {
            analytics.metric(
                "home:feedDisplayed",
                mapOf("index" to state.selectedTab, "feedType" to "following", "feedUrl" to "following", "reason" to "focus"),
            )
            onPauseOrDispose { }
        }
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
            onNotInShowcase = navigator::openNotInShowcase,
            onEndReached = vm::onEndReached,
            onRetry = vm::retry,
            onRetryLoadMore = vm::retryLoadMore,
            onPostSeen = vm::onPostSeen,
            onComposerPressed = vm::onComposerPressed,
            onOpenFullThread = navigator::openThread,
            postActions = { row ->
                val via = FeedViewModel.viaOf(row.reason)
                PostActions(
                    onOpen = {
                        vm.onPostPressed(it)
                        // RN PostFeedItem `onBeforePress`: stash the source feed for the thread (setUnstablePostSource).
                        account.postSources.set(it.uri, PostSource(feedDescriptor = "following", via = via))
                        navigator.openThread(it.uri)
                    },
                    onToggleLike = { vm.toggleLike(it, via) },
                    onToggleRepost = { vm.toggleRepost(it, via) },
                    onOpenAuthor = {
                        vm.onAuthorPressed(it)
                        if (it.author.did == session.did) navigator.selectTab(Routes.PROFILE) else navigator.openNotInShowcase("Profile")
                    },
                    // RN opens the composer, which is outside the slice.
                    onReply = {
                        vm.onReplyPressed(it)
                        navigator.openNotInShowcase("Reply")
                    },
                    onRichText = { segment -> openRichText(segment, uriHandler, navigator) },
                    onOpenReposter = { navigator.openNotInShowcase("Profile") },
                    onQuote = { navigator.openNotInShowcase("Quote post") },
                )
            },
        ),
    )
}

@Composable
fun ProfileRoute(account: AccountScope, session: Session, navigator: AppNavigator) {
    val uriHandler = LocalUriHandler.current
    val vm: ProfileViewModel = viewModel(
        key = "profile-${session.did}",
        factory = viewModelFactory {
            initializer {
                ProfileViewModel(
                    actorDid = session.did,
                    repository = account.repository,
                    interactions = account.interactions,
                    analytics = account.analytics,
                    postCache = account.postCache,
                    onProfileLoaded = account::setOwnProfile,
                )
            }
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    // RN Profile `listenSoftReset`: scroll the current section to the top and truncate its feed.
    val softResets = remember(account) { account.softReset.resets.filter { it == Routes.PROFILE }.map { } }
    ProfileScreen(
        state = state,
        softResets = softResets,
        currentAccountDid = session.did,
        callbacks = ProfileCallbacks(
            onRefresh = vm::refresh,
            onSoftReset = vm::softReset,
            onSelectTab = vm::selectTab,
            onBack = navigator::back,
            onRetryProfile = vm::retryProfile,
            onRetry = vm::retry,
            onRetryLoadMore = vm::retryLoadMore,
            onEndReached = vm::onEndReached,
            onPostSeen = vm::onPostSeen,
            onOpenFullThread = navigator::openThread,
            onNotInShowcase = navigator::openNotInShowcase,
            postActions = { row ->
                val via = FeedViewModel.viaOf(row.reason)
                PostActions(
                    onOpen = {
                        vm.onPostPressed(it)
                        // The profile feed has no feed source info, so the thread gets no feed descriptor.
                        account.postSources.set(it.uri, PostSource(feedDescriptor = null, via = via))
                        navigator.openThread(it.uri)
                    },
                    onToggleLike = { vm.toggleLike(it, via) },
                    onToggleRepost = { vm.toggleRepost(it, via) },
                    onOpenAuthor = {
                        vm.onAuthorPressed(it)
                        if (it.author.did != session.did) navigator.openNotInShowcase("Profile")
                    },
                    onReply = {
                        vm.onReplyPressed(it)
                        navigator.openNotInShowcase("Reply")
                    },
                    onRichText = { segment -> openRichText(segment, uriHandler, navigator) },
                    onOpenReposter = { navigator.openNotInShowcase("Profile") },
                    onQuote = { navigator.openNotInShowcase("Quote post") },
                )
            },
        ),
    )
}

@Composable
fun ThreadRoute(account: AccountScope, session: Session, uri: String, navigator: AppNavigator) {
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
                    preferencesCache = account.preferences,
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
            onBack = navigator::back,
            onThreadOptions = { navigator.openNotInShowcase("Thread options") },
            onRetry = vm::retry,
            onShowOtherReplies = vm::showOtherReplies,
            onOpenThread = navigator::openThread,
            onAnchorLaidOut = vm::onAnchorLaidOut,
            onStartReached = vm::onStartReached,
            onEndReached = vm::onEndReached,
            onItemSeen = vm::onItemSeen,
            // The composer is outside the slice.
            onReplyToAnchor = { navigator.openNotInShowcase("Reply") },
            postActions = { item ->
                PostActions(
                    onOpen = { if (it.uri != uri) navigator.openThread(it.uri) },
                    onToggleLike = vm::toggleLike,
                    onToggleRepost = vm::toggleRepost,
                    onOpenAuthor = {
                        if (item.depth == 0) vm.onAnchorAuthorPressed(it)
                        if (it.author.did == session.did) navigator.selectTab(Routes.PROFILE) else navigator.openNotInShowcase("Profile")
                    },
                    onReply = {
                        vm.onReplyPressed(it)
                        navigator.openNotInShowcase("Reply")
                    },
                    onRichText = { segment -> openRichText(segment, uriHandler, navigator) },
                    onOpenReposter = { navigator.openNotInShowcase("Profile") },
                    onQuote = { navigator.openNotInShowcase("Quote post") },
                )
            },
        ),
    )
}

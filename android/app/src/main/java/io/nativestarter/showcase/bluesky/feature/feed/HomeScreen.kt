package io.nativestarter.showcase.bluesky.feature.feed

import io.nativestarter.showcase.bluesky.designsystem.IconSize
import io.nativestarter.showcase.bluesky.designsystem.AvatarSize
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nativestarter.showcase.bluesky.R
import io.nativestarter.showcase.bluesky.core.common.ErrorText
import io.nativestarter.showcase.bluesky.core.platform.HapticStrength
import io.nativestarter.showcase.bluesky.core.platform.LocalHaptics
import io.nativestarter.showcase.bluesky.designsystem.AlfButton
import io.nativestarter.showcase.bluesky.designsystem.BskyIcon
import io.nativestarter.showcase.bluesky.designsystem.ButtonColor
import io.nativestarter.showcase.bluesky.designsystem.ButtonSize
import io.nativestarter.showcase.bluesky.designsystem.FontSize
import io.nativestarter.showcase.bluesky.designsystem.LocalAlf
import io.nativestarter.showcase.bluesky.designsystem.NotInShowcase
import io.nativestarter.showcase.bluesky.designsystem.Radius
import io.nativestarter.showcase.bluesky.designsystem.ShowcaseMark
import io.nativestarter.showcase.bluesky.designsystem.Space
import io.nativestarter.showcase.bluesky.designsystem.Txt
import io.nativestarter.showcase.bluesky.designsystem.text.rememberJsLineHeightExcess
import io.nativestarter.showcase.bluesky.designsystem.UserAvatar
import io.nativestarter.showcase.bluesky.designsystem.alfText
import io.nativestarter.showcase.bluesky.designsystem.feed.ErrorMessage
import io.nativestarter.showcase.bluesky.designsystem.feed.LoadMoreRetryButton
import io.nativestarter.showcase.bluesky.designsystem.feed.PostLoadingPlaceholder
import io.nativestarter.showcase.bluesky.designsystem.feed.ViewFullThreadRow
import io.nativestarter.showcase.bluesky.designsystem.post.PostActions
import io.nativestarter.showcase.bluesky.designsystem.post.PostFeedItem
import io.nativestarter.showcase.bluesky.designsystem.pressable
import io.nativestarter.showcase.bluesky.designsystem.resolveTextStyle
import io.nativestarter.showcase.bluesky.designsystem.ui.TabBar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.roundToInt

/** Everything the home screen reports back. Defaults do nothing, so previews and UI tests pass only what they check. */
data class HomeCallbacks(
    val onRefresh: () -> Unit = {},
    val onSelectTab: (Int) -> Unit = {},
    /** The selected tab or the header mark was pressed: RN `emitSoftReset()`. */
    val onRequestSoftReset: () -> Unit = {},
    /** A soft reset reached this screen: refresh with reason `soft-reset`. */
    val onSoftReset: () -> Unit = {},
    val onNotInShowcase: (String) -> Unit = {},
    val onEndReached: () -> Unit = {},
    /** RN `useFillPages`: top up short pages after the list has rendered them. */
    val onFillPages: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRetryLoadMore: () -> Unit = {},
    val onPostSeen: (PostFeedRow.SliceItemRow) -> Unit = {},
    val onComposerPressed: () -> Unit = {},
    val onOpenFullThread: (String) -> Unit = {},
    val postActions: (PostFeedRow.SliceItemRow) -> PostActions = { PostActions({}, {}, {}) },
)

/**
 * RN src/view/screens/Home.tsx with the Following `FeedPage` and `PostFeed` (CP-01):
 * - the header (drawer, mark, feeds) and feed tabs sit over the list and hide on scroll (MainScrollProvider);
 * - rows follow `PostFeed`: loading placeholders, error banner, empty state, composer prompt, slices, fallback
 *   marker, load-more retry, and `FollowingEndOfFeed` only when RN renders it;
 * - `onEndReached` fires within two screens of the end; a soft reset scrolls to the top and refreshes.
 */
@Composable
fun HomeScreen(
    state: FeedUiState,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
    softResets: Flow<Unit> = emptyFlow(),
    currentAccountDid: String? = null,
) {
    // RN wraps the feed pager in `<Layout.Screen testID="HomeScreen">` (Home.tsx:95); the pager itself carries
    // `testID="homeScreen"` (Home.tsx:305). Compose allows one test tag per node, so the screen is one node above.
    Box(modifier.fillMaxSize().testTag("HomeScreen")) {
        HomeScreenPager(state, callbacks, Modifier, softResets, currentAccountDid)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenPager(
    state: FeedUiState,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
    softResets: Flow<Unit> = emptyFlow(),
    currentAccountDid: String? = null,
) {
    val theme = LocalAlf.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    // 0 = header shown, -headerHeightPx = hidden (RN headerMode 0..1).
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }
    val headerHeightDp = with(density) { headerHeightPx.toDp() }

    val connection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val h = headerHeightPx.toFloat()
                if (h <= 0f) return Offset.Zero
                val nearTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < h
                headerOffsetPx = if (nearTop && available.y > 0) 0f else (headerOffsetPx + available.y).coerceIn(-h, 0f)
                return Offset.Zero
            }
        }
    }
    // Snap when scrolling stops: shown near the top, otherwise to the nearest end (RN `snapToClosestState`).
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress || headerHeightPx == 0) return@LaunchedEffect
        val h = headerHeightPx.toFloat()
        val nearTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < h
        val target = if (nearTop || headerOffsetPx > -h / 2) 0f else -h
        animate(headerOffsetPx, target) { value, _ -> headerOffsetPx = value }
    }
    LaunchedEffect(softResets) {
        softResets.collect {
            headerOffsetPx = 0f
            listState.animateScrollToItem(0)
            callbacks.onSoftReset()
        }
    }
    val endReached by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            // onEndReachedThreshold 2: within two viewport lengths of the end.
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
            val remainingItems = info.totalItemsCount - 1 - last.index
            remainingItems * last.size <= viewport * 2
        }
    }
    // Re-evaluated when the fetch settles, not only when the list changes.
    //
    // `onEndReached` is refused while a fetch is in flight, which is RN's own guard
    // (`PostFeed.onEndReached`: `if (isFetching || !hasNextPage || isError) return`). Auto-pagination
    // runs a fetch as soon as the first page lands, so on a short feed the end of the list is reached
    // while that fetch is still running: the call is refused, and without `isFetching` in the keys
    // nothing changes afterwards to make it run again. `feed:endReached` was then never logged at all,
    // where RN logs it with the next page still available. RN does not have this problem because
    // FlatList calls `onEndReached` again on later scroll and layout passes.
    LaunchedEffect(endReached, state.rows.size, state.isFetching, state.hasNextPage) {
        if (endReached && state.isFetched) callbacks.onEndReached()
    }
    // RN's page fill (`useFillPages`, src/state/queries/util.ts) is an effect, and it is declared
    // after the end-reached effect so the two run in RN's order: the list reports that its end is
    // visible first, then the fill fetches. Reversing them consumes the next page before anything
    // can report the end, which is how `feed:endReached` came to be missing from the port's log.
    LaunchedEffect(state.rows.size, state.isFetching, state.hasNextPage, state.isFetched) {
        callbacks.onFillPages()
    }

    // RN useHeaderOffset predicts the tab text as `20 * fontScale` (linear); the tab text itself scales nonlinearly.
    val headerTextExcess = rememberJsLineHeightExcess(TAB_TEXT_LINE_HEIGHT_DP)
    val headerOffset = headerHeightDp + headerTextExcess
    Box(modifier.fillMaxSize().background(theme.bg).testTag("homeScreen")) {
        val contentTop = statusBarTop + headerOffset
        if (state.selectedTab != 0) {
            NotInShowcase(state.feedTabs[state.selectedTab], Modifier.padding(top = contentTop))
        } else {
            val refreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = callbacks.onRefresh,
                state = refreshState,
                modifier = Modifier.fillMaxSize().testTag("followingFeedPage").nestedScroll(connection),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = refreshState,
                        isRefreshing = state.isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = contentTop),
                    )
                },
            ) {
                FeedList(state, callbacks, listState, contentTop, headerOffset, currentAccountDid)
            }
        }
        // Status bar area stays pinned; the header below it translates up and fades ((1 - mode)^2).
        Box(Modifier.fillMaxWidth().height(statusBarTop).background(theme.bg))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = statusBarTop)
                .onSizeChanged { headerHeightPx = it.height }
                .graphicsLayer {
                    translationY = headerOffsetPx
                    val mode = if (headerHeightPx > 0) -headerOffsetPx / headerHeightPx else 0f
                    alpha = (1 - mode) * (1 - mode)
                }
                .background(theme.bg),
        ) {
            HomeHeader(
                onMenu = { callbacks.onNotInShowcase("Menu") },
                onMark = callbacks.onRequestSoftReset,
                onFeeds = { callbacks.onNotInShowcase("Feeds") },
            )
            TabBar(
                state.feedTabs,
                state.selectedTab,
                onSelect = { index ->
                    when (index) {
                        HomeTabs.feedsLinkIndex(state.feedTabs) -> callbacks.onNotInShowcase("Feeds")
                        state.selectedTab -> callbacks.onRequestSoftReset()
                        else -> callbacks.onSelectTab(index)
                    }
                },
                centered = true,
                testTag = "homeScreenFeedTabs",
            )
        }
    }
}

/** RN `useHeaderOffset` `normalLineHeight`: the tab bar text line height it predicts, in dp. */
private const val TAB_TEXT_LINE_HEIGHT_DP = 20f

@Composable
private fun FeedList(
    state: FeedUiState,
    callbacks: HomeCallbacks,
    listState: androidx.compose.foundation.lazy.LazyListState,
    contentTop: androidx.compose.ui.unit.Dp,
    headerHeight: androidx.compose.ui.unit.Dp,
    currentAccountDid: String?,
) {
    // RN FeedFooter offset: max(headerOffset, 32) * 2 on native.
    val footerOffset = maxOf(headerHeight, Space._4xl) * 2
    TrackPostViews(listState, state.rows, callbacks.onPostSeen)
    LazyColumn(
        Modifier.fillMaxSize().testTag("followingFeedPage-feed-flatlist"),
        state = listState,
        contentPadding = PaddingValues(top = contentTop),
    ) {
        postFeedItems(
            state = state,
            footerOffset = footerOffset,
            currentAccountDid = currentAccountDid,
            onPostSeen = callbacks.onPostSeen,
            onOpenFullThread = callbacks.onOpenFullThread,
            onRetryLoadMore = callbacks.onRetryLoadMore,
            postActions = callbacks.postActions,
            errorContent = { ErrorMessage(ErrorText.clean(state.error), callbacks.onRetry) },
            emptyContent = {
                FollowingEmptyState(onFindAccounts = { callbacks.onNotInShowcase("Search") }, onDiscoverFeeds = { callbacks.onNotInShowcase("Feeds") })
            },
            endOfFeedContent = {
                FollowingEndOfFeed(onFindAccounts = { callbacks.onNotInShowcase("Search") }, onDiscoverFeeds = { callbacks.onNotInShowcase("Feeds") })
            },
            extraRow = { row ->
                when (row) {
                    PostFeedRow.ComposerPrompt -> ComposerPrompt(onClick = {
                        callbacks.onComposerPressed()
                        callbacks.onNotInShowcase("New post")
                    })
                    PostFeedRow.FallbackMarker -> DiscoverFallbackHeader()
                    else -> Unit
                }
            },
        )
    }
}

/** RN `HomeHeaderLayoutMobile`: menu button, centred mark (light haptic + soft reset), feeds (#) button. */
@Composable
private fun HomeHeader(onMenu: () -> Unit, onMark: () -> Unit, onFeeds: () -> Unit) {
    val theme = LocalAlf.current
    val haptics = LocalHaptics.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = Space.sm, end = Space.sm, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).testTag("viewHeaderDrawerBtn").pressable("Open drawer menu", onMenu), contentAlignment = Alignment.Center) {
            BskyIcon(R.drawable.ic_bars3, IconSize.lg, theme.textContrastMedium)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            ShowcaseMark(
                30.dp,
                Modifier.pressable {
                    haptics.play(HapticStrength.Light)
                    onMark()
                },
            )
        }
        Box(Modifier.size(34.dp).testTag("viewHeaderHomeFeedPrefsBtn").pressable("View your feeds and explore more", onFeeds), contentAlignment = Alignment.Center) {
            BskyIcon(R.drawable.ic_hashtag, IconSize.lg, theme.textContrastMedium)
        }
    }
}

/**
 * RN `ComposerPrompt` (src/view/com/feeds/ComposerPrompt.tsx): row align_start, paddingLeft 18, paddingRight 15,
 * native paddingTop/Bottom 10; avatar 42; an inner row flex_1, ml_md, height 40, align_center, justify_between
 * with the text (text_md, contrast_medium, includeFontPadding false) and two 24 dp icon buttons, gap_md.
 */
@Composable
private fun ComposerPrompt(onClick: () -> Unit) {
    val theme = LocalAlf.current
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("composerPrompt")
            .pressable("Compose new post", onClick)
            .padding(start = ComposerPromptLayout.paddingStart, end = ComposerPromptLayout.paddingEnd, top = ComposerPromptLayout.paddingVertical, bottom = ComposerPromptLayout.paddingVertical),
        verticalAlignment = Alignment.Top,
    ) {
        UserAvatar(null, AvatarSize.feed)
        Row(
            Modifier.weight(1f).padding(start = Space.md).height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // ALF Text with includeFontPadding false; leading_snug still applies.
            Txt("What's up?", alfText(FontSize.md, color = theme.textContrastMedium, includeFontPadding = false))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                BskyIcon(R.drawable.ic_camera, IconSize.lg, theme.palette.contrast(300), Modifier.pressable("Open camera", onClick))
                BskyIcon(R.drawable.ic_image, IconSize.lg, theme.palette.contrast(300), Modifier.pressable("Add image", onClick))
            }
        }
    }
}

/**
 * RN `DiscoverFallbackHeader`: row py 12 / px 12, borderTopWidth 1 (pal.border = contrast_100),
 * pal.viewLight background, a 68dp icon column with paddingLeft 12 and a 36dp info icon in
 * pal.textLight (contrast_700), then legacy `md` text with a `md-medium` (15, semibold) "Discover" link.
 */
@Composable
private fun DiscoverFallbackHeader() {
    val theme = LocalAlf.current
    Column(Modifier.fillMaxWidth().background(theme.bgContrast25)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(theme.borderContrastLow))
        Row(Modifier.padding(horizontal = Space.md, vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(68.dp).padding(start = Space.md)) {
                BskyIcon(R.drawable.ic_info, 36.dp, theme.textContrastMedium)
            }
            io.nativestarter.showcase.bluesky.designsystem.text.RnText(
                buildAnnotatedString {
                    append("We ran out of posts from your follows. Here's the latest from ")
                    withStyle(SpanStyle(color = theme.textLink, fontWeight = FontWeight.SemiBold)) { append("Discover") }
                    append(".")
                },
                style = resolveTextStyle(io.nativestarter.showcase.bluesky.designsystem.legacyText(15.sp)),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** RN `FollowingEndOfFeed`: container pt 40, pb 80, px 30, min height 75% of the window, border top. */
@Composable
private fun FollowingEndOfFeed(onFindAccounts: () -> Unit, onDiscoverFeeds: () -> Unit) {
    val theme = LocalAlf.current
    val minHeight = (LocalConfiguration.current.screenHeightDp * 0.75f).dp
    // Legacy Text type "xl-medium": fontSize 17, fontWeight semiBold (src/lib/themes.ts).
    val xlMedium = io.nativestarter.showcase.bluesky.designsystem.legacyText(17.sp, FontWeight.SemiBold)
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(theme.borderContrastLow))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .padding(start = EndOfFeedLayout.paddingHorizontal, end = EndOfFeedLayout.paddingHorizontal, top = Space._5xl, bottom = EndOfFeedLayout.paddingBottom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Txt("You've reached the end of your feed! Find some more accounts to follow.", xlMedium, align = TextAlign.Center)
            Spacer(Modifier.height(Space.xl))
            InvertedButton("Find accounts to follow", onFindAccounts)
            Txt("You can also discover new Custom Feeds to follow.", xlMedium, Modifier.padding(top = Space.xl), align = TextAlign.Center)
            Spacer(Modifier.height(Space.md))
            InvertedButton("Discover new custom feeds", onDiscoverFeeds)
        }
    }
}

/** RN `FollowingEmptyState`: magnifying glass 62, copy, and the same two buttons. */
@Composable
private fun FollowingEmptyState(onFindAccounts: () -> Unit, onDiscoverFeeds: () -> Unit) {
    val theme = LocalAlf.current
    val xlMedium = io.nativestarter.showcase.bluesky.designsystem.legacyText(17.sp, FontWeight.SemiBold)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = EndOfFeedLayout.paddingHorizontal, vertical = Space._5xl).widthIn(max = 460.dp).testTag("followingEmptyState"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BskyIcon(R.drawable.ic_search, 62.dp, theme.text, Modifier.padding(bottom = Space.lg))
        Txt("Your following feed is empty! Follow more users to see what's happening.", xlMedium, align = TextAlign.Center)
        Spacer(Modifier.height(Space.xl))
        InvertedButton("Find accounts to follow", onFindAccounts)
        Txt("You can also discover new Custom Feeds to follow.", xlMedium, Modifier.padding(top = Space.xl), align = TextAlign.Center)
        Spacer(Modifier.height(Space.md))
        InvertedButton("Discover new custom feeds", onDiscoverFeeds)
    }
}

/** RN `Button color="secondary_inverted" size="large"` with a trailing chevron. */
@Composable
private fun InvertedButton(label: String, onClick: () -> Unit) {
    AlfButton(label, onClick, size = ButtonSize.Large, color = ButtonColor.SecondaryInverted, iconEnd = R.drawable.ic_chevron_right)
}

@Suppress("unused")
private fun Float.px(): Int = roundToInt()

/** RN `ComposerPrompt` style literals with no ALF space token: paddingLeft 18, paddingRight 15, native paddingTop/Bottom 10. */
private object ComposerPromptLayout {
    val paddingStart = 18.dp
    val paddingEnd = 15.dp
    val paddingVertical = 10.dp
}

/** RN `FollowingEndOfFeed` / `FollowingEmptyState` styles: paddingHorizontal 30, paddingBottom 80 (no ALF token). */
private object EndOfFeedLayout {
    val paddingHorizontal = 30.dp
    val paddingBottom = 80.dp
}

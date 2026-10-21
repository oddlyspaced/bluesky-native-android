package io.nativestarter.showcase.bluesky.feature.profile

import io.nativestarter.showcase.bluesky.designsystem.IconSize
import io.nativestarter.showcase.bluesky.designsystem.AvatarSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.nativestarter.showcase.bluesky.R
import io.nativestarter.showcase.bluesky.core.common.ErrorText
import io.nativestarter.showcase.bluesky.core.network.Facet
import io.nativestarter.showcase.bluesky.core.network.ProfileViewDetailed
import io.nativestarter.showcase.bluesky.designsystem.AlfButton
import io.nativestarter.showcase.bluesky.designsystem.BskyIcon
import io.nativestarter.showcase.bluesky.designsystem.ButtonColor
import io.nativestarter.showcase.bluesky.designsystem.FontSize
import io.nativestarter.showcase.bluesky.designsystem.Hairline
import io.nativestarter.showcase.bluesky.designsystem.LineHeight
import io.nativestarter.showcase.bluesky.designsystem.LocalAlf
import io.nativestarter.showcase.bluesky.designsystem.Sanitize
import io.nativestarter.showcase.bluesky.designsystem.ScreenHeader
import io.nativestarter.showcase.bluesky.designsystem.Space
import io.nativestarter.showcase.bluesky.designsystem.StatCount
import io.nativestarter.showcase.bluesky.designsystem.Txt
import io.nativestarter.showcase.bluesky.designsystem.UserAvatar
import io.nativestarter.showcase.bluesky.designsystem.alfText
import io.nativestarter.showcase.bluesky.designsystem.feed.EmptyState
import io.nativestarter.showcase.bluesky.designsystem.feed.ErrorMessage
import io.nativestarter.showcase.bluesky.designsystem.post.PostActions
import io.nativestarter.showcase.bluesky.designsystem.pressable
import io.nativestarter.showcase.bluesky.designsystem.text.RichSegment
import io.nativestarter.showcase.bluesky.designsystem.text.RichText
import io.nativestarter.showcase.bluesky.designsystem.ui.TabBar
import io.nativestarter.showcase.bluesky.feature.feed.PostFeedRow
import io.nativestarter.showcase.bluesky.feature.feed.postFeedItems
import io.nativestarter.showcase.bluesky.feature.feed.TrackPostViews
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/** Everything the profile screen reports back. Defaults do nothing, so UI tests pass only what they check. */
data class ProfileCallbacks(
    val onRefresh: () -> Unit = {},
    /** A soft reset or a press on the selected Posts tab reached this screen. */
    val onSoftReset: () -> Unit = {},
    val onSelectTab: (Int) -> Unit = {},
    val onBack: () -> Unit = {},
    val onRetryProfile: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRetryLoadMore: () -> Unit = {},
    val onEndReached: () -> Unit = {},
    /** RN `useFillPages`: top up short pages after the list has rendered them. */
    val onFillPages: () -> Unit = {},
    val onPostSeen: (PostFeedRow.SliceItemRow) -> Unit = {},
    val onOpenFullThread: (String) -> Unit = {},
    val onNotInShowcase: (String) -> Unit = {},
    /** A link, mention or tag in the profile description was tapped. */
    val onRichText: (RichSegment) -> Unit = {},
    val postActions: (PostFeedRow.SliceItemRow) -> PostActions = { PostActions({}, {}, {}) },
)

/**
 * RN `ProfileScreen` (src/view/screens/Profile.tsx) for the signed-in user's own profile (CP-03):
 * `ProfileHeaderLoading` while the profile loads, `ErrorScreen` when it fails, otherwise the header, the section tabs
 * and the Posts `PostFeed` with RN loading, error, empty, load-more error and end-of-feed rows.
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    callbacks: ProfileCallbacks,
    modifier: Modifier = Modifier,
    softResets: Flow<Unit> = emptyFlow(),
    currentAccountDid: String? = null,
) {
    val theme = LocalAlf.current
    Box(modifier.fillMaxSize().background(theme.bg).testTag("profileScreen")) {
        val profile = state.profile
        val error = state.profileError
        when {
            error != null -> ProfileErrorScreen(error, callbacks.onRetryProfile, callbacks.onBack)
            profile == null -> ProfileHeaderLoading()
            else -> ProfileLoaded(profile, state, callbacks, softResets, isMe = profile.did == currentAccountDid, currentAccountDid)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ProfileLoaded(
    profile: ProfileViewDetailed,
    state: ProfileUiState,
    callbacks: ProfileCallbacks,
    softResets: Flow<Unit>,
    isMe: Boolean,
    currentAccountDid: String?,
) {
    val theme = LocalAlf.current
    val current by rememberUpdatedState(callbacks)
    val selectedTab by rememberUpdatedState(state.selectedTab)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val feed = state.feed
    var headerPx by remember { mutableIntStateOf(0) }
    var tabsPx by remember { mutableIntStateOf(0) }
    // RN PagerWithHeader `headerHeight`: the profile header plus the tab bar.
    val headerHeight = with(LocalDensity.current) { (headerPx + tabsPx).toDp() }

    fun scrollSectionToTop() {
        if (selectedTab != 0) return
        scope.launch { listState.animateScrollToItem(0) }
        current.onSoftReset()
    }
    LaunchedEffect(softResets) { softResets.collect { scrollSectionToTop() } }

    val endReached by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            // PostFeed onEndReachedThreshold 2: within two viewport lengths of the end.
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
            (info.totalItemsCount - 1 - last.index) * last.size <= viewport * 2
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
    LaunchedEffect(endReached, feed.rows.size, feed.isFetching, feed.hasNextPage) {
        if (endReached && feed.isFetched && selectedTab == 0) current.onEndReached()
    }
    // RN's page fill (`useFillPages`, src/state/queries/util.ts) is an effect, and it is declared
    // after the end-reached effect so the two run in RN's order: the list reports that its end is
    // visible first, then the fill fetches. Reversing them consumes the next page before anything
    // can report the end, which is how `feed:endReached` came to be missing from the port's log.
    LaunchedEffect(feed.rows.size, feed.isFetching, feed.hasNextPage, feed.isFetched) {
        current.onFillPages()
    }
    TrackPostViews(listState, feed.rows, { current.onPostSeen(it) }, enabled = state.selectedTab == 0)

    Box(Modifier.fillMaxSize().testTag("profileView")) {
        PullToRefreshBox(
            isRefreshing = feed.isRefreshing,
            onRefresh = current.onRefresh,
            modifier = Modifier.fillMaxSize().testTag("postsFeed"),
        ) {
            LazyColumn(Modifier.fillMaxSize().testTag("postsFeed-flatlist"), state = listState) {
                item(key = "header") {
                    ProfileHeader(
                        profile = profile,
                        descriptionFacets = state.descriptionFacets,
                        onNotInShowcase = current.onNotInShowcase,
                        onRichText = { current.onRichText(it) },
                        modifier = Modifier.onSizeChanged { headerPx = it.height },
                    )
                }
                stickyHeader(key = "tabs") {
                    Box(Modifier.onSizeChanged { tabsPx = it.height }) {
                        TabBar(
                            items = PROFILE_TABS,
                            selected = state.selectedTab,
                            onSelect = { index -> if (index == state.selectedTab) scrollSectionToTop() else current.onSelectTab(index) },
                            centered = false,
                            testTag = "profilePager",
                        )
                    }
                }
                if (state.selectedTab == 0) {
                    postFeedItems(
                        state = feed,
                        footerOffset = maxOf(headerHeight, Space._4xl) * 2,
                        currentAccountDid = currentAccountDid,
                        onPostSeen = { current.onPostSeen(it) },
                        onOpenFullThread = { current.onOpenFullThread(it) },
                        onRetryLoadMore = { current.onRetryLoadMore() },
                        postActions = { current.postActions(it) },
                        errorContent = {
                            if (state.isPostsHidden) {
                                EmptyState(R.drawable.ic_warning, "Posts hidden", Modifier.padding(vertical = Space._5xl), iconSize = 32.dp)
                            } else {
                                ErrorMessage(ErrorText.clean(feed.error), current.onRetry)
                            }
                        },
                        emptyContent = {
                            EmptyState(
                                R.drawable.ic_edit_big,
                                "No posts yet",
                                button = if (isMe) {
                                    { AlfButton("Write a post", { current.onNotInShowcase("New post") }, color = ButtonColor.Primary) }
                                } else {
                                    null
                                },
                            )
                        },
                        endOfFeedContent = { ProfileEndOfFeed() },
                    )
                } else {
                    item(key = "notInShowcase") {
                        Txt(
                            "${PROFILE_TABS[state.selectedTab]}: Not part of this showcase",
                            alfText(FontSize.md, color = theme.textContrastMedium),
                            Modifier.fillMaxWidth().padding(vertical = Space._5xl),
                            align = TextAlign.Center,
                        )
                    }
                }
            }
        }
        // RN StatusBarShadow: a dark gradient behind the translucent status bar over the banner.
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent))),
        )
    }
}

/** RN `numberOfLines={15}` on the profile description (ProfileHeaderStandard.tsx:150). */
private const val PROFILE_DESCRIPTION_MAX_LINES = 15

/**
 * RN `ProfileHeaderStandard` inside `ProfileHeaderShell`. The description is rich text: it carries no facets of
 * its own, so RN detects links, mentions and tags client-side (`useRichText` then `detectFacets`) and this port
 * does the same (review A8). [descriptionFacets] is empty until the detection has resolved, because RN renders
 * the raw text first (`resolvedRT ?? rawRT`).
 */
@Composable
private fun ProfileHeader(
    profile: ProfileViewDetailed,
    descriptionFacets: List<Facet>,
    onNotInShowcase: (String) -> Unit,
    onRichText: (RichSegment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAlf.current
    Box(modifier.fillMaxWidth().background(theme.bg)) {
        Column {
            // Shell: banner height 150; userBannerFallback is bg_contrast_25 when there is no banner.
            io.nativestarter.showcase.bluesky.core.image.RemoteImage(
                profile.banner,
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(theme.bgContrast25)
                    .semantics { contentDescription = if (profile.banner != null) "View profile banner" else "Profile banner placeholder" }
                    .testTag(if (profile.banner != null) "userBannerImage" else "userBannerFallback"),
            ) {}
            Column(Modifier.padding(start = Space.lg, end = Space.lg, top = Space.md, bottom = Space.sm)) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = Space.sm),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs, Alignment.End),
                ) {
                    AlfButton("Edit profile", { onNotInShowcase("Edit profile") }, text = "Edit Profile", testTag = "profileHeaderEditProfileButton")
                    AlfButton(
                        "Invite friends",
                        { onNotInShowcase("Invite friends") },
                        text = null,
                        iconStart = R.drawable.ic_share,
                        square = true,
                        testTag = "profileHeaderShareButton",
                    )
                    AlfButton(
                        "More options",
                        { onNotInShowcase("More options") },
                        text = null,
                        iconStart = R.drawable.ic_dots,
                        square = true,
                        testTag = "profileHeaderDropdownBtn",
                    )
                }
                Column(
                    Modifier.padding(top = Space._2xs, bottom = Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    Txt(
                        Sanitize.displayNameOrHandle(profile.displayName, profile.handle),
                        alfText(FontSize._3xl, FontWeight.Bold, leading = LineHeight.tight),
                        Modifier.testTag("profileHeaderDisplayName"),
                    )
                    Txt(
                        Sanitize.handle(profile.handle, "@"),
                        alfText(FontSize.md, color = theme.textContrastMedium, leading = LineHeight.snug),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    Metrics(profile, onNotInShowcase)
                    profile.description?.takeIf { it.isNotBlank() }?.let {
                        RichText(
                            text = it,
                            facets = descriptionFacets,
                            style = alfText(FontSize.md),
                            maxLines = PROFILE_DESCRIPTION_MAX_LINES,
                            testTag = "profileHeaderDescription",
                            onSegmentClick = onRichText,
                        )
                    }
                }
            }
        }
        // Shell: GrowableAvatar at top 104, left 10; 94dp circle with a 2dp bg border around a 90dp avatar.
        Box(
            Modifier
                .offset(x = 10.dp, y = 104.dp)
                .size(94.dp)
                .clip(CircleShape)
                .background(theme.bg)
                .semantics {
                    contentDescription = "View ${profile.handle}'s avatar"
                    role = Role.Image
                }
                .testTag("profileHeaderAviButton"),
            contentAlignment = Alignment.Center,
        ) {
            UserAvatar(profile.avatar, AvatarSize.profile)
        }
    }
}

/** RN Metrics.tsx: followers and following links (labels "{n} followers", "{n} following") and the post count. */
@Composable
private fun Metrics(profile: ProfileViewDetailed, onNotInShowcase: (String) -> Unit) {
    val followers = if (profile.followersCount == 1) "follower" else "followers"
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
        Metric("profileHeaderFollowersButton", "${profile.followersCount} $followers", profile.followersCount, followers) { onNotInShowcase("Followers") }
        Metric("profileHeaderFollowsButton", "${profile.followsCount} following", profile.followsCount, "following") { onNotInShowcase("Following") }
        Metric(null, null, profile.postsCount, if (profile.postsCount == 1) "post" else "posts", null)
    }
}

@Composable
private fun Metric(tag: String?, label: String?, count: Int, noun: String, onClick: (() -> Unit)?) {
    val theme = LocalAlf.current
    var modifier: Modifier = if (tag != null) Modifier.testTag(tag) else Modifier
    if (onClick != null) modifier = modifier.pressable(label, onClick)
    Row(modifier) {
        Txt("${StatCount.format(count)} ", alfText(FontSize.md, FontWeight.SemiBold))
        Txt(noun, alfText(FontSize.md, color = theme.textContrastMedium))
    }
}

/** RN `ProfileEndOfFeed` (screens/Profile/Sections/Feed.tsx): py_5xl, border_t, centred contrast-medium text. */
@Composable
private fun ProfileEndOfFeed() {
    val theme = LocalAlf.current
    Column(Modifier.fillMaxWidth()) {
        Hairline()
        Txt(
            "End of feed",
            alfText(FontSize.sm, color = theme.textContrastMedium),
            Modifier.fillMaxWidth().padding(vertical = Space._5xl),
            align = TextAlign.Center,
        )
    }
}

/** RN `ProfileHeaderLoading` (screens/Profile/Header/index.tsx): banner, avatar and button placeholders in contrast_50. */
@Composable
private fun ProfileHeaderLoading() {
    val theme = LocalAlf.current
    val placeholder = theme.bgContrast50
    Box(Modifier.fillMaxWidth().background(theme.bg).testTag("profileHeaderLoading")) {
        Column {
            Box(Modifier.fillMaxWidth().height(150.dp).background(placeholder))
            Row(
                Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.lg, top = Space.md, bottom = Space.sm),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(Modifier.size(width = 140.dp, height = 34.dp).clip(RoundedCornerShape(50.dp)).background(placeholder))
            }
        }
        Box(
            Modifier.offset(x = 10.dp, y = 110.dp).size(94.dp).clip(CircleShape).background(theme.bg),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(90.dp).clip(CircleShape).background(placeholder))
        }
    }
}

/**
 * RN `ErrorScreen` with `showHeader` (src/view/com/util/error/ErrorScreen.tsx): "Error" header, a 50 dp contrast_950
 * circle with a warning icon, a bold 2xl title, the cleaned message and a small inverted "Try again" button.
 */
@Composable
private fun ProfileErrorScreen(copy: ProfileErrorCopy, onRetry: () -> Unit, onBack: () -> Unit) {
    val theme = LocalAlf.current
    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("profileErrorScreen")) {
        ScreenHeader("Error", onBack)
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space._2xl)) {
            Box(Modifier.fillMaxWidth().padding(bottom = Space.md), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(50.dp).clip(CircleShape).background(theme.palette.contrast(950)),
                    contentAlignment = Alignment.Center,
                ) {
                    BskyIcon(R.drawable.ic_warning, IconSize.lg, theme.textInverted)
                }
            }
            Txt(copy.title, alfText(FontSize._2xl, FontWeight.Bold), Modifier.fillMaxWidth().padding(bottom = Space.md), align = TextAlign.Center)
            Txt(copy.message, alfText(FontSize.md), Modifier.fillMaxWidth().padding(bottom = Space.xl), align = TextAlign.Center)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AlfButton(
                    "Retry",
                    onRetry,
                    color = ButtonColor.SecondaryInverted,
                    text = "Try again",
                    iconStart = R.drawable.ic_rotate_ccw,
                    testTag = "errorScreenTryAgainButton",
                    hint = "Retries the last action, which errored out",
                )
            }
        }
    }
}

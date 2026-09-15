package io.nativestarter.showcase.bluesky.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.nativestarter.showcase.bluesky.R
import io.nativestarter.showcase.bluesky.designsystem.BskyIcon
import io.nativestarter.showcase.bluesky.designsystem.FontSize
import io.nativestarter.showcase.bluesky.designsystem.Hairline
import io.nativestarter.showcase.bluesky.designsystem.LineHeight
import io.nativestarter.showcase.bluesky.designsystem.Palette
import io.nativestarter.showcase.bluesky.designsystem.Radius
import io.nativestarter.showcase.bluesky.designsystem.Space
import io.nativestarter.showcase.bluesky.designsystem.Theme
import io.nativestarter.showcase.bluesky.designsystem.Txt
import io.nativestarter.showcase.bluesky.designsystem.UserAvatar
import io.nativestarter.showcase.bluesky.designsystem.alfText
import io.nativestarter.showcase.bluesky.designsystem.post.PostActions
import io.nativestarter.showcase.bluesky.designsystem.post.PostFeedItem
import io.nativestarter.showcase.bluesky.designsystem.pressable
import io.nativestarter.showcase.bluesky.designsystem.ui.TabBar

/** RN src/view/screens/Profile.tsx + screens/Profile/Header for the signed-in user's own profile (CP-03). */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    actions: PostActions,
    onRefresh: () -> Unit,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Theme.bg).testTag("profileView")) {
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().testTag("postsFeed")) {
                state.profile?.let { profile ->
                    item(key = "header") { ProfileHeader(profile) }
                    stickyHeader(key = "tabs") {
                        TabBar(
                            items = PROFILE_TABS,
                            selected = state.selectedTab,
                            onSelect = onSelectTab,
                            centered = false,
                            testTag = "profilePager",
                        )
                    }
                }
                if (state.selectedTab == 0) {
                    state.posts.forEach { slice ->
                        items(slice.rows, key = { slice.key + it.post.uri }) { row ->
                            PostFeedItem(
                                row.post,
                                actions,
                                isThreadParent = row.isThreadParent,
                                isThreadChild = row.isThreadChild,
                                showTopBorder = slice.rows.first() == row && state.posts.first() != slice,
                            )
                        }
                    }
                    if (!state.loading && state.error == null && state.reachedEnd) {
                        item(key = "footer") { EndOfFeed() }
                    }
                } else {
                    item(key = "notInShowcase") {
                        Txt(
                            "${PROFILE_TABS[state.selectedTab]}: Not part of this showcase",
                            alfText(FontSize.md, color = Theme.textContrastMedium),
                            Modifier.fillMaxWidth().padding(vertical = Space._5xl),
                            align = TextAlign.Center,
                        )
                    }
                }
                state.error?.let { err ->
                    item(key = "error") {
                        Txt(err, alfText(FontSize.md, color = Theme.textContrastMedium), Modifier.padding(Space.lg))
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

/** RN ProfileHeaderStandard inside ProfileHeaderShell. */
@Composable
private fun ProfileHeader(profile: io.nativestarter.showcase.bluesky.core.network.ProfileViewDetailed) {
    Box(Modifier.fillMaxWidth().background(Theme.bg)) {
        Column {
            // Shell: banner height 150; userBannerFallback is bg_contrast_25 when there is no banner.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Theme.bgContrast25)
                    .testTag(if (profile.banner != null) "userBannerImage" else "userBannerFallback"),
            )
            Column(Modifier.padding(start = Space.lg, end = Space.lg, top = Space.md, bottom = Space.sm)) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = Space.sm),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs, Alignment.End),
                ) {
                    SmallButton("profileHeaderEditProfileButton", "Edit profile") {
                        Txt("Edit Profile", alfText(FontSize.sm, FontWeight.Medium, Palette.contrast_700))
                    }
                    SmallButton("profileHeaderShareButton", "Invite friends", square = true) {
                        BskyIcon(R.drawable.ic_share, 16.dp, Palette.contrast_700)
                    }
                    SmallButton("profileHeaderDropdownBtn", "More options", square = true) {
                        BskyIcon(R.drawable.ic_dots, 16.dp, Palette.contrast_700)
                    }
                }
                Column(
                    Modifier.padding(top = Space._2xs, bottom = Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    Txt(
                        profile.displayName?.takeIf { it.isNotBlank() } ?: profile.handle,
                        alfText(FontSize._3xl, FontWeight.Bold, leading = LineHeight.tight),
                        Modifier.testTag("profileHeaderDisplayName"),
                    )
                    Txt("@${profile.handle}", alfText(FontSize.md, color = Theme.textContrastMedium, leading = LineHeight.snug))
                }
                Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    Metrics(profile)
                    profile.description?.takeIf { it.isNotBlank() }?.let {
                        Txt(it, alfText(FontSize.md), Modifier.testTag("profileHeaderDescription"))
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
                .background(Theme.bg)
                .testTag("profileHeaderAviButton"),
            contentAlignment = Alignment.Center,
        ) {
            UserAvatar(profile.avatar, 90.dp)
        }
    }
}

/** RN Metrics.tsx: "{n} followers  {n} following  {n} posts". */
@Composable
private fun Metrics(profile: io.nativestarter.showcase.bluesky.core.network.ProfileViewDetailed) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Metric("profileHeaderFollowersButton", profile.followersCount, if (profile.followersCount == 1) "follower" else "followers")
        Metric("profileHeaderFollowsButton", profile.followsCount, "following")
        Metric(null, profile.postsCount, if (profile.postsCount == 1) "post" else "posts")
    }
}

@Composable
private fun Metric(tag: String?, count: Int, label: String) {
    Row(if (tag != null) Modifier.testTag(tag) else Modifier) {
        Txt("$count ", alfText(FontSize.md, FontWeight.SemiBold))
        Txt(label, alfText(FontSize.md, color = Theme.textContrastMedium))
    }
}

/** RN `Button size="small" color="secondary"`: bg contrast_50, rounded_full, 33dp tall. */
@Composable
private fun SmallButton(tag: String, label: String, square: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier
            .testTag(tag)
            .height(33.dp)
            .then(if (square) Modifier.size(33.dp) else Modifier)
            .clip(RoundedCornerShape(Radius.full))
            .background(Palette.contrast_50)
            .pressable(label) {}
            // Button size small: paddingVertical 8, paddingHorizontal 14 (src/components/Button.tsx).
            .padding(horizontal = if (square) 0.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** RN screens/Profile/Sections/Feed.tsx footer. */
@Composable
private fun EndOfFeed() {
    Column {
        Hairline()
        Txt(
            "End of feed",
            alfText(FontSize.md, color = Theme.textContrastMedium),
            Modifier.fillMaxWidth().padding(vertical = Space._2xl + Space.lg),
            align = TextAlign.Center,
        )
    }
}

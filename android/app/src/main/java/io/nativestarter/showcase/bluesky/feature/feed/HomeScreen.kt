package io.nativestarter.showcase.bluesky.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import io.nativestarter.showcase.bluesky.R
import io.nativestarter.showcase.bluesky.designsystem.BskyIcon
import io.nativestarter.showcase.bluesky.designsystem.FontSize
import io.nativestarter.showcase.bluesky.designsystem.Hairline
import io.nativestarter.showcase.bluesky.designsystem.NotInShowcase
import io.nativestarter.showcase.bluesky.designsystem.Palette
import io.nativestarter.showcase.bluesky.designsystem.Radius
import io.nativestarter.showcase.bluesky.designsystem.ShowcaseMark
import io.nativestarter.showcase.bluesky.designsystem.Space
import io.nativestarter.showcase.bluesky.designsystem.Theme
import io.nativestarter.showcase.bluesky.designsystem.Txt
import io.nativestarter.showcase.bluesky.designsystem.UserAvatar
import io.nativestarter.showcase.bluesky.designsystem.alfText
import io.nativestarter.showcase.bluesky.designsystem.post.PostActions
import io.nativestarter.showcase.bluesky.designsystem.post.PostFeedItem
import io.nativestarter.showcase.bluesky.designsystem.pressable
import io.nativestarter.showcase.bluesky.designsystem.ui.TabBar

/** RN src/view/screens/Home.tsx with the "Following" feed page (CP-01). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: FeedUiState,
    actions: PostActions,
    onRefresh: () -> Unit,
    onSelectTab: (Int) -> Unit,
    onNotInShowcase: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Theme.bg).statusBarsPadding().testTag("homeScreen")) {
        HomeHeader(onMenu = { onNotInShowcase("Menu") }, onFeeds = { onNotInShowcase("Feeds") })
        TabBar(
            state.feedTabs,
            state.selectedTab,
            onSelect = { index ->
                if (index == HomeTabs.feedsLinkIndex(state.feedTabs)) onNotInShowcase("Feeds") else onSelectTab(index)
            },
            centered = true,
            testTag = "homeScreenFeedTabs",
        )
        if (state.selectedTab != 0) {
            NotInShowcase(state.feedTabs[state.selectedTab], Modifier.weight(1f))
            return@Column
        }
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize().testTag("followingFeedPage")) {
                item(key = "composer") { ComposerPrompt(onClick = { onNotInShowcase("New post") }) }
                state.slices.forEachIndexed { sliceIndex, slice ->
                    items(slice.rows, key = { slice.key + "|" + it.post.uri }) { row ->
                        PostFeedItem(
                            row.post,
                            actions,
                            isThreadParent = row.isThreadParent,
                            isThreadChild = row.isThreadChild,
                            showTopBorder = row == slice.rows.first(),
                        )
                    }
                }
                if (state.reachedEnd && !state.loading) {
                    item(key = "discoverFallback") { DiscoverFallbackHeader() }
                    item(key = "endOfFeed") { FollowingEndOfFeed(onFindAccounts = { onNotInShowcase("Search") }) }
                }
                state.error?.let { err ->
                    item(key = "error") {
                        Txt(err, alfText(FontSize.md, color = Theme.textContrastMedium), Modifier.padding(Space.lg))
                    }
                }
            }
        }
    }
}

/** RN `HomeHeaderLayoutMobile`: menu button, centred mark, feeds (#) button. The mark replaces the Bluesky logo. */
@Composable
private fun HomeHeader(onMenu: () -> Unit, onFeeds: () -> Unit) {
    // Height tuned against the RN Android baseline: tab labels were 2.5px (0.8dp) higher at 48dp.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 49.dp).padding(start = Space.sm, end = Space.sm, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).testTag("viewHeaderDrawerBtn").pressable("Open drawer menu", onMenu), contentAlignment = Alignment.Center) {
            BskyIcon(R.drawable.ic_bars3, 24.dp, Theme.textContrastMedium)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ShowcaseMark(30.dp) }
        Box(Modifier.size(34.dp).testTag("viewHeaderHomeFeedPrefsBtn").pressable("View your feeds and explore more", onFeeds), contentAlignment = Alignment.Center) {
            BskyIcon(R.drawable.ic_hashtag, 24.dp, Theme.textContrastMedium)
        }
    }
}

/** RN `ComposerPrompt`: avatar 42, "What's up?", camera and image buttons. */
@Composable
private fun ComposerPrompt(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("composerPrompt")
            .pressable("Compose new post", onClick)
            // RN py_md (12). Measured offsets (text 6px low, row 4px tall) give 10 / 11 on this device.
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(null, 42.dp)
        Txt("What's up?", alfText(FontSize.md, color = Theme.textContrastMedium), Modifier.padding(start = Space.md).weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            BskyIcon(R.drawable.ic_camera, 24.dp, Palette.contrast_300)
            BskyIcon(R.drawable.ic_image, 24.dp, Palette.contrast_300)
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
    Column(Modifier.fillMaxWidth().background(Theme.bgContrast25)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.contrast_100))
        // RN paddingVertical 12; legacy RN Text keeps font padding, so the row measures taller on device.
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(68.dp).padding(start = 12.dp)) {
                BskyIcon(R.drawable.ic_info, 36.dp, Palette.contrast_700)
            }
            BasicText(
                buildAnnotatedString {
                    append("We ran out of posts from your follows. Here's the latest from ")
                    withStyle(SpanStyle(color = Palette.primary_500, fontWeight = FontWeight.SemiBold)) { append("Discover") }
                    append(".")
                },
                style = alfText(15.sp),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** RN `FollowingEndOfFeed`: container pt 40, pb 80, px 30, min height 75% of the window. */
@Composable
private fun FollowingEndOfFeed(onFindAccounts: () -> Unit) {
    val minHeight = (LocalConfiguration.current.screenHeightDp * 0.75f).dp
    // Legacy Text type "xl-medium": fontSize 17, fontWeight semiBold (src/lib/themes.ts).
    val xlMedium = alfText(17.sp, FontWeight.SemiBold)
    Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.contrast_100))
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            // RN paddingTop 40 and a.mt_xl (20); +3 / +1.5 measured for RN font padding on legacy Text.
            .padding(start = 30.dp, end = 30.dp, top = 43.dp, bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Txt(
            "You've reached the end of your feed! Find some more accounts to follow.",
            xlMedium,
            align = TextAlign.Center,
        )
        Spacer(Modifier.height(21.5.dp))
        InvertedButton("Find accounts to follow", onFindAccounts)
        Txt(
            "You can also discover new Custom Feeds to follow.",
            xlMedium,
            Modifier.padding(top = 20.dp),
            align = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.md))
        InvertedButton("Discover new custom feeds", onFindAccounts)
    }
}

/** RN `Button color="secondary_inverted" size="large"`: contrast_900 pill, py 12, px 24, gap 6. */
@Composable
private fun InvertedButton(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(Radius.full))
            .background(Palette.contrast_900)
            .pressable(label, onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Large ButtonText: text_md font_medium; secondary_inverted text: t.atoms.text_inverted (contrast_0).
        // Measured RN large button height 43.4dp = 24 padding + 19.4 text line.
        Txt(label, alfText(FontSize.md, FontWeight.Medium, Palette.contrast_0).copy(lineHeight = 19.4.sp))
        BskyIcon(R.drawable.ic_chevron_right, 16.dp, Palette.contrast_0, Modifier.width(16.dp))
    }
}

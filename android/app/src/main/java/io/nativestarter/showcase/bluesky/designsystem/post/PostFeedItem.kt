package io.nativestarter.showcase.bluesky.designsystem.post

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.nativestarter.showcase.bluesky.R
import io.nativestarter.showcase.bluesky.core.network.PostView
import io.nativestarter.showcase.bluesky.designsystem.BskyIcon
import io.nativestarter.showcase.bluesky.designsystem.FontSize
import io.nativestarter.showcase.bluesky.designsystem.Hairline
import io.nativestarter.showcase.bluesky.designsystem.LineHeight
import io.nativestarter.showcase.bluesky.designsystem.LocalNow
import io.nativestarter.showcase.bluesky.designsystem.Palette
import io.nativestarter.showcase.bluesky.designsystem.Radius
import io.nativestarter.showcase.bluesky.designsystem.Space
import io.nativestarter.showcase.bluesky.designsystem.StatCount
import io.nativestarter.showcase.bluesky.designsystem.Theme
import io.nativestarter.showcase.bluesky.designsystem.TimeAgo
import io.nativestarter.showcase.bluesky.designsystem.Txt
import io.nativestarter.showcase.bluesky.designsystem.UserAvatar
import io.nativestarter.showcase.bluesky.designsystem.alfText
import io.nativestarter.showcase.bluesky.designsystem.pressable

/** Callbacks shared by every post row. */
data class PostActions(
    val onOpen: (PostView) -> Unit,
    val onToggleLike: (PostView) -> Unit,
    val onToggleRepost: (PostView) -> Unit,
)

/**
 * RN `PostFeedItem` (src/view/com/posts/PostFeedItem.tsx): outer paddingLeft 10 / paddingRight 15,
 * a pt_sm spacer row that carries the thread line for children, avatar column (pl 8, pr 10, 42dp),
 * then PostMeta, text and PostControls.
 */
@Composable
fun PostFeedItem(
    post: PostView,
    actions: PostActions,
    modifier: Modifier = Modifier,
    isThreadParent: Boolean = false,
    isThreadChild: Boolean = false,
    showTopBorder: Boolean = true,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Theme.bg)
            .testTag("feedItem-by-${post.author.handle}")
            .pressable(onClick = { actions.onOpen(post) }),
    ) {
        if (showTopBorder && !isThreadChild) Hairline()
        Row(Modifier.padding(start = 10.dp, end = 15.dp)) {
            // Top spacer row: {flexDirection: row, gap: 10, paddingLeft: 8} + a.pt_sm.
            Box(Modifier.padding(start = 8.dp).width(42.dp).height(Space.sm), contentAlignment = Alignment.TopCenter) {
                if (isThreadChild) {
                    Box(Modifier.padding(bottom = 4.dp).width(2.dp).fillMaxHeight().background(Palette.contrast_100))
                }
            }
        }
        Row(
            Modifier
                .padding(start = 10.dp, end = 15.dp, top = 1.dp)
                .height(IntrinsicSize.Min),
        ) {
            Column(
                Modifier.padding(start = 8.dp, end = 10.dp).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UserAvatar(post.author.avatar, 42.dp)
                if (isThreadParent) {
                    Box(Modifier.padding(top = 4.dp).width(2.dp).weight(1f).background(Palette.contrast_100))
                }
            }
            Column(Modifier.weight(1f)) {
                PostMeta(post)
                PostText(post)
                PostControls(post, actions, big = false)
            }
        }
        // outerStyles.paddingBottom: 8 for standalone posts and the last child of a thread slice.
        if (!isThreadParent) Spacer(Modifier.height(Space.sm))
    }
}

/** RN `PostContent` RichText: text_md inside `a.mb_2xs`. Rich text facets are not part of this slice. */
@Composable
fun PostText(post: PostView, modifier: Modifier = Modifier) {
    if (post.record.text.isEmpty()) return
    Txt(
        post.record.text,
        alfText(FontSize.md),
        modifier.testTag("postText").padding(bottom = Space._2xs),
    )
}

/**
 * RN `PostMeta` (src/view/com/util/PostMeta.tsx), Android branch: display name (md semibold, max 70%),
 * handle (md, contrast_medium) and the relative time with `flex_grow` + `text_right` and no "·" prefix
 * (`!IS_ANDROID && <Text>&middot;</Text>`).
 */
@Composable
fun PostMeta(post: PostView, modifier: Modifier = Modifier) {
    val now = LocalNow.current
    val medium = alfText(FontSize.md, color = Theme.textContrastMedium, leading = LineHeight.tight)
    // No BoxWithConstraints here: feed rows use IntrinsicSize.Min, and SubcomposeLayout has no intrinsics.
    Row(modifier.fillMaxWidth().padding(bottom = Space.xs), verticalAlignment = Alignment.Bottom) {
        // Display name: flex_shrink_0 with maxWidth 70%. As the first child it receives the full row width.
        Txt(
            post.author.displayName?.takeIf { it.isNotBlank() } ?: post.author.handle,
            alfText(FontSize.md, FontWeight.SemiBold, leading = LineHeight.tight),
            Modifier.maxWidthFraction(0.7f),
            maxLines = 1,
        )
        // Handle: flexShrink 10, so it gives up space first. Time: flex_grow + text_right (Android).
        Txt(
            " @${post.author.handle}",
            medium,
            Modifier.weight(1f),
            maxLines = 1,
        )
        Txt(
            TimeAgo.format(post.indexedAt, now),
            medium,
            Modifier.padding(start = Space.xs),
            maxLines = 1,
            align = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/** RN `maxWidth: '70%'` relative to the incoming max width. A layout modifier, so intrinsic measurement still works. */
private fun Modifier.maxWidthFraction(fraction: Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val cap = if (constraints.hasBoundedWidth) (constraints.maxWidth * fraction).toInt() else constraints.maxWidth
        val placeable = measurable.measure(
            constraints.copy(minWidth = minOf(constraints.minWidth, cap), maxWidth = cap),
        )
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    },
)

/**
 * RN `PostControls` (src/components/PostControls/index.tsx): left group reply/repost/like, each flex_1,
 * max width 320; right group bookmark/share/more. Buttons have padding 5 and gap_xs; icons contrast_500.
 */
@Composable
fun PostControls(post: PostView, actions: PostActions, big: Boolean, modifier: Modifier = Modifier) {
    var repostMenuOpen by remember { mutableStateOf(false) }
    val iconSize = if (big) 22.dp else 18.dp
    Row(
        modifier.fillMaxWidth().padding(top = if (big) 0.dp else Space._2xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).widthIn(max = 320.dp)) {
            Box(Modifier.weight(1f)) {
                ControlButton(
                    tag = "replyBtn",
                    icon = R.drawable.ic_bubble,
                    iconSize = iconSize,
                    count = post.replyCount,
                    big = big,
                    label = "Reply (${post.replyCount} replies)",
                    modifier = Modifier.padding(start = 0.dp),
                    onClick = { actions.onOpen(post) },
                )
            }
            Box(Modifier.weight(1f)) {
                ControlButton(
                    tag = "repostBtn",
                    icon = R.drawable.ic_repost,
                    iconSize = iconSize,
                    count = post.repostCount,
                    countTag = "repostCount",
                    big = big,
                    active = post.viewer.repost != null,
                    activeColor = Palette.positive_600,
                    label = if (post.viewer.repost != null) "Undo repost" else "Repost",
                    onClick = { repostMenuOpen = true },
                )
            }
            Box(Modifier.weight(1f)) {
                val liked = post.viewer.like != null
                ControlButton(
                    tag = "likeBtn",
                    icon = if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart,
                    iconSize = iconSize,
                    count = post.likeCount,
                    countTag = "likeCount",
                    big = big,
                    active = liked,
                    activeColor = Palette.pink,
                    label = if (liked) "Unlike" else "Like",
                    onClick = { actions.onToggleLike(post) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(if (big) Space.sm else Space.xs)) {
            ControlButton("postBookmarkBtn", R.drawable.ic_bookmark, iconSize, 0, big, label = "Save to bookmarks") {}
            ControlButton("postShareBtn", R.drawable.ic_share, iconSize, 0, big, label = "Open share menu") {}
            ControlButton("postDropdownBtn", R.drawable.ic_dots, iconSize, 0, big, label = "Open post options menu") {}
        }
    }
    if (repostMenuOpen) {
        RepostDialog(
            isReposted = post.viewer.repost != null,
            onDismiss = { repostMenuOpen = false },
            onRepost = {
                repostMenuOpen = false
                actions.onToggleRepost(post)
            },
        )
    }
}

@Composable
private fun ControlButton(
    tag: String,
    icon: Int,
    iconSize: androidx.compose.ui.unit.Dp,
    count: Int,
    big: Boolean,
    modifier: Modifier = Modifier,
    countTag: String? = null,
    active: Boolean = false,
    activeColor: androidx.compose.ui.graphics.Color = Palette.contrast_500,
    label: String,
    onClick: () -> Unit,
) {
    val color = if (active) activeColor else Palette.contrast_500
    Row(
        modifier
            .testTag(tag)
            .clip(RoundedCornerShape(Radius.full))
            .pressable(label, onClick)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BskyIcon(icon, iconSize, color)
        if (count > 0) {
            Txt(
                StatCount.format(count),
                alfText(
                    if (big) FontSize.md else FontSize.sm,
                    if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color,
                ),
                if (countTag != null) Modifier.testTag(countTag) else Modifier,
            )
        }
    }
}

/**
 * RN `RepostButtonDialogInner` (native): a bottom sheet with "Repost" / "Remove repost", "Quote post"
 * and "Cancel". Quote is outside this slice and is shown disabled.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun RepostDialog(isReposted: Boolean, onDismiss: () -> Unit, onRepost: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .semantics { testTagsAsResourceId = true }
                .pressable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl))
                    .background(Theme.bg)
                    .pressable(onClick = {})
                    .navigationBarsPadding()
                    .padding(horizontal = Space.xl, vertical = Space._2xl),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                SheetRow(
                    tag = "repostDropdownRepostBtn",
                    icon = R.drawable.ic_repost,
                    text = if (isReposted) "Remove repost" else "Repost",
                    color = if (isReposted) Palette.negative_500 else Theme.text,
                    onClick = onRepost,
                )
                SheetRow("repostDropdownQuoteBtn", R.drawable.ic_bubble, "Quote post", Theme.textContrastLow) {}
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Space.sm)
                        .clip(RoundedCornerShape(Radius.full))
                        .background(Theme.bgContrast50)
                        .pressable("Cancel", onDismiss)
                        .padding(vertical = Space.md),
                    contentAlignment = Alignment.Center,
                ) {
                    Txt("Cancel", alfText(FontSize.md, FontWeight.SemiBold, Theme.textContrastMedium))
                }
            }
        }
    }
}

@Composable
private fun SheetRow(
    tag: String,
    icon: Int,
    text: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clip(RoundedCornerShape(Radius.md))
            .background(Theme.bgContrast25)
            .pressable(text, onClick)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(text, alfText(FontSize.lg, FontWeight.SemiBold, color), Modifier.weight(1f))
        BskyIcon(icon, 24.dp, color, Modifier.size(24.dp))
    }
}

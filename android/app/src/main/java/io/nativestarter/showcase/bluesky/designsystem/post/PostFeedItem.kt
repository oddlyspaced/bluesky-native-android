package io.nativestarter.showcase.bluesky.designsystem.post

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.nativestarter.showcase.bluesky.R
import io.nativestarter.showcase.bluesky.core.network.PostView
import io.nativestarter.showcase.bluesky.core.network.ProfileViewBasic
import io.nativestarter.showcase.bluesky.core.platform.HapticStrength
import io.nativestarter.showcase.bluesky.core.platform.LocalHaptics
import io.nativestarter.showcase.bluesky.designsystem.BskyIcon
import io.nativestarter.showcase.bluesky.designsystem.FontSize
import io.nativestarter.showcase.bluesky.designsystem.Hairline
import io.nativestarter.showcase.bluesky.designsystem.LineHeight
import io.nativestarter.showcase.bluesky.designsystem.LocalAlf
import io.nativestarter.showcase.bluesky.designsystem.LocalNow
import io.nativestarter.showcase.bluesky.designsystem.LocalToaster
import io.nativestarter.showcase.bluesky.designsystem.Radius
import io.nativestarter.showcase.bluesky.designsystem.Sanitize
import io.nativestarter.showcase.bluesky.designsystem.Space
import io.nativestarter.showcase.bluesky.designsystem.StatCount
import io.nativestarter.showcase.bluesky.designsystem.TimeAgo
import io.nativestarter.showcase.bluesky.designsystem.ToastType
import io.nativestarter.showcase.bluesky.designsystem.Txt
import io.nativestarter.showcase.bluesky.designsystem.UserAvatar
import io.nativestarter.showcase.bluesky.designsystem.alfText
import io.nativestarter.showcase.bluesky.designsystem.pressable
import io.nativestarter.showcase.bluesky.designsystem.text.RichSegment
import io.nativestarter.showcase.bluesky.designsystem.text.RichText
import io.nativestarter.showcase.bluesky.designsystem.text.RichTextModel
import io.nativestarter.showcase.bluesky.feature.feed.FeedReason

/** Callbacks shared by every post row. The screen binds analytics context and `via` before passing them in. */
data class PostActions(
    val onOpen: (PostView) -> Unit,
    val onToggleLike: (PostView) -> Unit,
    val onToggleRepost: (PostView) -> Unit,
    val onOpenAuthor: (PostView) -> Unit = {},
    val onReply: (PostView) -> Unit = onOpen,
    val onRichText: (RichSegment) -> Unit = {},
    val onOpenReposter: (ProfileViewBasic) -> Unit = {},
    /** Called by the quote option; quoting needs the composer, which is outside the slice. */
    val onQuote: (PostView) -> Unit = {},
)

private const val BLOCKED_TOAST = "Cannot interact with a blocked user"

private fun PostView.isAuthorBlocked(): Boolean =
    author.viewer?.let { it.blocking != null || it.blockedBy || it.blockingByList != null } ?: false

/**
 * RN `PostFeedItem` (src/view/com/posts/PostFeedItem.tsx):
 * - outer paddingLeft 10 / paddingRight 15; paddingBottom 8 for the last child or a post outside a thread;
 *   hairline top border unless `hideTopBorder` or a thread child;
 * - a top row `{gap 10, paddingLeft 8}` with a 42 dp column (thread line for children) and the reason (`pt_sm`);
 * - avatar column (pl 8, pr 10, 42 dp) with the parent thread line; PostMeta, "Replied to", text, controls.
 */
@Composable
fun PostFeedItem(
    post: PostView,
    actions: PostActions,
    modifier: Modifier = Modifier,
    isThreadParent: Boolean = false,
    isThreadChild: Boolean = false,
    isThreadLastChild: Boolean = false,
    showTopBorder: Boolean = true,
    reason: FeedReason? = null,
    showReplyTo: Boolean = false,
    parentAuthor: ProfileViewBasic? = null,
    isParentBlocked: Boolean = false,
    isParentNotFound: Boolean = false,
    currentAccountDid: String? = null,
) {
    val theme = LocalAlf.current
    Column(
        modifier
            .fillMaxWidth()
            .background(theme.bg)
            .testTag("feedItem-by-${post.author.handle}")
            .pressable(onClick = { actions.onOpen(post) }),
    ) {
        if (showTopBorder && !isThreadChild) Hairline(theme.borderContrastLow)
        Row(Modifier.padding(start = 10.dp, end = 15.dp).height(IntrinsicSize.Min)) {
            Box(Modifier.padding(start = 8.dp).width(42.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                if (isThreadChild) {
                    Box(Modifier.padding(bottom = 4.dp).width(2.dp).fillMaxHeight().background(theme.replyLine))
                }
            }
            Column(Modifier.padding(start = 10.dp, top = Space.sm).weight(1f, fill = false)) {
                if (reason != null) PostFeedReason(reason, currentAccountDid, actions)
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
                PreviewableAvatar(post, 42.dp) { actions.onOpenAuthor(post) }
                if (isThreadParent) {
                    Box(Modifier.padding(top = 4.dp).width(2.dp).weight(1f).background(theme.replyLine))
                }
            }
            Column(Modifier.weight(1f)) {
                PostMeta(post)
                if (showReplyTo && (parentAuthor != null || isParentBlocked || isParentNotFound)) {
                    PostRepliedTo(parentAuthor, isParentBlocked, isParentNotFound, currentAccountDid)
                }
                PostText(post, actions)
                PostControls(post, actions, big = false)
            }
        }
        if (isThreadLastChild || (!isThreadChild && !isThreadParent)) Spacer(Modifier.height(Space.sm))
    }
}

/** RN `PreviewableUserAvatar`: label "{name}'s avatar", hint "Opens this profile". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PreviewableAvatar(post: PostView, size: Dp, onPress: () -> Unit) {
    val name = Sanitize.displayNameOrHandle(post.author.displayName, post.author.handle)
    Box(
        Modifier
            .semantics { contentDescription = "$name's avatar" }
            .combinedClickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClickLabel = "Opens this profile",
                onClick = onPress,
            ),
    ) {
        UserAvatar(post.author.avatar, size)
    }
}

/** RN `PostFeedReason` (src/view/com/posts/PostFeedReason.tsx): repost and pin reasons. */
@Composable
private fun PostFeedReason(reason: FeedReason, currentAccountDid: String?, actions: PostActions) {
    val theme = LocalAlf.current
    val style = alfText(FontSize.sm, FontWeight.Medium, theme.textContrastMedium, leading = LineHeight.snug)
    Row(
        Modifier.offset(x = (-16).dp).padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (reason) {
            is FeedReason.Repost -> {
                val isOwner = reason.by.did == currentAccountDid
                val name = Sanitize.displayNameOrHandle(reason.by.displayName, reason.by.handle)
                val label = if (isOwner) "Reposted by you" else "Reposted by $name"
                Row(
                    Modifier.pressable(label) { actions.onOpenReposter(reason.by) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BskyIcon(R.drawable.ic_repost, 13.dp, theme.textContrastMedium, Modifier.padding(end = 3.dp))
                    Txt(label, style, maxLines = 1)
                }
            }
            FeedReason.Pin -> {
                BskyIcon(R.drawable.ic_pin, 13.dp, theme.textContrastMedium, Modifier.padding(end = 3.dp))
                Txt("Pinned", style, maxLines = 1)
            }
        }
    }
}

/** RN `PostRepliedTo` (src/components/Post/PostRepliedTo.tsx). */
@Composable
private fun PostRepliedTo(parentAuthor: ProfileViewBasic?, isParentBlocked: Boolean, isParentNotFound: Boolean, currentAccountDid: String?) {
    val theme = LocalAlf.current
    val label = when {
        isParentBlocked -> "Replied to a blocked post"
        isParentNotFound -> "Replied to a post"
        parentAuthor != null && parentAuthor.did == currentAccountDid -> "Replied to you"
        parentAuthor != null -> "Replied to ${Sanitize.displayNameOrHandle(parentAuthor.displayName, parentAuthor.handle)}"
        else -> return
    }
    Row(
        Modifier.padding(bottom = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BskyIcon(R.drawable.ic_corner_down_right, 12.dp, theme.textContrastMedium, Modifier.offset(y = (-1).dp))
        Txt(label, alfText(FontSize.sm, color = theme.textContrastMedium, leading = LineHeight.snug), Modifier.weight(1f), maxLines = 1)
    }
}

/**
 * RN `PostContent` RichText: `text_md` inside `a.mb_2xs`, facets rendered as links, and `MAX_POST_LINES` (25) with a
 * "Show more" button when the text has 25 or more newlines.
 */
@Composable
fun PostText(post: PostView, actions: PostActions, modifier: Modifier = Modifier) {
    if (post.record.text.isEmpty()) return
    val theme = LocalAlf.current
    var limitLines by rememberSaveable(post.uri) { mutableStateOf(RichTextModel.countLines(post.record.text) >= RichTextModel.MAX_POST_LINES) }
    Column(modifier.padding(bottom = Space._2xs)) {
        RichText(
            text = post.record.text,
            facets = post.record.facets,
            style = alfText(FontSize.md),
            maxLines = if (limitLines) RichTextModel.MAX_POST_LINES else Int.MAX_VALUE,
            testTag = "postText",
            onSegmentClick = actions.onRichText,
        )
        if (limitLines) {
            Txt(
                "Show more",
                alfText(FontSize.md, color = theme.textLink, leading = LineHeight.snug),
                Modifier
                    .padding(bottom = 5.dp)
                    .semantics { contentDescription = "Expand post text" }
                    .pressable { limitLines = false },
            )
        }
    }
}

/**
 * RN `PostMeta` (src/view/com/util/PostMeta.tsx), Android branch: display name (md semibold, max 70%),
 * handle (md, contrast_medium) and the relative time with `flex_grow` + `text_right` and no "·" prefix.
 */
@Composable
fun PostMeta(post: PostView, modifier: Modifier = Modifier) {
    val theme = LocalAlf.current
    val now = LocalNow.current
    val medium = alfText(FontSize.md, color = theme.textContrastMedium, leading = LineHeight.tight)
    // No BoxWithConstraints here: feed rows use IntrinsicSize.Min, and SubcomposeLayout has no intrinsics.
    Row(modifier.fillMaxWidth().padding(bottom = Space.xs), verticalAlignment = Alignment.Bottom) {
        Txt(
            Sanitize.displayNameOrHandle(post.author.displayName, post.author.handle),
            alfText(FontSize.md, FontWeight.SemiBold, leading = LineHeight.tight),
            Modifier.maxWidthFraction(0.7f),
            maxLines = 1,
        )
        Txt(" " + Sanitize.handle(post.author.handle, "@"), medium, Modifier.weight(1f), maxLines = 1)
        Txt(
            TimeAgo.format(post.indexedAt, now),
            medium,
            Modifier.padding(start = Space.xs),
            maxLines = 1,
            align = TextAlign.End,
        )
    }
}

/** RN `maxWidth: '70%'` relative to the incoming max width. A layout modifier, so intrinsic measurement still works. */
private fun Modifier.maxWidthFraction(fraction: Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val cap = if (constraints.hasBoundedWidth) (constraints.maxWidth * fraction).toInt() else constraints.maxWidth
        val placeable = measurable.measure(constraints.copy(minWidth = minOf(constraints.minWidth, cap), maxWidth = cap))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    },
)

private fun plural(count: Int, one: String, other: String) = if (count == 1) "$count $one" else "$count $other"

/**
 * RN `PostControls` (src/components/PostControls/index.tsx): left group reply/repost/like, each flex_1, max width 320;
 * right group bookmark/share/more (gap xs, sm when big). Buttons: padding 5, gap_xs, icons contrast_500, a light
 * haptic on press. Guards: blocked authors show a toast; `replyDisabled` dims the reply slot to 0.6 with no action.
 */
@Composable
fun PostControls(post: PostView, actions: PostActions, big: Boolean, modifier: Modifier = Modifier) {
    val theme = LocalAlf.current
    val toaster = LocalToaster.current
    var repostMenuOpen by remember { mutableStateOf(false) }
    val iconSize = if (big) 22.dp else 18.dp
    val blocked = post.isAuthorBlocked()
    val replyDisabled = post.viewer.replyDisabled
    Row(
        modifier.fillMaxWidth().padding(top = if (big) 0.dp else Space._2xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).widthIn(max = 320.dp)) {
            Box(Modifier.weight(1f).then(if (replyDisabled) Modifier.alpha(0.6f) else Modifier)) {
                ControlButton(
                    tag = "replyBtn",
                    icon = R.drawable.ic_bubble,
                    iconSize = iconSize,
                    count = post.replyCount,
                    big = big,
                    label = "Reply (${plural(post.replyCount, "reply", "replies")})",
                    enabled = !replyDisabled,
                    onClick = { actions.onReply(post) },
                )
            }
            Box(Modifier.weight(1f)) {
                val reposted = post.viewer.repost != null
                val repostTotal = post.repostCount + post.quoteCount
                ControlButton(
                    tag = "repostBtn",
                    icon = R.drawable.ic_repost,
                    iconSize = iconSize,
                    count = post.repostCount,
                    countTag = "repostCount",
                    big = big,
                    active = reposted,
                    activeColor = theme.repostActive,
                    label = (if (reposted) "Undo repost" else "Repost") + " (${plural(repostTotal, "repost", "reposts")})",
                    onClick = {
                        if (blocked) toaster.show(BLOCKED_TOAST, ToastType.Warning) else repostMenuOpen = true
                    },
                    // RN long press: the dialog when embedding is disabled, otherwise quote.
                    onLongClick = {
                        when {
                            blocked -> toaster.show(BLOCKED_TOAST, ToastType.Warning)
                            post.viewer.embeddingDisabled -> repostMenuOpen = true
                            else -> actions.onQuote(post)
                        }
                    },
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
                    activeColor = theme.like,
                    label = (if (liked) "Unlike" else "Like") + " (${plural(post.likeCount, "like", "likes")})",
                    onClick = {
                        if (blocked) toaster.show(BLOCKED_TOAST, ToastType.Warning) else actions.onToggleLike(post)
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(if (big) Space.sm else Space.xs)) {
            ControlButton(
                "postBookmarkBtn",
                R.drawable.ic_bookmark,
                iconSize,
                0,
                big,
                label = if (post.viewer.bookmarked) "Remove from saved posts" else "Add to saved posts",
            ) {}
            ControlButton("postShareBtn", R.drawable.ic_share, iconSize, 0, big, label = "Open share menu") {}
            ControlButton("postDropdownBtn", R.drawable.ic_dots, iconSize, 0, big, label = "Open post options menu") {}
        }
    }
    if (repostMenuOpen) {
        RepostDialog(
            isReposted = post.viewer.repost != null,
            embeddingDisabled = post.viewer.embeddingDisabled,
            onDismiss = { repostMenuOpen = false },
            onRepost = {
                repostMenuOpen = false
                actions.onToggleRepost(post)
            },
            onQuote = {
                repostMenuOpen = false
                actions.onQuote(post)
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ControlButton(
    tag: String,
    icon: Int,
    iconSize: Dp,
    count: Int,
    big: Boolean,
    modifier: Modifier = Modifier,
    countTag: String? = null,
    active: Boolean = false,
    activeColor: Color? = null,
    label: String,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val theme = LocalAlf.current
    val haptics = LocalHaptics.current
    val color = if (active && activeColor != null) activeColor else theme.controlIcon
    Row(
        modifier
            .testTag(tag)
            .clip(RoundedCornerShape(Radius.full))
            .semantics {
                contentDescription = label
                if (!enabled) disabled()
            }
            .then(
                if (enabled) {
                    Modifier.combinedClickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            haptics.play(HapticStrength.Light)
                            onClick()
                        },
                        onLongClick = onLongClick?.let {
                            {
                                haptics.play(HapticStrength.Heavy)
                                it()
                            }
                        },
                    )
                } else {
                    Modifier
                },
            )
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BskyIcon(icon, iconSize, color)
        if (count > 0) {
            Txt(
                StatCount.format(count),
                alfText(if (big) FontSize.md else FontSize.sm, if (active) FontWeight.SemiBold else FontWeight.Normal, color, includeFontPadding = false),
                if (countTag != null) Modifier.testTag(countTag) else Modifier,
            )
        }
    }
}

/**
 * RN `RepostButtonDialogInner` (native): "Repost" / "Remove repost", "Quote post" (or "Quote posts disabled") and
 * "Cancel". A haptic plays when reposting (not when removing), as RN `playHaptic()` on the Repost row.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun RepostDialog(isReposted: Boolean, embeddingDisabled: Boolean, onDismiss: () -> Unit, onRepost: () -> Unit, onQuote: () -> Unit) {
    val theme = LocalAlf.current
    val haptics = LocalHaptics.current
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
                    .semantics { contentDescription = "Repost or quote post" }
                    .clip(RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl))
                    .background(theme.bg)
                    .pressable(onClick = {})
                    .navigationBarsPadding()
                    .padding(horizontal = Space.xl, vertical = Space._2xl),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                SheetRow(
                    tag = "repostDropdownRepostBtn",
                    icon = R.drawable.ic_repost,
                    text = if (isReposted) "Remove repost" else "Repost",
                    color = theme.primary,
                    onClick = {
                        if (!isReposted) haptics.play()
                        onRepost()
                    },
                )
                SheetRow(
                    tag = "quoteBtn",
                    icon = R.drawable.ic_bubble,
                    text = if (embeddingDisabled) "Quote posts disabled" else "Quote post",
                    color = if (embeddingDisabled) theme.textContrastLow else theme.primary,
                    enabled = !embeddingDisabled,
                    onClick = {
                        haptics.play()
                        onQuote()
                    },
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Space.sm)
                        .clip(RoundedCornerShape(Radius.full))
                        .background(theme.bgContrast50)
                        .pressable("Cancel quote post", onDismiss)
                        .padding(vertical = Space.md),
                    contentAlignment = Alignment.Center,
                ) {
                    Txt("Cancel", alfText(FontSize.md, FontWeight.Medium, theme.textContrastMedium))
                }
            }
        }
    }
}

@Composable
private fun SheetRow(tag: String, icon: Int, text: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val theme = LocalAlf.current
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clip(RoundedCornerShape(Radius.md))
            .background(theme.bgContrast25)
            .then(if (enabled) Modifier.pressable(text, onClick) else Modifier.semantics { contentDescription = text; disabled() })
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BskyIcon(icon, 24.dp, color, Modifier.size(24.dp))
        Txt(text, alfText(FontSize.md, FontWeight.Medium, color), Modifier.padding(start = Space.sm).weight(1f))
    }
}

package io.nativestarter.showcase.bluesky.feature.thread

import io.nativestarter.showcase.bluesky.designsystem.IconSize
import io.nativestarter.showcase.bluesky.designsystem.AvatarSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.nativestarter.showcase.bluesky.R
import io.nativestarter.showcase.bluesky.core.network.PostView
import io.nativestarter.showcase.bluesky.core.platform.HapticStrength
import io.nativestarter.showcase.bluesky.core.platform.LocalHaptics
import io.nativestarter.showcase.bluesky.designsystem.AlfButton
import io.nativestarter.showcase.bluesky.designsystem.BskyIcon
import io.nativestarter.showcase.bluesky.designsystem.ButtonColor
import io.nativestarter.showcase.bluesky.designsystem.FontSize
import io.nativestarter.showcase.bluesky.designsystem.Hairline
import io.nativestarter.showcase.bluesky.designsystem.LineHeight
import io.nativestarter.showcase.bluesky.designsystem.LocalAlf
import io.nativestarter.showcase.bluesky.designsystem.NiceDate
import io.nativestarter.showcase.bluesky.designsystem.Radius
import io.nativestarter.showcase.bluesky.designsystem.Sanitize
import io.nativestarter.showcase.bluesky.designsystem.ScreenHeader
import io.nativestarter.showcase.bluesky.designsystem.Space
import io.nativestarter.showcase.bluesky.designsystem.Txt
import io.nativestarter.showcase.bluesky.designsystem.UserAvatar
import io.nativestarter.showcase.bluesky.designsystem.alfText
import io.nativestarter.showcase.bluesky.designsystem.hairlineWidth
import io.nativestarter.showcase.bluesky.designsystem.post.PostActions
import io.nativestarter.showcase.bluesky.designsystem.post.PostControls
import io.nativestarter.showcase.bluesky.designsystem.post.PostMeta
import io.nativestarter.showcase.bluesky.designsystem.post.PostText
import io.nativestarter.showcase.bluesky.designsystem.post.PreviewableAvatar
import io.nativestarter.showcase.bluesky.designsystem.pressable
import io.nativestarter.showcase.bluesky.designsystem.text.RichText
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt

/** RN `OUTER_SPACE` and `LINEAR_AVI_WIDTH` (src/screens/PostThread/const.ts). */
private val OUTER_SPACE = Space.lg
private val AVI_WIDTH = 42.dp
private val REPLY_LINE_WIDTH = 2.dp

data class ThreadCallbacks(
    val onBack: () -> Unit = {},
    val onThreadOptions: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onShowOtherReplies: () -> Unit = {},
    val onOpenThread: (uri: String) -> Unit = {},
    val onAnchorLaidOut: () -> Unit = {},
    val onStartReached: () -> Unit = {},
    val onEndReached: () -> Unit = {},
    val onItemSeen: (ThreadItem.Post) -> Unit = {},
    val onReplyToAnchor: () -> Unit = {},
    val postActions: (ThreadItem.Post) -> PostActions = { PostActions(onOpen = {}, onToggleLike = {}, onToggleRepost = {}) },
)

/**
 * RN `PostThread` (src/screens/PostThread/index.tsx), linear view on a phone:
 * header "Post" with the thread options slot, `ThreadError` or the list, and the compose prompt fixed at the bottom.
 * RN has no pull-to-refresh on this screen.
 */
@Composable
fun ThreadScreen(
    state: ThreadUiState,
    callbacks: ThreadCallbacks,
    modifier: Modifier = Modifier,
    ownAvatar: String? = null,
) {
    val theme = LocalAlf.current
    Box(modifier.fillMaxSize().background(theme.bg).testTag("postThreadScreen")) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader("Post", callbacks.onBack) {
                BskyIcon(R.drawable.ic_sliders, IconSize.lg, theme.textContrastMedium, Modifier.pressable("Thread options", callbacks.onThreadOptions))
            }
            val error = state.error
            if (error != null) {
                ThreadError(error, callbacks.onRetry, Modifier.weight(1f).fillMaxWidth())
            } else {
                ThreadList(state, callbacks, Modifier.weight(1f).fillMaxWidth())
            }
        }
        if (state.canReply) {
            MobileComposePrompt(ownAvatar, callbacks.onReplyToAnchor, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun ThreadList(state: ThreadUiState, callbacks: ThreadCallbacks, modifier: Modifier) {
    val listState = rememberLazyListState()
    val current by rememberUpdatedState(callbacks)
    val windowHeight = with(LocalDensity.current) { LocalView.current.rootView.height.toDp() }
    // RN ListFooter height: a large buffer while parents are deferred, else room to pin the anchor to the top.
    val footerHeight = when {
        state.deferParents -> windowHeight * 2
        state.hasParents -> windowHeight - 200.dp
        else -> 180.dp
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.let { (it.visibleItemsInfo.lastOrNull()?.index ?: 0) >= it.totalItemsCount - 5 } }
            .distinctUntilChanged()
            .filter { it }
            .collect { current.onEndReached() }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 }
            .distinctUntilChanged()
            .filter { it }
            .collect { current.onStartReached() }
    }
    // Parents are prepended above the anchor; LazyColumn keeps the first visible item by key, as RN
    // `maintainVisibleContentPosition` keeps the anchor in place.
    LazyColumn(modifier, state = listState) {
        itemsIndexed(state.items, key = { _, item -> item.key }, contentType = { _, item -> item::class.simpleName }) { index, item ->
            when (item) {
                is ThreadItem.Post -> {
                    LaunchedEffect(item.uri) { current.onItemSeen(item) }
                    val actions = callbacks.postActions(item)
                    if (item.depth == 0) {
                        LaunchedEffect(item.uri, state.sort) { current.onAnchorLaidOut() }
                        ThreadAnchor(item.post, actions)
                    } else {
                        ThreadItemPost(item, actions, topBorderOverride = item.depth < 0 && index == 0)
                    }
                }
                is ThreadItem.NotFound -> ThreadItemPostTombstone(blocked = false)
                is ThreadItem.Blocked -> ThreadItemPostTombstone(blocked = true)
                // Sent only to signed-out viewers; the showcase is always signed in.
                is ThreadItem.NoUnauthenticated -> Spacer(Modifier.fillMaxWidth())
                is ThreadItem.ReadMore -> ThreadItemReadMore(item) { current.onOpenThread(item.uri) }
                is ThreadItem.ReadMoreUp -> ThreadItemReadMoreUp { current.onOpenThread(item.uri) }
                // The inline reply composer renders only above the mobile breakpoint.
                is ThreadItem.ReplyComposer -> Spacer(Modifier.fillMaxWidth())
                is ThreadItem.ShowOtherReplies -> ThreadItemShowOtherReplies { current.onShowOtherReplies() }
                is ThreadItem.Skeleton -> when (item.kind) {
                    ThreadItem.SkeletonKind.Anchor -> ThreadItemAnchorSkeleton()
                    ThreadItem.SkeletonKind.Reply -> ThreadItemPostSkeleton(index)
                    ThreadItem.SkeletonKind.ReplyComposer -> Spacer(Modifier.fillMaxWidth())
                }
            }
        }
        item(key = "footer") {
            Column(Modifier.fillMaxWidth().height(footerHeight)) {
                if (!state.isTombstoneView) Hairline()
            }
        }
    }
}

/** RN `ThreadItemAnchor`: parent reply line when not the root, large author block, text_lg body, details, big controls. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreadAnchor(post: PostView, actions: PostActions, modifier: Modifier = Modifier) {
    val theme = LocalAlf.current
    val isRoot = (post.record.reply?.root?.uri ?: post.uri) == post.uri
    Column(modifier.fillMaxWidth()) {
        if (!isRoot) {
            Row(Modifier.padding(start = Space.lg).height(Space.lg).padding(bottom = Space.xs)) {
                Box(Modifier.width(AVI_WIDTH).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.width(REPLY_LINE_WIDTH).fillMaxHeight().background(theme.borderContrastLow))
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .testTag("postThreadItem-by-${post.author.handle}")
                .padding(horizontal = OUTER_SPACE)
                .padding(top = if (isRoot) Space.lg else 0.dp),
        ) {
            Row(Modifier.padding(bottom = Space.md), verticalAlignment = Alignment.CenterVertically) {
                PreviewableAvatar(post, AVI_WIDTH) { actions.onOpenAuthor(post) }
                val name = Sanitize.displayNameOrHandle(post.author.displayName, post.author.handle)
                Column(Modifier.padding(start = Space.md).weight(1f).pressable(name) { actions.onOpenAuthor(post) }) {
                    Txt(name, alfText(FontSize.lg, FontWeight.SemiBold, leading = LineHeight.snug), maxLines = 1)
                    Txt(
                        Sanitize.handle(post.author.handle, "@"),
                        alfText(FontSize.md, color = theme.textContrastMedium, leading = LineHeight.snug),
                        maxLines = 1,
                    )
                }
            }
            Column(Modifier.padding(bottom = Space.sm)) {
                if (post.record.text.isNotEmpty()) {
                    RichText(
                        text = post.record.text,
                        facets = post.record.facets,
                        style = alfText(FontSize.lg),
                        maxLines = Int.MAX_VALUE,
                        testTag = "postText",
                        onSegmentClick = actions.onRichText,
                    )
                }
                // RN ExpandedPostDetails: flex_row, align_center, flex_wrap, gap_sm. At large font scales
                // "Everybody can reply" moves to its own line instead of wrapping inside the row.
                FlowRow(
                    Modifier.padding(top = Space.md),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    // Compose foundation 1.7 has no itemVerticalAlignment; align each item in its line instead.
                    Txt(
                        NiceDate.dotSeparated(post.indexedAt),
                        alfText(FontSize.sm, color = theme.textContrastMedium),
                        Modifier.align(Alignment.CenterVertically),
                    )
                    if (post.record.reply == null) WhoCanReply(Modifier.align(Alignment.CenterVertically))
                }
                EngagementCounts(post)
                PostControls(
                    post,
                    actions,
                    big = true,
                    // `marginLeft: -5` so the first icon lines up with the text.
                    modifier = Modifier.negativeStart(ThreadLayout.controlsOffset).padding(bottom = Space._2xs),
                )
            }
        }
    }
}

/** RN `WhoCanReply` for a post without a threadgate: Earth icon + "Everybody can reply". */
@Composable
private fun WhoCanReply(modifier: Modifier = Modifier) {
    val theme = LocalAlf.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
        BskyIcon(R.drawable.ic_earth, IconSize.sm, theme.textContrastMedium)
        Txt("Everybody can reply", alfText(FontSize.sm, color = theme.textContrastMedium, leading = LineHeight.tight))
    }
}

/** RN anchor stats row: shown unless every count is zero; border_t, mt_md, py_sm, columnGap lg. */
@Composable
private fun EngagementCounts(post: PostView) {
    if (post.repostCount == 0 && post.likeCount == 0 && post.quoteCount == 0 && post.bookmarkCount == 0) return
    Column(Modifier.padding(top = Space.md)) {
        Hairline()
        Row(Modifier.padding(vertical = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
            Stat(post.repostCount, "repost", "reposts", "repostCount-expanded")
            Stat(post.quoteCount, "quote", "quotes", "quoteCount-expanded")
            Stat(post.likeCount, "like", "likes", "likeCount-expanded")
            Stat(post.bookmarkCount, "save", "saves", "bookmarkCount-expanded")
        }
        Hairline()
    }
}

@Composable
private fun Stat(count: Int, one: String, other: String, tag: String) {
    if (count == 0) return
    val theme = LocalAlf.current
    Row(Modifier.testTag(tag)) {
        Txt("$count ", alfText(FontSize.md, FontWeight.SemiBold))
        Txt(if (count == 1) one else other, alfText(FontSize.md, color = theme.textContrastMedium))
    }
}

/**
 * RN `ThreadItemPost`: hairline top border unless a parent reply line or an override hides it; paddingHorizontal 16;
 * a 12 dp row that holds the parent reply line; avatar with the child reply line, meta, text, controls;
 * paddingBottom 8 when no child line or "read more" follows.
 */
@Composable
fun ThreadItemPost(item: ThreadItem.Post, actions: PostActions, topBorderOverride: Boolean, modifier: Modifier = Modifier) {
    val theme = LocalAlf.current
    val post = item.post
    val showTopBorder = !item.ui.showParentReplyLine && !topBorderOverride
    val showChildLine = item.ui.showChildReplyLine || item.ui.precedesChildReadMore
    Column(
        modifier
            .fillMaxWidth()
            .testTag("postThreadItem-by-${post.author.handle}")
            .pressable { actions.onOpen(post) },
    ) {
        if (showTopBorder) Hairline()
        Column(Modifier.padding(horizontal = OUTER_SPACE).padding(bottom = if (showChildLine) 0.dp else OUTER_SPACE / 2)) {
            Row(Modifier.height(12.dp)) {
                Box(Modifier.width(AVI_WIDTH).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    if (item.ui.showParentReplyLine) {
                        Box(Modifier.padding(bottom = Space.xs).width(REPLY_LINE_WIDTH).fillMaxHeight().background(theme.borderContrastLow))
                    }
                }
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    PreviewableAvatar(post, AVI_WIDTH) { actions.onOpenAuthor(post) }
                    if (showChildLine) {
                        Box(Modifier.padding(top = Space.xs).width(REPLY_LINE_WIDTH).weight(1f).background(theme.borderContrastLow))
                    }
                }
                Column(Modifier.weight(1f)) {
                    PostMeta(post)
                    PostText(post, actions)
                    PostControls(post, actions, big = false)
                }
            }
        }
    }
}

/** RN `ThreadItemPostTombstone`: "Post not found" (trash) or "Post blocked" (person-x) on bg_contrast_25. */
@Composable
private fun ThreadItemPostTombstone(blocked: Boolean) {
    val theme = LocalAlf.current
    Column(Modifier.fillMaxWidth().padding(bottom = Space.xs).padding(horizontal = OUTER_SPACE).padding(top = OUTER_SPACE / 1.2f)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.sm))
                .background(theme.bgContrast25)
                .padding(vertical = OUTER_SPACE / 1.2f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(AVI_WIDTH), contentAlignment = Alignment.Center) {
                BskyIcon(if (blocked) R.drawable.ic_person_x else R.drawable.ic_trash, IconSize.md, theme.textContrastMedium)
            }
            Txt(
                if (blocked) "Post blocked" else "Post not found",
                alfText(FontSize.md, FontWeight.SemiBold, theme.textContrastMedium),
            )
        }
    }
}

/** RN `ThreadItemShowOtherReplies`: border_t row, py_lg px_xl, a 26 dp eye-slash circle and "Show more replies". */
@Composable
private fun ThreadItemShowOtherReplies(onPress: () -> Unit) {
    val theme = LocalAlf.current
    Column(Modifier.fillMaxWidth().pressable("Show more replies", onPress)) {
        Hairline()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.padding(end = Space.xs).size(26.dp).clip(CircleShape).background(theme.bgContrast25),
                contentAlignment = Alignment.Center,
            ) {
                BskyIcon(R.drawable.ic_eye_off, IconSize.sm, theme.textContrastMedium)
            }
            Txt(
                "Show more replies",
                alfText(FontSize.sm, color = theme.textContrastMedium, leading = LineHeight.snug),
                Modifier.weight(1f),
                maxLines = 1,
            )
        }
    }
}

/** RN `ThreadItemReadMore`, linear: a rounded corner line from the avatar column and "Read N more replies". */
@Composable
private fun ThreadItemReadMore(item: ThreadItem.ReadMore, onPress: () -> Unit) {
    val theme = LocalAlf.current
    val line = theme.borderContrastLow
    Row {
        Canvas(Modifier.padding(start = (AVI_WIDTH - REPLY_LINE_WIDTH) / 2 + 16.dp).size(width = AVI_WIDTH / 2 + 10.dp, height = 18.dp)) {
            val stroke = 2.dp.toPx()
            val radius = Radius.sm.toPx()
            val x = stroke / 2
            val y = size.height - stroke / 2
            val path = Path().apply {
                moveTo(x, 0f)
                lineTo(x, y - radius)
                quadraticTo(x, y, x + radius, y)
                lineTo(size.width, y)
            }
            drawPath(path, line, style = Stroke(width = stroke))
        }
        Row(
            Modifier.pressable("Read more replies", onPress).padding(top = Space.sm, bottom = Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BskyIcon(R.drawable.ic_circle_plus, 18.dp, theme.textContrastLow)
            Txt(
                if (item.moreReplies == 1) "Read 1 more reply" else "Read ${item.moreReplies} more replies",
                alfText(FontSize.sm, color = theme.textContrastMedium),
            )
        }
    }
}

/** RN `ThreadItemReadMoreUp`: "Continue thread..." above the first parent, with a short reply line below. */
@Composable
private fun ThreadItemReadMoreUp(onPress: () -> Unit) {
    val theme = LocalAlf.current
    Column(Modifier.fillMaxWidth().pressable("Continue thread", onPress).padding(top = OUTER_SPACE).padding(horizontal = OUTER_SPACE)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(AVI_WIDTH), contentAlignment = Alignment.Center) {
                BskyIcon(R.drawable.ic_circle_arrow_up, IconSize.lg, theme.textContrastLow)
            }
            Txt("Continue thread...", alfText(FontSize.sm, color = theme.textContrastMedium))
        }
        Box(Modifier.width(AVI_WIDTH), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.padding(top = Space.xs).height(OUTER_SPACE / 2).width(REPLY_LINE_WIDTH).background(theme.borderContrastLow))
        }
    }
}

/** RN `Skeleton.Text`: a bar 0.7 of the line height with 0.15 above and below, bg_contrast_50, rounded_md. */
@Composable
private fun SkeletonText(size: TextUnit, widthFraction: Float, modifier: Modifier = Modifier, blend: Boolean = false) {
    val theme = LocalAlf.current
    val lineHeight = (size.value * LineHeight.snug).roundToInt()
    Box(modifier.fillMaxWidth(widthFraction).padding(vertical = (lineHeight * 0.15f).dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height((lineHeight * 0.7f).dp)
                .alpha(if (blend) 0.6f else 1f)
                .clip(RoundedCornerShape(Radius.md))
                .background(theme.bgContrast50),
        )
    }
}

@Composable
private fun SkeletonCircle(size: Dp, blend: Boolean = false) {
    val theme = LocalAlf.current
    Box(Modifier.size(size).alpha(if (blend) 0.6f else 1f).clip(CircleShape).background(theme.bgContrast50))
}

@Composable
private fun SkeletonPill(size: Dp) {
    val theme = LocalAlf.current
    Box(Modifier.size(width = size * 1.618f, height = size).alpha(0.6f).clip(RoundedCornerShape(Radius.full)).background(theme.bgContrast50))
}

/** RN `PostControlsSkeleton`: three pills (max width 320) and three circles, 28 dp rows (32 when big). */
@Composable
private fun PostControlsSkeleton(big: Boolean) {
    val size = (if (big) 32 else 28).dp - 8.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f)) {
            Row(Modifier.widthIn(max = 320.dp).fillMaxWidth()) {
                repeat(3) { index ->
                    Box(Modifier.weight(1f).then(if (index == 0) Modifier.negativeStart(4.dp) else Modifier).padding(Space.xs)) {
                        SkeletonPill(size)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(if (big) Space.sm else Space.xs)) {
            repeat(3) { Box(Modifier.padding(Space.xs)) { SkeletonCircle(size, blend = true) } }
        }
    }
}

/** RN `ThreadItemAnchorSkeleton`. */
@Composable
private fun ThreadItemAnchorSkeleton() {
    Column(Modifier.fillMaxWidth().padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
            SkeletonCircle(AVI_WIDTH)
            Column(Modifier.weight(1f)) {
                SkeletonText(FontSize.lg, 0.2f)
                SkeletonText(FontSize.md, 0.4f, blend = true)
            }
        }
        Column {
            SkeletonText(FontSize.xl, 1f)
            SkeletonText(FontSize.xl, 0.6f)
        }
        SkeletonText(FontSize.sm, 0.5f)
        PostControlsSkeleton(big = true)
    }
}

/** RN `ThreadItemPostSkeleton`: two text lines on even rows, one on odd rows. */
@Composable
private fun ThreadItemPostSkeleton(index: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = OUTER_SPACE, vertical = OUTER_SPACE / 1.5f),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        SkeletonCircle(AVI_WIDTH)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                SkeletonText(FontSize.md, 0.2f)
                SkeletonText(FontSize.md, 0.3f, blend = true)
            }
            Column {
                SkeletonText(FontSize.md, if (index % 2 == 0) 1f else 0.6f, blend = true)
                if (index % 2 == 0) SkeletonText(FontSize.md, 0.6f, blend = true)
            }
            PostControlsSkeleton(big = false)
        }
    }
}

/** RN `ThreadError`: centred title and message (max width 260) and a small inverted "Retry" button. */
@Composable
private fun ThreadError(error: ThreadErrorCopy, onRetry: () -> Unit, modifier: Modifier) {
    val theme = LocalAlf.current
    Column(
        modifier.padding(horizontal = OUTER_SPACE).padding(top = OUTER_SPACE * 2, bottom = OUTER_SPACE),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = 260.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Txt(error.title, alfText(FontSize.lg, FontWeight.SemiBold, leading = LineHeight.snug), Modifier.fillMaxWidth(), align = TextAlign.Center)
                Txt(
                    error.message,
                    alfText(FontSize.sm, color = theme.textContrastMedium, leading = LineHeight.snug),
                    Modifier.fillMaxWidth(),
                    align = TextAlign.Center,
                )
            }
            AlfButton("Retry", onRetry, color = ButtonColor.SecondaryInverted, iconEnd = R.drawable.ic_rotate_ccw)
        }
    }
}

/**
 * RN `MobileComposePrompt` + `ThreadComposePrompt` on a phone: fixed above the bottom bar, a gradient from transparent
 * to the background, and a rounded row (bg_contrast_25, hairline border, p_sm, gap_sm) with the own avatar and
 * "Write your reply". Press: open the composer, then a light haptic.
 */
@Composable
private fun MobileComposePrompt(avatar: String?, onPress: () -> Unit, modifier: Modifier) {
    val theme = LocalAlf.current
    val haptics = LocalHaptics.current
    val hairline = hairlineWidth()
    val bg = theme.bg
    val press = {
        onPress()
        haptics.play(HapticStrength.Light)
    }
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0.15f to bg.copy(alpha = 0f), 0.4f to bg))
            .padding(horizontal = Space.sm)
            .padding(bottom = Space._2xs),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.full))
                .background(theme.bgContrast25)
                .border(hairline, theme.borderContrastLow, RoundedCornerShape(Radius.full))
                .semantics { onClick(label = "Opens composer") { press(); true } }
                .pressable("Compose reply", press)
                .padding(Space.sm),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(avatar, AvatarSize.composePrompt)
            Txt("Write your reply", alfText(FontSize.md, color = theme.textContrastMedium))
        }
    }
}

/** Equivalent of RN `marginLeft: -x`: widens the child and shifts it left. */
fun Modifier.negativeStart(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = amount.roundToPx()
    val widened = if (constraints.hasBoundedWidth) {
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
    } else {
        constraints
    }
    val placeable = measurable.measure(widened)
    val width = (placeable.width - extra).coerceAtLeast(0)
    layout(width, placeable.height) { placeable.place(-extra, 0) }
}

/** RN `ThreadItemAnchor` PostControls style: `{marginLeft: -5}` (no ALF token). */
private object ThreadLayout {
    val controlsOffset = 5.dp
}

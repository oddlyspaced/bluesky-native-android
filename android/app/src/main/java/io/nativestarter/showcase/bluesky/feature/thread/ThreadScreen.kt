package io.nativestarter.showcase.bluesky.feature.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.nativestarter.showcase.bluesky.R
import io.nativestarter.showcase.bluesky.core.network.PostView
import io.nativestarter.showcase.bluesky.designsystem.BskyIcon
import io.nativestarter.showcase.bluesky.designsystem.FontSize
import io.nativestarter.showcase.bluesky.designsystem.Hairline
import io.nativestarter.showcase.bluesky.designsystem.LineHeight
import io.nativestarter.showcase.bluesky.designsystem.NiceDate
import io.nativestarter.showcase.bluesky.designsystem.Radius
import io.nativestarter.showcase.bluesky.designsystem.ScreenHeader
import io.nativestarter.showcase.bluesky.designsystem.Space
import io.nativestarter.showcase.bluesky.designsystem.Theme
import io.nativestarter.showcase.bluesky.designsystem.Txt
import io.nativestarter.showcase.bluesky.designsystem.UserAvatar
import io.nativestarter.showcase.bluesky.designsystem.alfText
import io.nativestarter.showcase.bluesky.designsystem.pressable
import io.nativestarter.showcase.bluesky.designsystem.post.PostActions
import io.nativestarter.showcase.bluesky.designsystem.post.PostControls
import io.nativestarter.showcase.bluesky.designsystem.post.PostMeta
import io.nativestarter.showcase.bluesky.designsystem.post.PostText

/** RN src/screens/PostThread/index.tsx (linear view) for CP-02. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    state: ThreadUiState,
    actions: PostActions,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Theme.bg).statusBarsPadding().testTag("postThreadScreen")) {
        ScreenHeader("Post", onBack) {
            BskyIcon(R.drawable.ic_sliders, 24.dp, Theme.textContrastMedium)
        }
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.parents, key = { "p-" + it.uri }) { ThreadReplyItem(it, actions, showTopBorder = false) }
                state.anchor?.let { anchor ->
                    item(key = "anchor") {
                        ThreadAnchor(anchor, actions, isRoot = anchor.record.reply == null && state.parents.isEmpty())
                    }
                }
                items(state.replies, key = { it.uri }) { ThreadReplyItem(it, actions, showTopBorder = true) }
                if (state.anchor != null) item(key = "end") { Hairline() }
                state.error?.let { err ->
                    item(key = "error") {
                        Txt(err, alfText(FontSize.md, color = Theme.textContrastMedium), Modifier.padding(Space.lg))
                    }
                }
            }
        }
        ReplyPrompt()
    }
}

/** RN `ThreadItemAnchor`: large author block, text_lg body, expanded details and big controls. */
@Composable
fun ThreadAnchor(post: PostView, actions: PostActions, isRoot: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .testTag("postThreadItem-by-${post.author.handle}")
            .padding(horizontal = Space.lg)
            .padding(top = if (isRoot) Space.lg else 0.dp),
    ) {
        Row(Modifier.padding(bottom = Space.md), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(post.author.avatar, 42.dp)
            Column(Modifier.padding(start = Space.md).weight(1f)) {
                Txt(
                    post.author.displayName?.takeIf { it.isNotBlank() } ?: post.author.handle,
                    alfText(FontSize.lg, FontWeight.SemiBold, leading = LineHeight.snug),
                    maxLines = 1,
                )
                Txt(
                    "@${post.author.handle}",
                    alfText(FontSize.md, color = Theme.textContrastMedium, leading = LineHeight.snug),
                    maxLines = 1,
                )
            }
        }
        Column(Modifier.padding(bottom = Space.sm)) {
            if (post.record.text.isNotEmpty()) {
                Txt(post.record.text, alfText(FontSize.lg), Modifier.testTag("postText"))
            }
            Row(
                Modifier.padding(top = Space.md),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(NiceDate.dotSeparated(post.indexedAt), alfText(FontSize.sm, color = Theme.textContrastMedium))
                if (isRoot) WhoCanReply()
            }
            EngagementCounts(post)
            PostControls(
                post,
                actions,
                big = true,
                // `marginLeft: -5` so the first icon lines up with the text.
                modifier = Modifier.negativeStart(5.dp).padding(bottom = Space._2xs),
            )
        }
    }
}

/** RN `WhoCanReply` for a post without a threadgate: Earth icon + "Everybody can reply". */
@Composable
private fun WhoCanReply() {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
        BskyIcon(R.drawable.ic_earth, 16.dp, Theme.textContrastMedium)
        Txt(
            "Everybody can reply",
            alfText(FontSize.sm, color = Theme.textContrastMedium, leading = LineHeight.tight),
        )
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
    Row(Modifier.testTag(tag)) {
        Txt("$count ", alfText(FontSize.md, FontWeight.SemiBold))
        Txt(if (count == 1) one else other, alfText(FontSize.md, color = Theme.textContrastMedium))
    }
}

/** RN `ThreadItemPost`: 12dp top spacer, row gap_md with 42dp avatar, meta, text, controls, 8dp bottom. */
@Composable
fun ThreadReplyItem(post: PostView, actions: PostActions, showTopBorder: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .testTag("postThreadItem-by-${post.author.handle}")
            .pressable { actions.onOpen(post) },
    ) {
        if (showTopBorder) Hairline()
        Row(Modifier.padding(horizontal = Space.lg).padding(top = Space.md, bottom = Space.sm)) {
            UserAvatar(post.author.avatar, 42.dp)
            Column(Modifier.padding(start = Space.md).weight(1f)) {
                PostMeta(post)
                PostText(post)
                PostControls(post, actions, big = false)
            }
        }
    }
}

/** RN thread bottom composer prompt ("Write your reply"). Composing is outside this slice. */
@Composable
private fun ReplyPrompt() {
    Box(Modifier.fillMaxWidth().background(Theme.bg).padding(horizontal = Space.sm, vertical = Space.xs)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.full))
                .background(Theme.bgContrast25)
                .border(1.dp, Theme.borderContrastLow, RoundedCornerShape(Radius.full))
                .padding(horizontal = Space.sm, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(null, 24.dp)
            Spacer(Modifier.padding(start = Space.sm))
            Txt("Write your reply", alfText(FontSize.md, color = Theme.textContrastMedium))
        }
    }
}

/** Equivalent of RN `marginLeft: -x`: widens the child and shifts it left. */
fun Modifier.negativeStart(amount: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val extra = amount.roundToPx()
    val widened = if (constraints.hasBoundedWidth) {
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
    } else constraints
    val placeable = measurable.measure(widened)
    val width = (placeable.width - extra).coerceAtLeast(0)
    layout(width, placeable.height) { placeable.place(-extra, 0) }
}

package io.nativestarter.showcase.bluesky.designsystem.feed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.nativestarter.showcase.bluesky.R
import io.nativestarter.showcase.bluesky.designsystem.BskyIcon
import io.nativestarter.showcase.bluesky.designsystem.FontSize
import io.nativestarter.showcase.bluesky.designsystem.LineHeight
import io.nativestarter.showcase.bluesky.designsystem.LocalAlf
import io.nativestarter.showcase.bluesky.designsystem.Radius
import io.nativestarter.showcase.bluesky.designsystem.Space
import io.nativestarter.showcase.bluesky.designsystem.Txt
import io.nativestarter.showcase.bluesky.designsystem.alfText
import io.nativestarter.showcase.bluesky.designsystem.pressable

/** RN `PostLoadingPlaceholder` (src/view/com/util/LoadingPlaceholder.tsx). */
@Composable
fun PostLoadingPlaceholder(modifier: Modifier = Modifier) {
    val theme = LocalAlf.current
    Row(
        modifier.fillMaxWidth().padding(start = 10.dp, end = 15.dp, top = 20.dp, bottom = 5.dp).testTag("postLoadingPlaceholder"),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(start = 8.dp, end = 12.dp)
                .offset(y = (-6).dp)
                .size(42.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(theme.placeholder),
        )
        Column(Modifier.weight(1f)) {
            Bar(Modifier.width(100.dp).padding(bottom = 10.dp))
            Bar(Modifier.fillMaxWidth(0.95f).padding(bottom = 8.dp))
            Bar(Modifier.fillMaxWidth(0.95f).padding(bottom = 8.dp))
            Bar(Modifier.fillMaxWidth(0.8f).padding(bottom = 11.dp))
            Row(Modifier.fillMaxWidth().alpha(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(R.drawable.ic_bubble, R.drawable.ic_repost, R.drawable.ic_heart, R.drawable.ic_dots).forEach {
                    BskyIcon(it, 18.dp, theme.controlIcon, Modifier.padding(5.dp))
                }
            }
        }
    }
}

@Composable
private fun Bar(modifier: Modifier) {
    val theme = LocalAlf.current
    Box(modifier) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)).background(theme.placeholder))
    }
}

/**
 * RN `ErrorMessage` (src/view/com/util/error/ErrorMessage.tsx): pink row with a warning icon, the cleaned error
 * and a round "Retry" button.
 */
@Composable
fun ErrorMessage(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    val theme = LocalAlf.current
    Row(
        modifier
            .fillMaxWidth()
            .testTag("errorMessageView")
            .background(theme.errorBanner)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BskyIcon(R.drawable.ic_warning, 20.dp, Color.White)
        Txt(message, alfText(FontSize.md, FontWeight.Medium, Color.White, leading = LineHeight.snug), Modifier.weight(1f))
        if (onRetry != null) {
            Box(
                Modifier
                    .testTag("errorMessageTryAgainButton")
                    .size(33.dp)
                    .clip(CircleShape)
                    .semantics { onClick(label = "Retries the last action, which errored out") { onRetry(); true } }
                    .pressable("Retry", onRetry),
                contentAlignment = Alignment.Center,
            ) {
                BskyIcon(R.drawable.ic_rotate_ccw, 20.dp, Color.White)
            }
        }
    }
}

/** RN `LoadMoreRetryBtn` (src/view/com/util/LoadMoreRetryBtn.tsx). */
@Composable
fun LoadMoreRetryButton(label: String, onPress: () -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalAlf.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 1.dp)
            .pressable(label, onPress)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BskyIcon(R.drawable.ic_rotate_ccw, 18.dp, theme.textContrastMedium)
        Txt(label, alfText(FontSize.md, color = theme.textContrastMedium), Modifier.weight(1f))
    }
}

/** RN `ViewFullThread` (src/view/com/posts/ViewFullThread.tsx): a short line, three dots and "View full thread". */
@Composable
fun ViewFullThreadRow(onPress: () -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalAlf.current
    val line = theme.replyLine
    val dots = theme.palette.contrast(200)
    Row(
        modifier.fillMaxWidth().pressable("View full thread", onPress).padding(start = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(42.dp), contentAlignment = Alignment.TopCenter) {
            Canvas(Modifier.size(width = 4.dp, height = 40.dp)) {
                val px = density
                drawLine(line, Offset(2 * px, 0f), Offset(2 * px, 15 * px), strokeWidth = 2 * px)
                listOf(22f, 28f, 34f).forEach { drawCircle(dots, radius = 1.5f * px, center = Offset(2 * px, it * px)) }
            }
        }
        Txt(
            "View full thread",
            alfText(FontSize.md, color = theme.textLink),
            Modifier.padding(top = 18.dp, bottom = 4.dp),
        )
    }
}

/** RN `EmptyState` (src/view/com/util/EmptyState.tsx): icon, message and an optional button. */
@Composable
fun EmptyState(
    icon: Int,
    message: String,
    modifier: Modifier = Modifier,
    button: (@Composable () -> Unit)? = null,
    /** ALF icon size: `3xl` (48) by default, `2xl` (32) for "Posts hidden". */
    iconSize: androidx.compose.ui.unit.Dp = 48.dp,
) {
    val theme = LocalAlf.current
    Column(modifier.fillMaxWidth().testTag("emptyState"), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(top = Space._5xl).size(64.dp), contentAlignment = Alignment.Center) {
            BskyIcon(icon, iconSize, theme.textContrastLow)
        }
        Txt(
            message,
            alfText(FontSize.md, FontWeight.Medium, theme.textContrastHigh, leading = LineHeight.snug),
            Modifier.fillMaxWidth(0.6f).padding(top = Space.xs, bottom = if (button == null) Space._5xl else 0.dp),
            align = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (button != null) {
            Spacer(Modifier.height(Space.md))
            button()
            Spacer(Modifier.height(Space._5xl))
        }
    }
}

/** A small rounded button (ALF `Button size="small"`), used by empty and error states. */
@Composable
fun SmallPillButton(label: String, background: Color, foreground: Color, onPress: () -> Unit, modifier: Modifier = Modifier, testTag: String? = null) {
    Box(
        modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clip(RoundedCornerShape(Radius.full))
            .background(background)
            .pressable(label, onPress)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(label, alfText(FontSize.sm, FontWeight.Medium, foreground))
    }
}

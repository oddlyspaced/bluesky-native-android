package io.nativestarter.showcase.bluesky.designsystem.text

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import io.nativestarter.showcase.bluesky.core.network.Facet
import io.nativestarter.showcase.bluesky.designsystem.LocalAlf
import io.nativestarter.showcase.bluesky.designsystem.resolveTextStyle

/**
 * RN `RichText` (src/components/RichText.tsx) on native: links, mentions and tags in `text_link` colour with no
 * underline; links show `toShortUrl`; emoji-only text without facets is 1.85x larger. [onSegmentClick] receives
 * the link, mention or tag that was tapped.
 */
@Composable
fun RichText(
    text: String,
    facets: List<Facet>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    testTag: String? = null,
    onSegmentClick: (RichSegment) -> Unit = {},
) {
    val theme = LocalAlf.current
    val segments = remember(text, facets) { RichTextModel.segments(text, facets) }
    val linkStyles = TextLinkStyles(SpanStyle(color = theme.textLink))
    val annotated = buildAnnotatedString {
        for (segment in segments) {
            when (segment) {
                is RichSegment.Plain -> append(segment.text)
                is RichSegment.Link -> withLink(LinkAnnotation.Clickable("link", linkStyles) { onSegmentClick(segment) }) {
                    append(RichTextModel.toShortUrl(segment.text))
                }
                is RichSegment.Mention -> withLink(LinkAnnotation.Clickable("mention", linkStyles) { onSegmentClick(segment) }) {
                    append(segment.text)
                }
                is RichSegment.Tag -> withLink(LinkAnnotation.Clickable("tag", linkStyles) { onSegmentClick(segment) }) {
                    append(segment.text)
                }
            }
        }
    }
    val base = if (facets.isEmpty() && RichTextModel.isOnlyEmoji(text)) {
        style.copy(fontSize = style.fontSize * 1.85f, lineHeight = style.lineHeight * 1.85f)
    } else {
        style
    }
    RnText(
        text = annotated,
        style = resolveTextStyle(base),
        modifier = modifier.then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

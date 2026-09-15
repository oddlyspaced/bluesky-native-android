package io.nativestarter.showcase.bluesky.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ToastType { Default, Success, Warning, Error }

data class ToastMessage(val text: String, val type: ToastType, val id: Long)

/** RN `Toast.show(text, {type})` (src/components/Toast): one visible toast, replaced by the next, 3 s duration. */
class Toaster {
    private val _current = MutableStateFlow<ToastMessage?>(null)
    val current: StateFlow<ToastMessage?> = _current.asStateFlow()
    private var nextId = 0L

    fun show(text: String, type: ToastType = ToastType.Default) {
        _current.value = ToastMessage(text, type, nextId++)
    }

    fun dismiss(id: Long) {
        if (_current.value?.id == id) _current.value = null
    }

    companion object {
        const val DURATION_MS = 3_000L
    }
}

val LocalToaster = staticCompositionLocalOf { Toaster() }

/** Global outlet at the top of the screen, `px_xl`, as the RN Toaster. */
@Composable
fun ToastHost(toaster: Toaster, modifier: Modifier = Modifier) {
    val message by toaster.current.collectAsStateWithLifecycle()
    val current = message ?: return
    LaunchedEffect(current.id) {
        delay(Toaster.DURATION_MS)
        toaster.dismiss(current.id)
    }
    val theme = LocalAlf.current
    val accent = when (current.type) {
        ToastType.Success -> theme.palette.positive(500)
        ToastType.Warning -> theme.palette.yellow
        ToastType.Error -> theme.palette.negative(500)
        ToastType.Default -> theme.borderContrastLow
    }
    Box(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.xl, vertical = Space.sm), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .fillMaxWidth()
                .testTag("toast")
                .semantics { liveRegion = LiveRegionMode.Polite }
                .background(theme.bg, RoundedCornerShape(Radius.md))
                .border(1.dp, accent, RoundedCornerShape(Radius.md))
                .padding(horizontal = Space.lg, vertical = Space.md),
        ) {
            Txt(current.text, alfText(FontSize.md, FontWeight.Medium, leading = LineHeight.snug))
        }
    }
}

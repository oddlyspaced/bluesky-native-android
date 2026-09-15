package io.nativestarter.showcase.bluesky.core.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf

enum class HapticStrength { Light, Medium, Heavy }

/** RN `useHaptics()` (src/lib/haptics.ts). Screens call this interface, never the Vibrator API. */
interface Haptics {
    fun play(strength: HapticStrength = HapticStrength.Medium)
}

object NoopHaptics : Haptics {
    override fun play(strength: HapticStrength) = Unit
}

/**
 * Android implementation of expo-haptics as RN uses it:
 * - RN maps every strength to `ImpactFeedbackStyle.Light` on Android ("medium impact was too strong on Android").
 * - expo-haptics `light` on API 26+: `VibrationEffect.createWaveform(oldSDKPattern [0, 20], [0, 100], -1)`,
 *   a single 20 ms pulse at amplitude 100 through the Vibrator service.
 */
class VibratorHaptics(context: Context) : Haptics {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun play(strength: HapticStrength) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 20), intArrayOf(0, 100), -1))
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics> { NoopHaptics }

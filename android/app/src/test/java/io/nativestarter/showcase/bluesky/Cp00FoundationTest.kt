package io.nativestarter.showcase.bluesky

import io.nativestarter.showcase.bluesky.core.analytics.JsonlEventLog
import io.nativestarter.showcase.bluesky.core.analytics.toClout
import io.nativestarter.showcase.bluesky.core.common.ErrorText
import io.nativestarter.showcase.bluesky.core.network.ToggleQueue
import io.nativestarter.showcase.bluesky.core.network.XrpcException
import io.nativestarter.showcase.bluesky.designsystem.AlfPalette
import io.nativestarter.showcase.bluesky.designsystem.AlfThemes
import io.nativestarter.showcase.bluesky.designsystem.Sanitize
import io.nativestarter.showcase.bluesky.designsystem.ThemeName
import io.nativestarter.showcase.bluesky.shell.UnreadPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class Cp00FoundationTest {

    private fun hex(c: androidx.compose.ui.graphics.Color) = String.format("#%06X", c.value.toLong().ushr(32).toInt() and 0xFFFFFF)

    @Test
    fun darkAndDimPalettesMatchInvertPalette() {
        // Expected values from @bsky.app/alf invertPalette applied to DEFAULT_PALETTE and DEFAULT_SUBDUED_PALETTE.
        assertEquals("#000000", hex(AlfThemes.dark.bg))
        assertEquals("#FFFFFF", hex(AlfThemes.dark.text))
        assertEquals("#232E3E", hex(AlfThemes.dark.borderContrastLow))
        assertEquals("#A5B2C5", hex(AlfThemes.dark.textContrastMedium))
        assertEquals("#151D28", hex(AlfThemes.dim.bg))
        assertEquals("#2C3A4E", hex(AlfThemes.dim.borderContrastLow))
        assertEquals("#006AFF", hex(AlfThemes.dark.primary))
        assertEquals("#0F73FF", hex(AlfThemes.dim.primary))
        // text_link: primary_600 in dark schemes, which is the inverted primary_400.
        assertEquals("#4291FF", hex(AlfThemes.dark.textLink))
        assertEquals("#4D97FF", hex(AlfThemes.dim.textLink))
        assertEquals("#006AFF", hex(AlfThemes.light.textLink))
        assertEquals(AlfPalette.DEFAULT.contrast, AlfPalette.DEFAULT.inverted().inverted().contrast)
    }

    @Test
    fun systemColorModeFollowsRnDefaults() {
        assertEquals(ThemeName.Light, AlfThemes.forSystem(systemDark = false).name)
        assertEquals("darkTheme defaults to dim", ThemeName.Dim, AlfThemes.forSystem(systemDark = true).name)
        assertEquals(ThemeName.Dark, AlfThemes.forSystem(systemDark = true, darkTheme = ThemeName.Dark).name)
        assertEquals("#DCE2EA", hex(AlfThemes.light.replyLine))
        assertEquals("#394960", hex(AlfThemes.dim.replyLine))
    }

    @Test
    fun unreadPolicyMatchesRn() {
        assertFalse("a zero count always polls", UnreadPolicy.skipPoll(0, 0.99))
        assertTrue(UnreadPolicy.skipPoll(30, 0.0))
        assertTrue(UnreadPolicy.skipPoll(3, 0.5))
        assertFalse(UnreadPolicy.skipPoll(3, 0.49))
        assertEquals("", UnreadPolicy.badge(0))
        assertEquals("1", UnreadPolicy.badge(1))
        assertEquals("30+", UnreadPolicy.badge(31))
        assertEquals("1 unread item", UnreadPolicy.hint("1"))
        assertEquals("30+ unread items", UnreadPolicy.hint("30+"))
        assertEquals("", UnreadPolicy.hint(""))
    }

    @Test
    fun eventsAreJsonLinesWithoutNullProps() {
        assertEquals(
            """{"name":"post:like","props":{"uri":"at://x","logContext":"FeedItem","likerClout":1}}""",
            JsonlEventLog.encode("post:like", linkedMapOf("uri" to "at://x", "logContext" to "FeedItem", "feedDescriptor" to null, "likerClout" to 1)),
        )
        assertEquals("""{"name":"composerPrompt:press","props":{}}""", JsonlEventLog.encode("composerPrompt:press", emptyMap()))
    }

    @Test
    fun cloutIsRoundedNaturalLog() {
        assertEquals(null, toClout(null))
        assertEquals(0, toClout(0))
        assertEquals(0, toClout(1))
        assertEquals(1, toClout(2))
        assertEquals(2, toClout(8))
        assertEquals(5, toClout(150))
    }

    @Test
    fun errorTextFollowsCleanError() {
        assertEquals(ErrorText.NETWORK, ErrorText.clean(IOException("unexpected end of stream")))
        assertEquals(ErrorText.SERVER, ErrorText.clean(XrpcException(502, "UpstreamFailure", "bad gateway")))
        assertEquals("Profile not found", ErrorText.clean(XrpcException(400, "InvalidRequest", "Profile not found")))
        assertEquals("Custom message", ErrorText.clean(XrpcException(400, "InvalidRequest", "Custom message")))
        assertEquals("boom", ErrorText.clean(IllegalStateException("Error: boom")))
        assertEquals(ErrorText.RATE_LIMIT, ErrorText.cleanForScreen(XrpcException(429, "RateLimitExceeded", "slow down")))
    }

    @Test
    fun sanitizersMatchRn() {
        assertEquals("Alice", Sanitize.displayName("  Alice✅  "))
        assertEquals("A B", Sanitize.displayName("A ​ B"))
        assertEquals("", Sanitize.displayName("Alice", blurred = true))
        assertEquals("‪@alice.test‬", Sanitize.handle("Alice.Test", "@"))
        assertEquals("⚠Invalid Handle", Sanitize.handle("handle.invalid"))
        // RN sanitizeDisplayName(displayName || sanitizeHandle(handle)): the LRE/PDF marks added by sanitizeHandle
        // are control characters, so sanitizeDisplayName removes them again.
        assertEquals("bob.test", Sanitize.displayNameOrHandle("", "bob.test"))
    }

    @Test
    fun toggleQueueCoalescesAndReportsTheConfirmedState() = runTest(UnconfinedTestDispatcher()) {
        val requests = mutableListOf<Boolean>()
        val gate = CompletableDeferred<Unit>()
        var settled: String? = "unset"
        val queue = ToggleQueue<String?>(
            scope = this,
            request = { _, isOn ->
                requests += isOn
                if (requests.size == 1) gate.await()
                if (isOn) "like-uri" else null
            },
            onSettled = { settled = it },
        )
        queue.toggle(true) { null }
        queue.toggle(false) { null }
        queue.toggle(true) { null }
        gate.complete(Unit)
        assertEquals("the waiting toggle equal to the running one is skipped", listOf(true), requests)
        assertEquals("like-uri", settled)
    }

    @Test
    fun toggleQueueKeepsConfirmedStateWhenACompensatingRequestFails() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        var settled: String? = "unset"
        var calls = 0
        val queue = ToggleQueue<String?>(
            scope = this,
            request = { _, isOn ->
                calls++
                if (calls == 1) {
                    gate.await()
                    "like-uri"
                } else {
                    error("unlike failed")
                }
            },
            onSettled = { settled = it },
        )
        queue.toggle(true) { null }
        queue.toggle(false) { null }
        gate.complete(Unit)
        assertEquals(2, calls)
        assertEquals("a failed unlike leaves the confirmed like visible", "like-uri", settled)
    }
}

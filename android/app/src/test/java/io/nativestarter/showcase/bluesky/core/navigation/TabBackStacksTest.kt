package io.nativestarter.showcase.bluesky.core.navigation

import androidx.compose.runtime.snapshots.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabBackStacksTest {
    private fun stacks() = TabBackStacks(Routes.TABS)

    @Test
    fun eachTabKeepsItsOwnStack() {
        val s = stacks()
        s.push(Routes.THREAD, "at://home-thread")
        s.press(Routes.PROFILE)
        s.push(Routes.THREAD, "at://profile-thread")
        assertEquals(listOf(Routes.PROFILE, Routes.THREAD), s.stack(Routes.PROFILE).map { it.route })
        s.press(Routes.HOME)
        assertEquals("at://home-thread", s.current.arg)
        s.press(Routes.PROFILE)
        assertEquals("at://profile-thread", s.current.arg)
    }

    @Test
    fun pressingTheSelectedTabPopsToRootThenSoftResets() {
        val s = stacks()
        val thread = s.push(Routes.THREAD, "a")
        val nested = s.push(Routes.THREAD, "b")
        assertEquals(TabBackStacks.Press.PoppedToRoot(listOf(thread, nested)), s.press(Routes.HOME))
        assertEquals(Routes.HOME, s.current.route)
        assertEquals(TabBackStacks.Press.SoftReset, s.press(Routes.HOME))
    }

    @Test
    fun backPopsThenReturnsToTheFirstTabThenIsNotHandled() {
        val s = stacks()
        s.press(Routes.PROFILE)
        val thread = s.push(Routes.THREAD, "a")
        assertTrue(s.canGoBack())
        assertEquals(TabBackStacks.Back.Popped(thread), s.back())
        assertEquals(TabBackStacks.Back.ToFirstTab, s.back())
        assertEquals(Routes.HOME, s.selected)
        assertFalse(s.canGoBack())
        assertEquals(TabBackStacks.Back.NotHandled, s.back())
    }

    @Test
    fun entryIdsAreUnique() {
        val s = stacks()
        val a = s.push(Routes.THREAD, "x")
        s.back()
        val b = s.push(Routes.THREAD, "x")
        assertTrue(a.id != b.id)
        assertEquals(Routes.TABS.size + 1, s.allEntries().size)
    }

    /**
     * The shell recomposes because it read the stacks, not because a version counter was bumped beside a
     * suppressed unused read (review B12).
     */
    @Test
    fun stackChangesAreObservableSnapshotState() {
        val s = stacks()
        var applied = 0
        val handle = Snapshot.registerApplyObserver { _, _ -> applied++ }
        try {
            Snapshot.withMutableSnapshot { s.push(Routes.THREAD, "a") }
            assertTrue("pushing a screen notifies snapshot observers", applied > 0)
            val afterPush = applied
            Snapshot.withMutableSnapshot { s.press(Routes.PROFILE) }
            assertTrue("selecting a tab notifies snapshot observers", applied > afterPush)
        } finally {
            handle.dispose()
        }
    }

    /** Process death must not drop an open thread and return the user to the Home root (review B13). */
    @Test
    fun stacksSurviveSaveAndRestore() {
        val s = stacks()
        s.push(Routes.THREAD, "at://home-thread")
        s.press(Routes.PROFILE)
        s.push(Routes.THREAD, "at://profile-thread")
        val saved = s.save()

        val restored = TabBackStacks(Routes.TABS)
        restored.restore(saved)
        assertEquals(Routes.PROFILE, restored.selected)
        assertEquals("at://profile-thread", restored.current.arg)
        assertEquals(listOf(Routes.PROFILE, Routes.THREAD), restored.stack(Routes.PROFILE).map { it.route })
        restored.press(Routes.HOME)
        assertEquals("the other tab's stack came back too", "at://home-thread", restored.current.arg)
        val ids = restored.allEntries().map { it.id }
        assertEquals("restored entries get fresh ids", ids.size, ids.toSet().size)
    }

    @Test
    fun restoringNothingLeavesTheTabRoots() {
        val s = stacks()
        s.restore(emptyList())
        assertEquals(Routes.HOME, s.selected)
        assertEquals(Routes.HOME, s.current.route)
        assertFalse(s.canGoBack())
    }

    @Test
    fun selectKeepsTheStack() {
        val s = stacks()
        s.push(Routes.THREAD, "a")
        s.select(Routes.PROFILE)
        s.select(Routes.HOME)
        assertEquals(Routes.THREAD, s.current.route)
    }
}

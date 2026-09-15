package io.nativestarter.showcase.bluesky.core.navigation

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

    @Test
    fun selectKeepsTheStack() {
        val s = stacks()
        s.push(Routes.THREAD, "a")
        s.select(Routes.PROFILE)
        s.select(Routes.HOME)
        assertEquals(Routes.THREAD, s.current.route)
    }
}

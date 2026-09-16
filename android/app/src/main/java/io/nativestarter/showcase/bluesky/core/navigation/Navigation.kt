package io.nativestarter.showcase.bluesky.core.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Route names. Tabs are the roots of their own back stacks (review A16). */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val MESSAGES = "messages"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
    const val THREAD = "thread"
    const val NOT_IN_SHOWCASE = "notInShowcase"

    val TABS = listOf(HOME, SEARCH, MESSAGES, NOTIFICATIONS, PROFILE)

    /** RN route names, as `router:navigate {from}` reports them (src/Navigation.tsx). */
    fun rnScreenName(route: String?): String? = when (route) {
        HOME -> "Home"
        SEARCH -> "Search"
        MESSAGES -> "Messages"
        NOTIFICATIONS -> "Notifications"
        PROFILE -> "MyProfile"
        THREAD -> "PostThread"
        NOT_IN_SHOWCASE -> "NotInShowcase"
        else -> null
    }

    /** RN `TAB_TO_NAV_ITEM` (src/lib/routes/tab-to-nav-item.ts). */
    fun navItem(tab: String): String = when (tab) {
        HOME -> "home"
        SEARCH -> "search"
        MESSAGES -> "chat"
        NOTIFICATIONS -> "notifications"
        else -> "profile"
    }
}

/** What feature screens may ask of navigation. The shell implements it; features never see the back stacks. */
interface AppNavigator {
    /** Pushes the thread on the selected tab's stack (RN `navigation.push('PostThread')`). */
    fun openThread(uri: String)

    fun openNotInShowcase(title: String)

    /** RN `navigation.navigate('<Tab>')`: selects the tab and keeps its stack. */
    fun selectTab(tab: String)

    fun back()
}

/** One screen on a back stack. [id] is unique for the life of the stacks and keys saved state and view models. */
data class RouteEntry(val id: Long, val route: String, val arg: String? = null)

/**
 * Per-tab back stacks, as the RN bottom tab navigator with a native stack in each tab (src/Navigation.tsx):
 * - each tab keeps its own stack; switching tabs never clears a stack;
 * - pressing the selected tab pops its stack to the root, or asks for a soft reset when it is already at the root
 *   (BottomBar `onPressTab`: `InsideAtRoot` emits a soft reset, `Inside` pops to top);
 * - back pops the selected stack; at a tab root it returns to the first tab (`backBehavior: 'firstRoute'`), and at
 *   the first tab's root it lets the system handle back.
 */
class TabBackStacks(private val tabs: List<String>) {
    private var nextId = 0L

    /**
     * Snapshot state, so the shell recomposes because it read the stacks, not because a version counter was
     * bumped next to a suppressed unused read (review B12).
     */
    private val stacks: Map<String, androidx.compose.runtime.snapshots.SnapshotStateList<RouteEntry>> =
        tabs.associateWith { androidx.compose.runtime.mutableStateListOf(RouteEntry(nextId++, it)) }

    var selected: String by androidx.compose.runtime.mutableStateOf(tabs.first())
        private set

    val current: RouteEntry get() = stacks.getValue(selected).last()

    fun stack(tab: String): List<RouteEntry> = stacks.getValue(tab).toList()

    /** Every entry on every stack, for pruning saved state. */
    fun allEntries(): List<RouteEntry> = stacks.values.flatten()

    sealed interface Press {
        data object Switched : Press
        data object SoftReset : Press
        data class PoppedToRoot(val removed: List<RouteEntry>) : Press
    }

    fun press(tab: String): Press {
        require(tab in stacks) { "unknown tab $tab" }
        if (tab != selected) {
            selected = tab
            return Press.Switched
        }
        val stack = stacks.getValue(tab)
        if (stack.size == 1) return Press.SoftReset
        val removed = stack.subList(1, stack.size).toList()
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        return Press.PoppedToRoot(removed)
    }

    fun select(tab: String) {
        require(tab in stacks) { "unknown tab $tab" }
        selected = tab
    }

    fun push(route: String, arg: String? = null): RouteEntry =
        RouteEntry(nextId++, route, arg).also { stacks.getValue(selected).add(it) }

    sealed interface Back {
        data class Popped(val removed: RouteEntry) : Back
        data object ToFirstTab : Back
        data object NotHandled : Back
    }

    fun back(): Back {
        val stack = stacks.getValue(selected)
        return when {
            stack.size > 1 -> Back.Popped(stack.removeAt(stack.lastIndex))
            selected != tabs.first() -> {
                selected = tabs.first()
                Back.ToFirstTab
            }
            else -> Back.NotHandled
        }
    }

    fun canGoBack(): Boolean = stacks.getValue(selected).size > 1 || selected != tabs.first()
}

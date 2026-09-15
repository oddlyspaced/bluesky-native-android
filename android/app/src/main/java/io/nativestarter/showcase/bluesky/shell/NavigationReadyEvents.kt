package io.nativestarter.showcase.bluesky.shell

import io.nativestarter.showcase.bluesky.core.analytics.Analytics

/**
 * RN `onNavigationReady` (src/Navigation.tsx): `router:navigate {}` then `init {initMs}`, once per mount of the
 * navigation tree. App.tsx renders the tree under `<Fragment key={currentAccount?.did}>`, so the tree mounts again
 * each time the account key changes: at launch (no account), after sign-in (the account DID) and after sign-out.
 *
 * The instance lives in the process container, so an Activity recreation (theme or font scale change) does not
 * count as a new mount.
 */
class NavigationReadyEvents(private val analytics: Analytics, private val initMs: () -> Long) {
    private var mounted = false
    private var accountKey: String? = null

    /** [did] is the signed-in account, or null while signed out or restoring (RN `currentAccount` undefined). */
    @Synchronized
    fun onAccountKey(did: String?) {
        if (mounted && did == accountKey) return
        mounted = true
        accountKey = did
        analytics.metric("router:navigate")
        analytics.metric("init", mapOf("initMs" to initMs()))
    }
}

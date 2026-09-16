# Architecture review 1: CP-00 to CP-03, Android

- Reviewer: `architecture-reviewer` agent (Nativestarter Migration Pack), run on 2026-09-16
- Code reviewed: `android/` over `cd2d6e5..1834e63` (CP-00 is `cd2d6e5`, the base of the range)
- Verdict: **No blockers found.** 17 major, 20 minor
- Finding ids use the `B` prefix, so they do not collide with the iOS `A` series
- Paths are relative to `android/app/src/main/java/io/nativestarter/showcase/bluesky/` unless they start with `android/`

## Major

| # | Area | Finding | Evidence | Fix |
|---|---|---|---|---|
| B1 | Session lifecycle | `AccountScope.close()` runs only when a different DID signs in. After sign-out the previous account's PostCache, shadows, postSources, preferences and ownProfile stay in memory, and its main-immediate scope stays live. Signing back in as the same DID reuses stale state | `AppContainer.kt:67-76,110`; `core/session/Session.kt:94-106`; `shell/AppShell.kt:125` | Close the account scope on sign-out; add a test |
| B2 | Concurrency | `refresh()` holds the mutex across the HTTP call, so `logout()` waits for the whole OkHttp timeout. Sign-out appears to do nothing | `core/session/Session.kt:94-100,123-145` | Set the signed-out state under the lock first; let the in-flight refresh drop its result on the generation change |
| B3 | Preferences state | A failed `getPreferences` or `pinnedFeeds` is handled with `onSuccess` only, so the user's hideReplies, hideReposts and hideQuotePosts are silently ignored for the session, and `homeReady` never becomes true. Neither refresh nor retry re-reads | `feature/feed/FeedViewModel.kt:100-115` | Retry the preference load on refresh and retry; do not treat a failure as loaded defaults |
| B4 | Preferences state | On a failed read, `getOrDefault(UNKNOWN_SORT)` pins `"top"` into `query.sort`, so retry never re-reads. Android twin of iOS A54 | `feature/thread/ThreadViewModel.kt:150-168` | Store the sort only on success; re-read inside retry |
| B5 | Main-thread performance | The `combine` transform runs on the main dispatcher, so every shadow emission re-tunes every loaded page or re-parses the whole thread. One like tap costs work proportional to the feed | `feature/feed/FeedViewModel.kt:71-88`; `feature/profile/ProfileViewModel.kt:95-116`; `feature/thread/ThreadViewModel.kt:101-138` | `flowOn(Dispatchers.Default)`, or apply shadows per row at render time |
| B6 | Images, concurrency | The shared `async` is a child of the first caller's scope, so cancelling that caller fails every other waiter | `core/image/ImageLoader.kt:85-105` | Own the async in a loader-level scope; add concurrency and cancellation tests |
| B7 | Network duplication | `preferences()` and `pinnedFeeds()` both GET `app.bsky.actor.getPreferences`; home issues two identical requests, a thread a third | `core/network/BskyRepository.kt:101-124`; `feature/feed/FeedViewModel.kt:101-113`; `feature/thread/ThreadViewModel.kt:154` | Fetch once, derive both, share through a cache |
| B8 | Duplication | About 50 lines of feed callbacks duplicated between feed and profile view models, already diverging on `feedDescriptor` | `feature/feed/FeedViewModel.kt:143-175`; `feature/profile/ProfileViewModel.kt:170-200` | Extract shared `FeedEvents` taking the descriptor and interaction context |
| B9 | Events consistency | The feed logs `post:view` with RN's 40% for 500 ms rule; the thread logs it on composition, including rows prepared off-screen | `feature/thread/ThreadScreen.kt:163`; `feature/feed/PostFeedList.kt:127-153` | Use `TrackPostViews` for the thread list too |
| B10 | Main-thread performance | `FontMetricsCache.get` parses a font file during composition for each weight and size pair, and a font-scale change invalidates the keys | `designsystem/text/RnLineBox.kt:131-143,167-178` | Pre-warm off the main thread; fall back to Compose metrics until ready |
| B11 | Design default | The build file records "no Material components are drawn", but the code draws `PullToRefreshDefaults.Indicator` and `CircularProgressIndicator`. The result may match RN Android, but the recorded reason contradicts the code | `android/app/build.gradle.kts:69-70`; `feature/feed/HomeScreen.kt:189-195`; `PostFeedList.kt:92`; `feature/profile/ProfileScreen.kt:165-169` | Correct the recorded reason and check iOS renders the same shape |
| B12 | Navigation correctness | `TabBackStacks` is not snapshot state; recomposition works only because of a `@Suppress("UNUSED_VARIABLE")` version read | `shell/AppShell.kt:202-226,236-240` | Hold stacks in snapshot state; drop the version counter |
| B13 | Navigation, state loss | Back stacks live only in a ViewModel, so process death returns the user to the Home root and loses open threads | `shell/AppShell.kt:202-204,237` | Save entries in `SavedStateHandle` |
| B14 | Tests | `unitTests.isReturnDefaultValues = true` makes unmocked `android.*` calls return defaults instead of failing, which weakens all 110 JVM tests | `android/app/build.gradle.kts:48-50` | Limit the flag, or use injectable seams; record which tests depend on it |
| B15 | Tests | The two preference-failure tests assert only the absence of an event, and one pins the stuck state of B3 as correct | `feature/Cp02ThreadTest.kt:368-372`; `EventRulesTest.kt:82-92` | Add retry-after-failure tests for both paths |
| B16 | Scale, navigation | Closed `Routes` object, hard-coded `TABS`, a `when` block and a second hard-coded name map: four coordinated edits per screen, and no URL-to-route parser | `core/navigation/Navigation.kt:4-35`; `shell/AppShell.kt:340-359` | Route-type keyed factory registry plus a URL parser |
| B17 | Scale, API surface | One 13-method repository interface that every screen widens, with a fake that must implement all of it | `core/network/BskyRepository.kt:15-45`; `test/.../testing/Fakes.kt:46-132` | Split by domain: feed, thread, actor, record |

## Minor

| # | Finding | Evidence |
|---|---|---|
| B18 | `authed()` checks the DID before the call but not after, so an in-flight call finishes against the old token | `core/session/Session.kt:112-121` |
| B19 | `allowBackup="false"` without `dataExtractionRules`, so device-to-device transfer is not excluded | `android/app/src/main/AndroidManifest.xml:11` |
| B20 | Release has no R8 and uses the debug signing key; recorded as a smoke configuration. The debug JSON Lines sink class ships but is never instantiated | `android/app/build.gradle.kts:30-35`; `android/README.md:99-104` |
| B21 | Two `OkHttpClient` instances, so two connection pools for one host | `core/network/XrpcClient.kt:35`; `core/image/AndroidImageLoader.kt:37` |
| B22 | `inFlight = false` is not in a `finally`, so cancellation freezes the unread badge | `shell/Unread.kt:50-61` |
| B23 | `postCache` defaults to a fresh instance, so a future caller silently loses thread placeholders | `feature/thread/ThreadViewModel.kt:63`; `feature/profile/ProfileViewModel.kt:66` |
| B24 | An infinite `state.collect` exists only to log the anchor view, keeping the combined flow hot | `feature/thread/ThreadViewModel.kt:141-143` |
| B25 | The `endReached` `derivedStateOf` block is duplicated verbatim | `feature/feed/HomeScreen.kt:161-170`; `feature/profile/ProfileScreen.kt:150-158` |
| B26 | The RN `FeedFooter` offset expression is written and documented twice | `feature/feed/HomeScreen.kt:249`; `feature/profile/ProfileScreen.kt:188` |
| B27 | `AvatarSize.feed = 42.dp` competes with `AVI_WIDTH` and two hard-coded `42.dp` | `designsystem/Tokens.kt:97-105`; `feature/thread/ThreadScreen.kt:86`; `designsystem/post/PostFeedItem.kt:122,140` |
| B28 | Two supported ways to read the theme (`Theme` getters and `LocalAlf.current`) with no rule | `designsystem/Tokens.kt:59-70` |
| B29 | 21 dead palette-step getters; `TabBar` reads raw steps instead of semantic atoms | `designsystem/Tokens.kt:20-56`; `designsystem/ui/TabBar.kt:65,123` |
| B30 | 68 raw dp literals in `feature/` and `shell/`, 118 in `designsystem/`; the named layout objects are the right pattern, but `PostFeedItem` and the profile header still carry unnamed literals | grep; `designsystem/post/PostFeedItem.kt:121-154`; `feature/profile/ProfileScreen.kt:300-315` |
| B31 | `rememberJsLineHeightExcess` is a real rule but applied at one call site; the profile pager header may need it | `designsystem/text/RnLineBox.kt:96-119`; `feature/feed/HomeScreen.kt:176` |
| B32 | `LocalView.current.rootView.height` is not snapshot-backed, so it misses rotation and multi-window changes | `feature/thread/ThreadScreen.kt:138` |
| B33 | Route factories take the whole `AccountScope`; acceptable at this size because screens take only state and callbacks | `feature/FeatureRoutes.kt:50,117,182` |
| B34 | No tests for the unread view model, the shell router and store eviction, `RemoteImage`, image-loader concurrency, corrupt-blob recovery, or profile load-more retry | test inventories |
| B35 | No normalised entity cache; shadows cover like and repost only, so each new mutation adds a parallel map | `core/network/PostInteractions.kt:42-47`; `core/network/PostCache.kt:7-19` |
| B36 | `FeedUiState` and `postFeedItems` are feed-shaped, so a notifications or search list gets nothing from them | `feature/feed/FeedViewModel.kt:24-43`; `feature/feed/PostFeedList.kt:40-99` |
| B37 | One commit per checkpoint (fixes iOS A24), with two small mixes: an iOS fixture deletion in CP-00, and a refactor combined with a font-scale fix | `git log --stat cd2d6e5..1834e63 -- android` |

## Checked and found correct

- **iOS A46 does not reproduce.** One mutex serialises login, refresh and logout writes; refresh re-reads and compares DID and access token inside it. `signOutDuringRefreshStaysSignedOut` covers the race with a gated refresh and asserts the revoke uses the newest token. B2 is the cost of that design, not the same bug.
- **Single-flight refresh** with token-equality reuse; `concurrentExpiredTokensShareOneRefresh` proves the single-use token is spent once under four callers.
- **Failed refresh** signs out only on a terminal auth failure; both paths tested.
- **Credential storage:** AES-256-GCM under an Android Keystore key, ciphertext in DataStore, off the main thread, deleting only on decrypt or decode failure. The instrumented test asserts the token is absent from the file bytes.
- **Debug and release hygiene:** the JSON Lines sink, the `e2eSignIn*` controls and cleartext networking are all excluded from Release. iOS A25, A52 and A60 do not recur.
- **Layering:** screens depend on interfaces only; no platform API in a screen except B32; `collectAsStateWithLifecycle` everywhere.
- **`RnLineBox` and `RnFontScale` are rules, not per-screen constants.** iOS A19 and A57 do not recur.
- **Optimistic like and repost:** coalescing queue, last confirmed server state, failed compensation surfaced, shadows yield to refetched data. Seven tests.
- **Pagination and refresh generations:** one job, generation guard, truncation to page 1 as RN does, bounded auto-pagination. Four tests.
- **Test quality:** about 100 of 110 JVM tests plus all 10 connected tests sampled across 14 classes. Most prove behaviour; the weak ones are B15 and the gaps B34. No sleep-timed assertions.

## Not checked

- Visual and behavioural parity, including the open error-home difference: the parity reviewer's job.
- Any runtime measurement (build, tests, emulator, Maestro, profiling): the devices and the Gradle lock were held by another agent. B5 and B10 are reasoned from the dispatcher and call site, not measured.
- iOS: out of scope for this round.

## Suggested fix order

B3 and B4 first (a user's filters and thread sort are silently dropped after one transient failure), then B1 and B6 (lifecycle defects with user-visible effects), then B5 and B10 (performance ceiling as the feed grows), then B12, B13 and B16 (the seams that break first at 74 more screens).

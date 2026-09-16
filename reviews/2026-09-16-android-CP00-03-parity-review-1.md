# Parity review 1: CP-00 to CP-03, Android

- Reviewer: `parity-reviewer` agent (Nativestarter Migration Pack), run on 2026-09-16
- Code reviewed: `android/` over `cd2d6e5..1834e63`, against bluesky-social/social-app at `2bfb94d`
- Verdict: **Blockers found: 5** (A1, A2, A3, A4, A5)
- Finding ids use the `A` prefix within this Android parity series. They are separate from the iOS `A` (architecture) and `N`/`F` (parity) series.
- Paths are relative to `android/app/src/main/java/io/nativestarter/showcase/bluesky/` unless they start with `android/`

## Blockers

| # | Area | Finding | Native evidence | RN reference | Fix |
|---|---|---|---|---|---|
| A1 | Events | `LifecycleResumeEffect(Unit)` logs `home:feedDisplayed {reason:"focus"}` on every `ON_RESUME`, so returning from the device Home key logs an event RN never sends | `feature/FeatureRoutes.kt:62-68` | `src/view/screens/Home.tsx:176-186` uses `useFocusEffect`, which fires on navigation focus only | Fire on navigation focus of the Home back-stack entry, not on lifecycle resume |
| A2 | Evidence | Every headline number in PARITY.md is unreproducible. The raw captures were deleted; the only surviving native event log predates `1bb20f6`, `1862ea3`, `5368546` and `b4054fb`, and the noise-floor results are from 15 Sep. Re-running the report's own command gives `MISMATCH (ordered): rn 16, native 14`, not the reported MATCH | `android/PARITY.md:10-16,79-101,106-116,150-153`; `android/parity/out/interactions/native-events.jsonl` (15 Sep 18:29); `android/parity/out/noise/masked/results.json` (`createdAt 2026-09-15T13:21:22Z`) | n/a | Re-capture every state and the event log at the final commit, and commit the diff `results.json` and the native event log (not the PNGs), so the numbers can be checked without a device |
| A3 | Masks | The `error`/`home` band list is byte-identical to `base`/`home`, including the info-glyph band. On the error screen that band lands on the ink of "Yo" in "You've reached the end of your feed!", the exact text that differs from native | `android/parity/masks-spec.json:203-209` | `migration-pack/skills/check-parity/references/masking.md` | Declare per-state bands for `error`, re-check `empty`, and make `glyphs` fail rather than mask a cluster that is not icon-shaped |
| A4 | Errors, recorded differences | PARITY.md calls the error-home result "a real behaviour difference, not a capture artefact". The review traced RN: had the soft reset run, `truncateAndInvalidate` would leave one page, `hasNextPage` would be true and the end-of-feed block would not render. The RN capture shows the end-of-feed block, which proves the gesture never reached `FeedPage.onSoftReset`. Judgement: test-harness artefact | `android/PARITY.md:10,95,118-131,191`; `android/parity/flows/pack/error.yml:36-38` | `src/view/shell/bottom-bar/BottomBar.tsx:97-107`; `src/view/com/feeds/FeedPage.tsx:99-113`; `src/state/queries/util.ts:151-165`; `src/view/com/posts/PostFeed.tsx:950-951` | Drive RN's refresh through a path the driver can reach, prove it ran (assert `feed:refresh {reason:"soft-reset"}` in the RN log, or use `LoadLatestBtn`), then re-measure. Do not send difference row 2 to the owner as written |
| A5 | Recorded differences | 7 drift screens, 7 difference rows and 3 accepted-design rows all read "awaiting owner acceptance", and 6 proposed out-of-scope rows likewise. No name or date anywhere | `android/PARITY.md:5,186-205`; `plan/slice-plan.md:37-46` | Gate: "Parity report passes, or every drift is accepted in writing" | Owner sign-off, not a code change |

## Major

| # | Area | Finding | Native evidence | RN reference |
|---|---|---|---|---|
| A6 | Events evidence | The RN event list is hand-written from source, so order and props were never observed in the running app. Same as iOS F8 | `android/PARITY.md:146`; `android/parity/events/rn-expected-interactions.jsonl` | n/a |
| A7 | testIDs | `HomeScreen` and `contentHider-post` are not exposed. Android does expose `homeScreen`, `followingFeedPage`, `homeScreenFeedTabs`, `profileScreen`, `profileView` and `profilePager`, so the iOS F2 gaps are already fixed here | `feature/feed/HomeScreen.kt:178,188,230` | `src/view/screens/Home.tsx:95`; `src/view/com/posts/PostFeedItem.tsx:513` |
| A8 | Facets | The profile bio renders with plain text, although facets are in scope for every screen | `feature/profile/ProfileScreen.kt:290-292`; `android/PARITY.md:197` | `src/screens/Profile/Header/ProfileHeaderStandard.tsx:145-156` |
| A9 | Noise evidence | The noise floor is measured on `base` only and then used to justify drift on five other states. RN-to-RN variance between the two committed runs is not uniform: error home 0.14%, interactions thread 0.54%, empty home 0.04% | `android/PARITY.md:106-116` | n/a |
| A10 | Masks | `rect` bands return the declared rectangle and never read either image, so a layout shift could hide a real difference silently. Same gap as iOS F5 | `android/parity/make_masks.py:85-87` | `masking.md` |
| A11 | States | Loading, load-more error and profile error have no capture in either app | `android/PARITY.md:184` | `plan/slice-plan.md:27-28` |
| A12 | Themes | `ThemeName.Dark` is unreachable in the slice (system dark maps to Dim), so only light and dim are captured, although the spec asks for light, dark and dim | `designsystem/Theme.kt:93,134,138-141`; `android/PARITY.md:180` | `plan/slice-plan.md:24` |
| A13 | Events | `thread:preferences:load` is gated on whole-`UserPreferences` equality and preferences are read once per fetch, so an unrelated preference change logs where RN would not, and a late `threadViewPrefs` change does not log where RN would | `feature/thread/ThreadViewModel.kt:98,152-156,177-184` | `src/state/queries/preferences/useThreadPreferences.ts:50-52,65-70` |

## Minor

| # | Finding | Evidence |
|---|---|---|
| A14 | Action-row masks bound the right-hand slot by a 12 px width threshold, so a count that moved past x 900 would be masked silently, although today the counts stay visible as PARITY.md claims | `android/parity/masks-spec.json`; `android/PARITY.md:76` |
| A15 | Font-scale caps are ported at two sites; RN caps at four in-scope sites, including the avatar | `designsystem/Components.kt:244`; `shell/AppShell.kt:447`; `src/view/com/util/UserAvatar.tsx:292` |
| A16 | During a refresh the pager sets `cursor = firstPageCursor` where RN keeps the param used to fetch page 1. No user-visible difference today | `feature/feed/FeedPager.kt:64-78`; `src/state/queries/util.ts:151-165` |

## Verified equal to RN, no finding

- Feed tuner set, order and each tuner body; the author-feed tuner is empty for `posts_and_author_threads`.
- Time-ago, including the 5 s "now" floor, floor rounding, 30-day months and the `M/d/yyyy` fallback.
- Ordered `post:view`: 40% visible for 500 ms, index order, slice position.
- Post-sign-in remount events.
- Refresh truncation, apart from the interim cursor in A16.
- `feed:refresh` reasons on both screens, including RN's profile soft reset logging nothing.
- `nav:click {item, surface: "bottomBar"}` and the load-more retry copy.

## Pairing method

`run-pack.sh` captures one app per invocation under the seed lock and reseeds per flow, so "same run" means the same batch under one seed lock, with each state seeded twice, once per app. That is the strongest pairing available with one device each, the flows wait on the same settling condition in both apps, and PARITY.md describes it fairly.

## Could not reproduce, and why

- All 15 raw and masked ratios: the native captures were deleted and are not committed.
- The noise floor: same reason; the surviving results predate the reviewed rounds.
- The ordered event MATCH: the current native event log is gone, and the one on disk is stale.
- The mask areas: they are merged RN and native measurements and need the native images.
- The tests and Release build: taken from the lead's independent verification (110 JVM, 10 connected, Release succeeds).

## Commands the reviewer ran

- `make_masks.py` on RN baselines only for base, error and empty: areas below the committed figures, in the expected direction. This produced A3.
- Rendered every computed mask over the RN baselines and inspected them: A3 and A14.
- `events.mjs --ordered` on the surviving native log: MISMATCH 16 vs 14 (stale log): A2.
- `magick compare -metric AE` between the two committed RN runs of the same state: A9.
- `git log --oneline cd2d6e5..1834e63 -- android`: 23 commits, all read.
- No Gradle, adb, emulator, Maestro, xcodebuild or server command was run, and no repository file was modified.

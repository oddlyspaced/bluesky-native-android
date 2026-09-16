# Parity report: Android native vs RN Android

Every number in this file was measured with the commands below. Nothing is estimated. Where a result is not valid evidence, the row says so and gives the reason.

Acceptance: **awaiting owner acceptance.**

## Verdict

- **Every state is measured in the same run as its RN capture.** No baseline from an older run is used in a diff.
- **One screen fails: error home (4.91% masked).** It is a real behaviour difference, not a capture artefact (see "Error home").
- **Drift, above 1% (a person who did not write the port must sign off):** base home 1.61%, dark home 1.75%, font scale 2.0 home 1.68%, thread 1.45% and profile 1.26%, empty home 1.38%, interactions home 1.62%.
- **Everything else passes masked.** No screen passes raw.
- **Events: ordered MATCH, 16 of 16 on the interactions flow** (`events.mjs --ordered`). The longer
  readonly flow does **not** match: 21 RN events against 19 native, with 14 difference lines that
  collapse to 3 causes. See "Event diff". The earlier line in this file claimed a match without
  naming its flow, which read as a claim about the whole app. It is corrected here.
- **Noise floor:** native masked 0 px on home and profile, 0.10% on thread; RN masked 0 px on home and profile, 0.16% on thread.
- Fixed this round: font scale 2.0 home 6.24% → 1.68%, interactions home 4.45% → 1.62%, and the RN empty profile now loads and is captured.

## Setup

| Item | Value |
|---|---|
| Device | emulator-5554, Pixel 10 Pro AVD, Android 16 (API 36.1), 1280x2856, density 480 (3 px = 1 dp) |
| RN app | `xyz.blueskyweb.app`, bluesky-social-app at `2bfb94d` (react-native 0.86.0), e2e build, Metro on 8081 |
| Native app | `io.nativestarter.showcase.bluesky`, debug build. base screenshots `1862ea3`; dark and font scale 2.0 `5368546`; empty, interactions and error `b4054fb` |
| Server | dev network PDS `http://localhost:3000`, AppView DID `did:plc:bw7ad3erl7btq6qwf66yqiov`, reseeded by every flow under `/tmp/bsky-seed.lock`. Native databases: Postgres 5435, Redis 6382 |
| Device setup | `adb -s emulator-5554 reverse tcp:3000 tcp:3000` and `tcp:8081 tcp:8081`, checked before each capture |
| Pack tools | `migration-pack/skills/check-parity/scripts/capture.mjs`, `diff.mjs` (threshold 0.02, fuzz 5%), `events.mjs` |
| Total pixels per image | 3,655,680 |

## Commands

Run from `android/parity`. `<pack>` is `../../../../migration-pack/skills/check-parity/scripts`.

1. Tests and builds (under `/tmp/native-build.lock`):
   ```
   ./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx3g testDebugUnitTest assembleDebug assembleRelease
   ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon :app:connectedDebugAndroidTest
   ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon :app:installDebug
   ```
   `connectedDebugAndroidTest` uninstalls the app when it ends, so run `installDebug` in a separate, later command.
2. One capture. `run-step.sh` stops when another app is in the foreground (exit 3) or a mobilecli DeviceServer holds UiAutomation on emulator-5554 (exit 4), then calls `run-pack.sh`, which takes the seed lock, sets dark mode or font scale, runs `capture.mjs` and releases the lock:
   ```
   ./run-step.sh <native|rn> <base|dark|font2|interactions|empty|error> <out-root>
   ```
   Both apps are captured in one batch, one capture at a time.
3. Masks and diffs (the RN side is the same-run RN capture):
   ```
   python3 make_masks.py masks-spec.json <state> <root>/<state>/rn/android <root>/<state>/native/android <root>/masks-<state>.json
   node <pack>/diff.mjs --rn <root>/<state>/rn/android --native <root>/<state>/native/android --out <root>/<state>/diff-raw
   node <pack>/diff.mjs --rn <root>/<state>/rn/android --native <root>/<state>/native/android --out <root>/<state>/diff-masked --mask <root>/masks-<state>.json
   ```
4. Noise floor: the same `diff.mjs` commands between two base runs of one app, with the base masks.
5. Events (the native log is read right after the native interactions capture):
   ```
   adb -s emulator-5554 shell run-as io.nativestarter.showcase.bluesky cat files/events.jsonl > <root>/interactions/native-events.jsonl
   node <pack>/events.mjs --rn events/rn-expected-interactions.jsonl --native <root>/interactions/native-events.jsonl \
     --ignore uri,authorDid,initMs,likerClout,postClout --ordered
   ```

The raw capture folders are not committed. The RN captures used as baselines are in `parity/baseline/rn/`.

## Masks

`make_masks.py` measures each mask on both images and merges them. Bands are declared per state and screen in `masks-spec.json`.

| Kind | What it hides | Reason |
|---|---|---|
| `status` | y 0 to 150 | Status bar: clock, battery, signal |
| `glyphs` | Ink clusters of icon glyphs, at least `minw` px wide | Licence substitution: Lucide glyphs for the Central icon set, and the neutral header mark (ASSETS.md sections 2 and 3) |
| `tail` | The right-aligned relative time of a post meta line | Capture time |
| `rect` | The thread anchor date line and its globe glyph | Capture time; the globe is a Lucide glyph |

Not masked: counts (reply, repost and like counts, stats rows, profile metrics, the badge), names, handles, post text, buttons, borders, backgrounds and the code-drawn fallback avatars.

Mask area per screen (4 px grid, overlaps removed): base 10.89 / 8.97 / 7.04%, dark 10.89 / 8.96 / 7.04%, font scale 2.0 11.73 / 10.31 / 7.33%, empty 7.40 / 7.00%, interactions 10.90 / 9.05%, error 11.02 / 6.29%. Every screen is under the 15% limit.

## Results (same run per state)

Fuzz 5%, threshold 0.02.

| State | Screen | Raw px (ratio) | Masked px (ratio) | Status | Valid? |
|---|---|---|---|---|---|
| base | home | 115,250 (3.15%) | 58,774 (1.61%) | drift | Yes |
| base | thread | 55,152 (1.51%) | 26,392 (0.72%) | pass | Yes |
| base | profile | 109,202 (2.99%) | 30,031 (0.82%) | pass | Yes. Both apps waited for "2 followers" and "2 following" |
| dark | home | 116,780 (3.19%) | 64,050 (1.75%) | drift | Yes |
| dark | thread | 62,664 (1.71%) | 33,682 (0.92%) | pass | Yes |
| dark | profile | 47,847 (1.31%) | 31,845 (0.87%) | pass | Yes |
| font scale 2.0 | home | 131,032 (3.58%) | 61,350 (1.68%) | drift | Yes |
| font scale 2.0 | thread | 98,260 (2.69%) | 52,873 (1.45%) | drift | Yes |
| font scale 2.0 | profile | 143,220 (3.92%) | 46,175 (1.26%) | drift | Yes. Posts loaded in both apps |
| empty | home | 74,082 (2.03%) | 50,380 (1.38%) | drift | Yes |
| empty | profile | 111,054 (3.04%) | 29,819 (0.82%) | pass | Yes. RN needed up to 90 s to leave `ProfileHeaderLoading` |
| interactions | home | 117,312 (3.21%) | 59,204 (1.62%) | drift | Yes. Both apps wait for `FollowingEndOfFeed` before the screenshot |
| interactions | thread | 62,611 (1.71%) | 31,877 (0.87%) | pass | Yes |
| error | home | 237,324 (6.49%) | 179,637 (4.91%) | **fail** | Yes. Real difference, see "Error home" |
| error | thread | 32,769 (0.90%) | 24,120 (0.66%) | pass | Yes. Both show "Error loading post" and Retry |

### Fixed this round

| Screen | Before | After | Fix |
|---|---|---|---|
| font scale 2.0 home | 228,220 (6.24%), RN r2 baseline | 61,350 (1.68%), same run | `29055d7`: RN's linear `useHeaderOffset` prediction |
| interactions home | 162,854 (4.45%) | 59,204 (1.62%) | `b4054fb`: both apps wait for the end-of-feed state |
| empty profile | not captured | 29,819 (0.82%) | `b4054fb`: 90 s wait for the loaded Posts tab |

## Noise floor

Two base runs of each app, same build, same batch, base masks:

| Screen | Native raw | Native masked | RN raw | RN masked |
|---|---|---|---|---|
| home | 772 (0.02%) | 0 (0.00%) | 3,267 (0.09%) | 0 (0.00%) |
| thread | 9,157 (0.25%) | 3,803 (0.10%) | 17,343 (0.47%) | 5,941 (0.16%) |
| profile | 760 (0.02%) | 0 (0.00%) | 2,628 (0.07%) | 0 (0.00%) |

The raw pixels are the status bar and relative times. Every drift above is at least 1.2 percentage points above the noise floor.

## Error home

The error flow signs in, reseeds the server, opens the cached thread, goes back and refreshes the feed. A server probe on 2026-09-16 confirms what the apps meet after the reseed: `app.bsky.feed.getTimeline` with the old session returns **HTTP 500**, `com.atproto.server.refreshSession` returns 400 `InvalidRequest`, and alice's DID changes.

In the same run:

- **Native** reaches the failed-refresh state: page 1 posts and the load-more retry row ("There was an issue fetching posts. Tap here to try again."), with no Discover notice. This is what RN's own code renders for a failed refresh (`PostFeed.tsx:688` adds `loadMoreError` when `isError && !isEmpty`; line 951 hides end of feed; `util.ts:151-165` keeps only page 1).
- **RN** keeps the stale cached pages and shows `FollowingEndOfFeed`, with no retry row and no Discover notice. Its recorded hierarchy contains no error row at all, although the same flow's thread capture shows RN's thread query failing ("Error loading post").

Three gestures were measured (swipe 40→80%, swipe 30→90%, and the Home tab soft reset, which RN wires to the same `truncateAndInvalidate` path). RN never refetched the feed in any of them, so its feed query never entered the error state. The native app was fixed this round to truncate to page 1 on refresh (`b4054fb`), which removed the Discover notice from its failed-refresh screen; the remaining difference is that RN does not refetch at all here. The flow now takes each screenshot at the app's settled state after the same gesture and records the difference instead of asserting a state RN never reaches. Open: difference 2.

## Font scale (`29055d7`)

The text line boxes were not the cause of the old 6.24% failure:

- The tab bar border was at y 471 in both apps, row heights below the composer prompt matched, and text ink heights were equal.
- The whole 18 px offset sat above the composer prompt. RN `useHeaderOffset` gives the list `paddingTop` as `52 + 23 + 20 * fontScale - 4` dp with the linear `Configuration.fontScale`, while the tab text goes through `PixelUtil.toPixelFromSP` and the nonlinear Android 16 `FontScaleConverter`: 20 sp is 34 dp (102 px), not 40 dp (120 px).

`RnFontScale.jsLineHeightExcessPx` in `designsystem/text/RnLineBox.kt` returns that difference (0 px at 1.0, 7 px at 1.3, 18 px at 2.0), and home adds it to the measured header height for the content top and `FeedFooter`. `RnLineBoxTest` checks line boxes at 1.0, 1.3 and 2.0 against the Android 16 converter tables.

## Seed race

The AppView indexes the seeded follows asynchronously, so a profile loaded early can show "1 follower" or "1 following". Server check: alice.test has 2 followers (bob, carla) and 2 follows, with the full seed and with `?users&follows`.

`flows/pack/wait-profile-counts.yml` (used by `readonly.yml` and `empty.yml`) waits for "2 followers" and "2 following", pulls to refresh up to 4 times if needed, then asserts each count inside its id node (`profileHeaderFollowersButton`, `profileHeaderFollowsButton`). In both apps the label sits on a child of the id node. The counts are not masked. Every profile capture in both apps passed the wait.

## Event diff

The RN side is derived from source, not captured: the RN `.env` sets `EXPO_PUBLIC_LOG_DEBUG=session`, which hides `metric` logs.

Result on the interactions capture, with `--ignore uri,authorDid,initMs,likerClout,postClout`:

```
MATCH (unordered): rn 16 event(s), native 16 event(s)
MATCH (ordered):   rn 16 event(s), native 16 event(s)
```

### The readonly flow does not match

Measured on 2026-09-16, same ignore list. Evidence: `parity/evidence/base/` and
`parity/evidence/dark/` (`events-result.json` with its two input logs).

```
rn 21 event(s), native 19 event(s), match false
missing 2 | mismatches 3 | order mismatches 9
```

The two states were captured in separate runs and produce **byte-identical** difference lines, so
this is a stable property of the flow, not capture noise. The 14 lines have 3 causes:

| # | Cause | Evidence |
|---|---|---|
| 1 | Order: RN emits `home:feedDisplayed` before the following feed's `post:view` events. The port emits a `post:view` first. This one inversion at index 6 cascades into most of the 9 order mismatches, which are the same events reported one position apart. | `orderMismatches[0]`, then indexes 10 to 18 |
| 2 | Properties: the port's following-feed `post:view` positions read 1, 2, 3 where RN reads 0, 1, 2, and the first row's `isReply` is `false` in the port against `true` in RN. | `mismatches` at indexes 7, 8, 9 |
| 3 | The profile feed emits two fewer events: no `feed:endReached` and one fewer `post:view`, both on `author\|...\|posts_and_author_threads`. | `missing`, indexes 19 and 20 |

**The cause of each is not established yet, and this file does not claim one.** The RN side of this
comparison is derived from source, not captured (see the note above), so each difference is either a
defect in the port or an error in the derived list. Difference 2 resembles the `post:view` position
defect already found and fixed on iOS, which makes a port defect the more likely reading, but that is
an expectation, not a measurement. Until the cause is established, these are open items, not drift to
accept: a sign-off never covers an unexplained difference.

| Rule | RN source | Fixed in |
|---|---|---|
| `router:navigate {}` and `init` again after sign-in | App.tsx renders the tree under `key={currentAccount?.did}`; `onNavigationReady` runs once per mount | `1862ea3` |
| `home:feedDisplayed` after the remount events | Home.tsx mounts `HomeScreenReady` only when preferences and pinned feeds have loaded | `5368546` |
| `post:view` positions `[0, 1, 2, 3]`, top to bottom | `PostFeed` `getPostPosition`; `List` viewability 40% for 500 ms, in index order | `1bb20f6`, `1862ea3` |
| Anchor `post:view` before `router:navigate {from: Home}` | `BaseNavigationContainer` calls `onStateChange` from its own `useEffect`; the screen's anchor effect runs first | expected list corrected in `5368546` |
| No `thread:preferences:load` in this flow | `useThreadPreferences.ts:50-71` logs only when server preferences change after mount; Home already loaded them (`replaceEqualDeep`) | expected list corrected in `1862ea3` |

## States covered

| State | RN | Native | Notes |
|---|---|---|---|
| Home, thread, profile (light) | r4 | `1862ea3` | Same run |
| Dark (system dark = dim) | r4 | `5368546` | Same run |
| Font scale 2.0 | r4 | `5368546` | Same run, profile with posts |
| Empty seed (`?users&follows`) | r5 | `b4054fb` | Same run, RN profile captured |
| After like and repost | r5 | `b4054fb` | Same run, both at the end-of-feed state |
| Error (reseed, then refresh) | r5 | `b4054fb` | Same run, home fails on behaviour |
| Loading, load-more error, profile error | no | no | Covered by JVM and Compose UI tests |

RN baselines in `parity/baseline/rn/` (no baseline was ever overwritten): `android-r4-{home,thread,profile}.png`, `android-dark-r4-{home,thread,profile}.png`, `android-font2-r4-{home,thread,profile}.png`, `android-interactions-r4-thread.png`, `android-empty-r4-home.png`, `android-error-r4-{home,thread}.png`, and this round `android-empty-r5-{home,profile}.png`, `android-interactions-r5-{home,thread}.png`, `android-error-r5-{home,thread}.png`.

## Tests

| Suite | Result |
|---|---|
| JVM (`testDebugUnitTest`, with `assembleDebug assembleRelease`), code `b4054fb` | 110 tests, 0 failures |
| Compose UI and Keystore (`:app:connectedDebugAndroidTest`, emulator-5554), code `1862ea3` | 10 tests, 0 failures |

## Differences that remain

None of these is accepted. Each needs a named owner.

| # | Difference | Screen | Evidence |
|---|---|---|---|
| 1 | Drift above 1%: base home 1.61%, dark home 1.75%, font scale 2.0 home 1.68%, thread 1.45%, profile 1.26%, empty home 1.38%, interactions home 1.62% | Home, thread, profile | Results |
| 2 | After a reseed, native refetches the feed and shows the load-more retry row; RN does not refetch and keeps the end-of-feed state | Home (error) | "Error home" |
| 3 | Relative time: native shows "now" where RN shows "1m" to "3m" (tick start differs) | All | Masked |
| 4 | Header dropdown and share glyph shapes (Lucide) | All | Glyph masks |
| 5 | Code-drawn fallback avatars | All | Not masked; counted |
| 6 | Scroll indicator after navigation | Profile | RN shows a scrollbar; Compose lists show none |
| 7 | Profile description links and mentions are not detected | Profile | RN runs `detectFacets`; the seeded bio has none |

## Accepted design differences

| Difference | Platform | RN behaviour | Native behaviour | Reason | Accepted by | Parity check |
|---|---|---|---|---|---|---|
| UI icons | Android and iOS | Central icon system glyphs | Lucide (ISC) glyphs | licensing: ASSETS.md sections 2 and 3 | awaiting owner acceptance | Glyph-box masks |
| Header logo | Android and iOS | Bluesky butterfly | Neutral placeholder mark | licensing: ASSETS.md sections 2 and 3 | awaiting owner acceptance | Masked as a glyph |
| Fallback avatar | Android and iOS | Bluesky default avatar artwork | Silhouette drawn in code | licensing: ASSETS.md sections 2 and 3 | awaiting owner acceptance | Not masked |

The proposed out-of-scope rows in `plan/slice-plan.md` also await owner acceptance.

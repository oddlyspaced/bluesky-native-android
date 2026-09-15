# Parity report: Android native vs RN Android

Every number in this file was measured with the commands below. Nothing is estimated. Where a result is not valid evidence, the row says so and gives the reason.

Acceptance: **awaiting owner acceptance.**

## Verdict

- **Base, same run (r4):** home drift 1.61% masked, thread pass 0.72%, profile pass 0.82%. Home needs sign-off by a person who did not write the port. No screen passes raw.
- **Native noise floor (two runs of one build):** masked 0 px on home and profile, 3,803 px (0.10%) on thread. The RN noise floor is not measured: the second RN base run did not start (see "Not measured").
- **Events (interactions flow):** unordered MATCH, 16 of 16. Ordered: 1 difference left, fixed in code (`5368546`) after the capture and not re-captured.
- **Interactions home fails (4.45% masked), but the row is not valid evidence:** the two screenshots show different moments of the feed load. Interactions thread passes (0.90%).
- **Not measured on the final code:** dark, font scale 2.0, empty, error, the RN noise floor, and a capture with `5368546`. The dev network AppView began to return HTTP 502 during the batch. The rows from earlier builds are kept below and marked as stale.

## Setup

| Item | Value |
|---|---|
| Device | emulator-5554, Pixel 10 Pro AVD, Android 16 (API 36.1), 1280x2856, density 480 (3 px = 1 dp) |
| RN app | `xyz.blueskyweb.app`, bluesky-social-app at `2bfb94d` (react-native 0.86.0), e2e build, Metro on 8081 |
| Native app | `io.nativestarter.showcase.bluesky`, debug build installed from `1862ea3` for the r4 captures. `5368546` (home event gate) is built and tested but not installed or captured |
| Server | dev network PDS `http://localhost:3000`, AppView DID `did:plc:bw7ad3erl7btq6qwf66yqiov`, reseeded by every flow under `/tmp/bsky-seed.lock` |
| Device setup | `adb -s emulator-5554 reverse tcp:3000 tcp:3000` and `tcp:8081 tcp:8081` before each capture |
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
2. One capture. `run-step.sh` stops when another app is in the foreground (exit 3) or a mobilecli DeviceServer is on emulator-5554 (exit 4), then calls `run-pack.sh`, which takes the seed lock, sets dark mode or font scale, and runs `capture.mjs`:
   ```
   ./run-step.sh <native|rn> <base|dark|font2|interactions|empty|error> r4
   ```
   Both apps are captured in the same batch, so the RN side of each diff is the same-run RN capture.
3. Masks and diffs:
   ```
   python3 make_masks.py masks-spec.json <state> r4/<state>/rn/android r4/<state>/native/android r4/masks-<state>.json
   node <pack>/diff.mjs --rn r4/<state>/rn/android --native r4/<state>/native/android --out r4/<state>/diff-raw
   node <pack>/diff.mjs --rn r4/<state>/rn/android --native r4/<state>/native/android --out r4/<state>/diff-masked --mask r4/masks-<state>.json
   ```
4. Noise floor: the same `diff.mjs` commands with `--rn r4/base/native/android --native r4b/base/native/android` and `--mask r4/masks-base.json`.
5. Events (the native log is read right after the native interactions capture):
   ```
   adb -s emulator-5554 shell run-as io.nativestarter.showcase.bluesky cat files/events.jsonl > r4/interactions/native-events.jsonl
   node <pack>/events.mjs --rn events/rn-expected-interactions.jsonl --native r4/interactions/native-events.jsonl \
     --ignore uri,authorDid,initMs,likerClout,postClout [--ordered]
   ```

## Masks

`make_masks.py` measures each mask on both images and merges them. Bands are declared per state and screen in `masks-spec.json`.

| Kind | What it hides | Reason |
|---|---|---|
| `status` | y 0 to 150 | Status bar: clock, battery, signal |
| `glyphs` | Ink clusters of icon glyphs, at least `minw` px wide | Licence substitution: Lucide glyphs for the Central icon set, and the neutral header mark (ASSETS.md sections 2 and 3) |
| `tail` | The right-aligned relative time of a post meta line | Capture time |
| `rect` | The thread anchor date line and its globe glyph | Capture time; the globe is a Lucide glyph |

Not masked: counts (reply, repost and like counts, stats rows, profile metrics, the badge), names, handles, post text, buttons, borders, backgrounds and the code-drawn fallback avatars.

Mask area, r4 (4 px grid, overlaps removed): base home 10.89%, thread 8.97%, profile 7.04%; interactions home 10.90%, thread 9.05%. All are under the 15% limit.

## Results (r4, same run, native build `1862ea3`)

Fuzz 5%, threshold 0.02.

| State | Screen | Raw px (ratio) | Masked px (ratio) | Status | Valid? |
|---|---|---|---|---|---|
| base | home | 115,250 (3.15%) | 58,774 (1.61%) | drift | Yes |
| base | thread | 55,152 (1.51%) | 26,392 (0.72%) | pass | Yes |
| base | profile | 109,202 (2.99%) | 30,031 (0.82%) | pass | Yes. Both apps waited for "2 followers" and "2 following" |
| interactions | home | 221,505 (6.06%) | 162,854 (4.45%) | **fail** | **No.** Native shows the Discover notice and the end-of-feed block below the five posts; RN shows neither yet. The posts, the like and repost counts and the colours match |
| interactions | thread | 65,131 (1.78%) | 32,906 (0.90%) | pass | Yes |

### Stale rows (earlier builds, not re-captured)

These rows are from native build `41c1923` against RN r2/r3 baselines. They do not include `29055d7` (font scale) or the event changes.

| State | Screen | Masked (ratio) | Status | Note |
|---|---|---|---|---|
| dark | home / thread / profile | 1.75% / 0.94% / 1.01% | drift / pass / drift | |
| font scale 2.0 | home | 6.24% | fail | Cause found and fixed in `29055d7` (see "Font scale") |
| font scale 2.0 | thread | 1.45% | drift | |
| font scale 2.0 | profile | 2.17% | fail | The RN r2 baseline shows no posts and "1 following". It needs a new RN baseline |
| empty | home / profile | 1.38% / 0.98% | drift / pass | |
| error | home | 5.34% | fail | Not valid: the RN r2 baseline shows a different moment of the error flow |
| error | thread | 0.66% | pass | |

## Noise floor

Native base captured twice in one batch, same build, same seed flow (`r4` at 00:44, `r4b` later in the batch):

| Screen | Raw | Masked |
|---|---|---|
| home | 772 (0.02%) | 0 (0.00%) |
| thread | 9,157 (0.25%) | 3,803 (0.10%) |
| profile | 760 (0.02%) | 0 (0.00%) |

The raw pixels are the status bar and relative times. The thread residual is inside the anchor area. RN noise floor: not measured (RN base r4b did not reach "Sign in").

## Font scale (`29055d7`, not re-captured)

Measurement of the `41c1923` font scale 2.0 images shows that the text line boxes are not the cause of the home failure:

- The tab bar border is at y 471 in both apps. Every row below the composer prompt has the same height in both apps, and text ink heights are equal.
- The whole offset (18 px) is above the composer prompt. RN `useHeaderOffset` (src/components/hooks/useHeaderOffset.ts) gives the list `paddingTop` as `52 + 23 + 20 * fontScale - 4` dp with the linear `Configuration.fontScale`. The tab text itself goes through `PixelUtil.toPixelFromSP`, which uses the nonlinear Android 16 `FontScaleConverter`: 20 sp is 34 dp (102 px), not 40 dp (120 px). 120 - 102 = 18 px.
- RN `DisplayMetricsHolder` keeps `fontScaleConverter`, so RN text scales nonlinearly, the same as `RnText`.

Fix: `RnFontScale.jsLineHeightExcessPx` in `designsystem/text/RnLineBox.kt` gives the difference between a JS `lineHeight * fontScale` prediction and the real line box. Home adds it to the measured header height for the content top and `FeedFooter`: 0 px at 1.0, 7 px at 1.3, 18 px at 2.0. `RnLineBoxTest` checks line boxes at 1.0, 1.3 and 2.0 with the Android 16 converter tables.

## Seed race

`?users&follows&posts&feeds&thread` creates the follows, and the AppView indexes them asynchronously, so a profile loaded early can show "1 follower" or "1 following". Server check: alice.test has 2 followers (bob, carla) and 2 follows.

`flows/pack/wait-profile-counts.yml` (used by `readonly.yml` and `empty.yml`) waits for "2 followers" and "2 following", pulls to refresh up to 4 times if needed, and then asserts each count inside its id node (`profileHeaderFollowersButton`, `profileHeaderFollowsButton`). In both apps the label is on a child of the id node. The counts are not masked. In r4, both base profile captures passed the wait.

## Event diff

The RN side is derived from source, not captured: the RN `.env` sets `EXPO_PUBLIC_LOG_DEBUG=session`, which hides `metric` logs.

Result on the r4 interactions capture (native `1862ea3`), with `--ignore uri,authorDid,initMs,likerClout,postClout`:

```
MATCH (unordered): rn 16 event(s), native 16 event(s)
MISMATCH (ordered): rn 16 event(s), native 16 event(s)
  order differs at 3: rn router:navigate (line 4), native home:feedDisplayed (line 4)
  order differs at 4: rn init (line 5), native router:navigate (line 5)
  order differs at 5: rn home:feedDisplayed (line 6), native init (line 6)
```

| Rule | RN source | State |
|---|---|---|
| `router:navigate {}` and `init` again after sign-in | App.tsx renders the tree under `key={currentAccount?.did}`; Navigation.tsx `onNavigationReady` runs once per mount | Fixed (`1862ea3`), captured: present |
| `home:feedDisplayed` after the remount events | Home.tsx mounts `HomeScreenReady` (which logs it on focus) only when preferences and pinned feeds have loaded | **Fixed in `5368546`, after the capture. Not re-captured.** This is the remaining ordered difference |
| `post:view` positions `[0, 1, 2, 3]`, top to bottom | `PostFeed` `getPostPosition`; `List` viewability 40% for 500 ms, in index order | Fixed (`1bb20f6`, `1862ea3`), captured: in order |
| Anchor `post:view` before `router:navigate {from: Home}` | `BaseNavigationContainer` calls `onStateChange` from its own `useEffect`; the thread screen's anchor effect runs first in the same commit | Expected list corrected in `5368546`; native matches |
| No `thread:preferences:load` in this flow | `useThreadPreferences.ts:50-71` logs only when server preferences change after mount; Home already loaded them (`replaceEqualDeep`) | Expected list corrected in `1862ea3`; native logs nothing, as expected. The iOS port is being aligned to this rule |

`likerClout` and `postClout` are ignored because they depend on the follower count at tap time.

## Not measured

| Item | Reason |
|---|---|
| RN base r4b (RN noise floor) | 01:10:47: `setup-app.yml` did not see "Sign in" within 180 s |
| Native dark r4 | 01:27:08: signed in, then home showed "Unable to connect" |
| RN dark r4 and every later state (font2, empty, error) | 01:27 to 01:44: RN dark hung in `setupServer.js`. At 01:43 and 01:44, `app.bsky.feed.getTimeline` through the PDS returned HTTP 502 after 10.8 s. PDS, seed manager and Metro still answered on 3000, 1986 and 8081. The dev network (`mock-server.ts`) was not restarted, because it is under a supervisor |
| Capture with `5368546` | Built and tested only; the device work was stopped by the server failure |

Earlier on 2026-09-15 (20:29 to 20:40) and 2026-09-16 (00:36), captures also failed for two setup reasons, both cleared: a `com.mobilenext.mobilecli.DeviceServer` held UiAutomation on the emulator (the lead stopped it), and the adb reverse rules were missing (now re-added before each capture).

## States covered

| State | RN | Native | Notes |
|---|---|---|---|
| Home, thread, profile (light) | r4 | r4 (`1862ea3`) | Same run |
| After like and repost | r4 | r4 (`1862ea3`) | Home row not valid (different moment) |
| Dark (system dark = dim) | r2 | `41c1923` | Stale |
| Font scale 2.0 | r2 | `41c1923` | Stale; home fix not captured; profile baseline needs posts |
| Empty seed (`?users&follows`) | home r1, profile r3 | `41c1923` | Stale |
| Error (reseed after sign-in) | r2 | `41c1923` | Stale; home row invalid |
| Loading, load-more error, profile error | no | no | Covered by JVM and Compose UI tests |

New RN baselines this round (none overwritten): `parity/baseline/rn/android-r4-home.png`, `android-r4-thread.png`, `android-r4-profile.png`, `android-interactions-r4-thread.png`.

## Tests

| Suite | Result |
|---|---|
| JVM (`testDebugUnitTest`, with `assembleDebug assembleRelease`), code `5368546` | 109 tests, 0 failures |
| Compose UI and Keystore (`:app:connectedDebugAndroidTest`, emulator-5554), code `1862ea3` | 10 tests, 0 failures |

## Differences that remain

None of these is accepted. Each needs a named owner.

| # | Difference | Screen | Evidence |
|---|---|---|---|
| 1 | Home masked 1.61% (drift) | Home | Results r4 |
| 2 | Font scale 2.0 home offset fixed in code, not re-captured; font scale 2.0 profile needs an RN baseline with posts | Home, profile | "Font scale" |
| 3 | `home:feedDisplayed` order fixed in code, not re-captured | Events | "Event diff" |
| 4 | Relative time: native shows "now" where RN shows "2m" (tick start differs) | Home, thread, profile | Masked |
| 5 | Header dropdown and share glyph shapes (Lucide) | All | Glyph masks |
| 6 | Code-drawn fallback avatars | All | Not masked; counted |
| 7 | Scroll indicator after navigation | Profile | RN shows a scrollbar; Compose lists show none |
| 8 | Profile description links and mentions are not detected | Profile | RN runs `detectFacets`; the seeded bio has none |

## Accepted design differences

| Difference | Platform | RN behaviour | Native behaviour | Reason | Accepted by | Parity check |
|---|---|---|---|---|---|---|
| UI icons | Android and iOS | Central icon system glyphs | Lucide (ISC) glyphs | licensing: ASSETS.md sections 2 and 3 | awaiting owner acceptance | Glyph-box masks |
| Header logo | Android and iOS | Bluesky butterfly | Neutral placeholder mark | licensing: ASSETS.md sections 2 and 3 | awaiting owner acceptance | Masked as a glyph |
| Fallback avatar | Android and iOS | Bluesky default avatar artwork | Silhouette drawn in code | licensing: ASSETS.md sections 2 and 3 | awaiting owner acceptance | Not masked |

The proposed out-of-scope rows in `plan/slice-plan.md` also await owner acceptance.

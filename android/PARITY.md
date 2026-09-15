# Parity report: Android native vs RN Android

Every number in this file was measured with the commands below. Nothing is estimated. Where a result is not valid evidence, the row says so and gives the reason.

Acceptance: **awaiting owner acceptance.**

## Verdict

- **Every state is now measured in the same run as its RN capture** (2026-09-16, r4 and r5), except the RN empty profile (see "Not measured").
- **No screen fails masked except error home (5.34%)**, which is a real behaviour difference (see "Error home").
- **Drift, above 1% (a person who did not write the port must sign off):** base home 1.61%, dark home 1.75%, font scale 2.0 home 1.68%, profile 1.26% and thread 1.45%, empty home 1.38%.
- **Font scale 2.0 home: 6.24% → 1.68%** after the header offset fix (`29055d7`).
- **No screen passes raw.**
- **Events: ordered MATCH, 16 of 16** (`events.mjs --ordered`, native `5368546`).
- **Noise floor:** native masked 0 px on home and profile, 0.10% on thread; RN masked 0 px on home and profile, 0.16% on thread.

## Setup

| Item | Value |
|---|---|
| Device | emulator-5554, Pixel 10 Pro AVD, Android 16 (API 36.1), 1280x2856, density 480 (3 px = 1 dp) |
| RN app | `xyz.blueskyweb.app`, bluesky-social-app at `2bfb94d` (react-native 0.86.0), e2e build, Metro on 8081 |
| Native app | `io.nativestarter.showcase.bluesky`, debug build. Base, noise and interactions screenshots (r4, r4b): `1862ea3`. Interactions events (r5), dark, font scale 2.0, empty and error (r4): `5368546`. The two builds differ only in when `home:feedDisplayed` is logged |
| Server | dev network PDS `http://localhost:3000`, AppView DID `did:plc:bw7ad3erl7btq6qwf66yqiov`, reseeded by every flow under `/tmp/bsky-seed.lock`. Since 2026-09-16 03:21 the dev network uses embedded Postgres (5435) and Homebrew Redis (6382) instead of Docker |
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
2. One capture. `run-step.sh` stops when another app is in the foreground (exit 3) or a mobilecli DeviceServer is on emulator-5554 (exit 4), then calls `run-pack.sh`, which takes the seed lock, sets dark mode or font scale, runs `capture.mjs` and releases the lock:
   ```
   ./run-step.sh <native|rn> <base|dark|font2|interactions|empty|error> <out-root>
   ```
   Both apps are captured in one batch, one capture at a time. Before each capture the batch checks the adb reverse rules and `app.bsky.feed.getTimeline` through the AppView.
3. Masks and diffs (the RN side is the same-run RN capture):
   ```
   python3 make_masks.py masks-spec.json <state> <root>/<state>/rn/android <root>/<state>/native/android <root>/masks-<state>.json
   node <pack>/diff.mjs --rn <root>/<state>/rn/android --native <root>/<state>/native/android --out <root>/<state>/diff-raw
   node <pack>/diff.mjs --rn <root>/<state>/rn/android --native <root>/<state>/native/android --out <root>/<state>/diff-masked --mask <root>/masks-<state>.json
   ```
   Empty: RN has no profile image, so the diff uses home only. `make_masks.py` needs a profile image on each side, so the native empty profile is given to both sides for mask building, and only the `home` masks are passed to `diff.mjs`.
4. Noise floor: the same `diff.mjs` commands between two base runs of one app (`r4/base/<app>` and `r4b/base/<app>`) with the base masks.
5. Events (the native log is read right after the native interactions capture):
   ```
   adb -s emulator-5554 shell run-as io.nativestarter.showcase.bluesky cat files/events.jsonl > r5/interactions/native-events.jsonl
   node <pack>/events.mjs --rn events/rn-expected-interactions.jsonl --native r5/interactions/native-events.jsonl \
     --ignore uri,authorDid,initMs,likerClout,postClout --ordered
   ```

The raw capture folders (`r4`, `r4b`, `r5`) are not committed. The RN captures used as baselines are committed in `parity/baseline/rn/` (see "States covered").

## Masks

`make_masks.py` measures each mask on both images and merges them. Bands are declared per state and screen in `masks-spec.json`.

| Kind | What it hides | Reason |
|---|---|---|
| `status` | y 0 to 150 | Status bar: clock, battery, signal |
| `glyphs` | Ink clusters of icon glyphs, at least `minw` px wide | Licence substitution: Lucide glyphs for the Central icon set, and the neutral header mark (ASSETS.md sections 2 and 3) |
| `tail` | The right-aligned relative time of a post meta line | Capture time |
| `rect` | The thread anchor date line and its globe glyph | Capture time; the globe is a Lucide glyph |

Not masked: counts (reply, repost and like counts, stats rows, profile metrics, the badge), names, handles, post text, buttons, borders, backgrounds and the code-drawn fallback avatars.

Mask area (4 px grid, overlaps removed): every screen is under the 15% limit. The largest is font scale 2.0 home, 11.73%.

## Results (same run, 2026-09-16)

Fuzz 5%, threshold 0.02.

| State | Screen | Raw px (ratio) | Masked px (ratio) | Status | Mask area | Valid? |
|---|---|---|---|---|---|---|
| base | home | 115,250 (3.15%) | 58,774 (1.61%) | drift | 10.89% | Yes |
| base | thread | 55,152 (1.51%) | 26,392 (0.72%) | pass | 8.97% | Yes |
| base | profile | 109,202 (2.99%) | 30,031 (0.82%) | pass | 7.04% | Yes. Both apps waited for "2 followers" and "2 following" |
| dark | home | 116,780 (3.19%) | 64,050 (1.75%) | drift | 10.89% | Yes |
| dark | thread | 62,664 (1.71%) | 33,682 (0.92%) | pass | 8.96% | Yes |
| dark | profile | 47,847 (1.31%) | 31,845 (0.87%) | pass | 7.04% | Yes |
| font scale 2.0 | home | 131,032 (3.58%) | 61,350 (1.68%) | drift | 11.73% | Yes |
| font scale 2.0 | thread | 98,260 (2.69%) | 52,873 (1.45%) | drift | 10.31% | Yes |
| font scale 2.0 | profile | 143,220 (3.92%) | 46,175 (1.26%) | drift | 7.33% | Yes. Posts loaded in both apps |
| empty | home | 74,793 (2.05%) | 50,380 (1.38%) | drift | 7.40% | Yes |
| empty | profile | — | — | not measured | — | See "Not measured" |
| error | home | 252,252 (6.90%) | 195,355 (5.34%) | **fail** | 11.01% | Yes. Real difference, see "Error home" |
| error | thread | 32,310 (0.88%) | 24,120 (0.66%) | pass | 6.29% | Yes. Both show "Error loading post" and Retry |
| interactions | home | 221,505 (6.06%) | 162,854 (4.45%) | **fail** | 10.90% | **No.** Native shows the Discover notice and the end-of-feed block below the five posts; RN does not show them yet. The posts, counts and colours match |
| interactions | thread | 65,131 (1.78%) | 32,906 (0.90%) | pass | 9.05% | Yes |

### Font scale 2.0 home before and after

| Native build | RN baseline | Masked |
|---|---|---|
| `41c1923` | r2 | 228,220 (6.24%) |
| `5368546` (with `29055d7`) | r4, same run | 61,350 (1.68%) |

## Noise floor

Two base runs of each app, same build, same batch (`r4` and `r4b`), base masks:

| Screen | Native raw | Native masked | RN raw | RN masked |
|---|---|---|---|---|
| home | 772 (0.02%) | 0 (0.00%) | 3,267 (0.09%) | 0 (0.00%) |
| thread | 9,157 (0.25%) | 3,803 (0.10%) | 17,343 (0.47%) | 5,941 (0.16%) |
| profile | 760 (0.02%) | 0 (0.00%) | 2,628 (0.07%) | 0 (0.00%) |

The raw pixels are the status bar and relative times. The thread residual is inside the anchor area in both apps. Every drift above is at least 1.2 percentage points above the noise floor.

## Error home

The error flow signs in, reseeds the server, opens the cached thread, goes back and pulls to refresh. In the same run:

- RN shows the five posts and the end-of-feed block ("You've reached the end of your feed!").
- Native shows the five posts, the Discover notice ("We ran out of posts from your follows") and the load-more error ("There was an issue fetching posts. Tap here to try again.").

The previous report called this row invalid because the RN baseline came from another run. With the same-run capture, the difference is in behaviour: after the failed refresh, native keeps the fallback marker and shows the load-more error, and RN does not. The cause is not yet traced in `PostFeed.tsx` / `post-feed.ts`. It is open (difference 2).

## Font scale (`29055d7`)

Measurement of the `41c1923` font scale 2.0 images showed that the text line boxes were not the cause:

- The tab bar border was at y 471 in both apps. Every row below the composer prompt had the same height in both apps, and text ink heights were equal.
- The whole offset (18 px) was above the composer prompt. RN `useHeaderOffset` (src/components/hooks/useHeaderOffset.ts) gives the list `paddingTop` as `52 + 23 + 20 * fontScale - 4` dp with the linear `Configuration.fontScale`. The tab text itself goes through `PixelUtil.toPixelFromSP`, which uses the nonlinear Android 16 `FontScaleConverter`: 20 sp is 34 dp (102 px), not 40 dp (120 px). 120 - 102 = 18 px.
- RN `DisplayMetricsHolder` keeps `fontScaleConverter`, so RN text scales nonlinearly, the same as `RnText`.

Fix: `RnFontScale.jsLineHeightExcessPx` in `designsystem/text/RnLineBox.kt` gives the difference between a JS `lineHeight * fontScale` prediction and the real line box. Home adds it to the measured header height for the content top and `FeedFooter`: 0 px at 1.0, 7 px at 1.3, 18 px at 2.0. `RnLineBoxTest` checks line boxes at 1.0, 1.3 and 2.0 with the Android 16 converter tables. Result: home 6.24% → 1.68% masked.

## Seed race

`?users&follows&posts&feeds&thread` creates the follows, and the AppView indexes them asynchronously, so a profile loaded early can show "1 follower" or "1 following". Server check: alice.test has 2 followers (bob, carla) and 2 follows.

`flows/pack/wait-profile-counts.yml` (used by `readonly.yml` and `empty.yml`) waits for "2 followers" and "2 following", pulls to refresh up to 4 times if needed, and then asserts each count inside its id node (`profileHeaderFollowersButton`, `profileHeaderFollowsButton`). In both apps the label is on a child of the id node. The counts are not masked. Every base, dark and font scale 2.0 profile capture in both apps passed the wait.

## Event diff

The RN side is derived from source, not captured: the RN `.env` sets `EXPO_PUBLIC_LOG_DEBUG=session`, which hides `metric` logs.

Result on the r5 interactions capture (native `5368546`), with `--ignore uri,authorDid,initMs,likerClout,postClout`:

```
MATCH (unordered): rn 16 event(s), native 16 event(s)
MATCH (ordered): rn 16 event(s), native 16 event(s)
```

The r4 capture (native `1862ea3`) was MATCH unordered and MISMATCH ordered at lines 4 to 6 (`home:feedDisplayed` before the remount events). `5368546` fixed that.

| Rule | RN source | Fixed in |
|---|---|---|
| `router:navigate {}` and `init` again after sign-in | App.tsx renders the tree under `key={currentAccount?.did}`; Navigation.tsx `onNavigationReady` runs once per mount | `1862ea3` |
| `home:feedDisplayed` after the remount events | Home.tsx mounts `HomeScreenReady` (which logs it on focus) only when preferences and pinned feeds have loaded | `5368546` |
| `post:view` positions `[0, 1, 2, 3]`, top to bottom | `PostFeed` `getPostPosition`; `List` viewability 40% for 500 ms, in index order | `1bb20f6`, `1862ea3` |
| Anchor `post:view` before `router:navigate {from: Home}` | `BaseNavigationContainer` calls `onStateChange` from its own `useEffect`; the thread screen's anchor effect runs first in the same commit | Expected list corrected in `5368546` |
| No `thread:preferences:load` in this flow | `useThreadPreferences.ts:50-71` logs only when server preferences change after mount; Home already loaded them (`replaceEqualDeep`) | Expected list corrected in `1862ea3`; the lead confirmed this reading, and iOS is aligning |

`likerClout` and `postClout` are ignored because they depend on the follower count at tap time.

## Not measured

| Item | Reason |
|---|---|
| RN empty profile | Two attempts (03:44 and 03:55). RN stayed on the profile loading placeholder (banner, avatar and button skeletons, no metrics, no feed) and did not show "No posts yet" within 30 s. Native loaded the empty profile. The cause is not traced. The older RN r3 empty profile baseline exists, but a cross-run diff is not used here |
| Interactions home, same moment | See the invalid row above. A flow wait for the end-of-feed state in both apps would fix it |
| Loading, load-more error, profile error | Covered by JVM and Compose UI tests only |

Earlier failures, all cleared: a `com.mobilenext.mobilecli.DeviceServer` held UiAutomation on the emulator (2026-09-15 20:29 to 20:40; stopped by the lead), missing adb reverse rules (2026-09-16 00:36), and the dev network AppView returning HTTP 502 because Docker had wedged (2026-09-16 01:27 to 01:44; fixed by the lead at 03:21).

## States covered

| State | RN | Native | Notes |
|---|---|---|---|
| Home, thread, profile (light) | r4 | `1862ea3` | Same run |
| After like and repost | r4 | `1862ea3` screenshots, `5368546` events | Home row not valid |
| Dark (system dark = dim) | r4 | `5368546` | Same run |
| Font scale 2.0 | r4 | `5368546` | Same run; profile with posts |
| Empty seed (`?users&follows`) | r4 home only | `5368546` | RN profile not captured |
| Error (reseed after sign-in) | r4 | `5368546` | Same run; home fails on behaviour |

New RN baselines this round (none overwritten), in `parity/baseline/rn/`: `android-r4-{home,thread,profile}.png`, `android-interactions-r4-thread.png`, `android-dark-r4-{home,thread,profile}.png`, `android-font2-r4-{home,thread,profile}.png`, `android-error-r4-{home,thread}.png`, `android-empty-r4-home.png`.

## Tests

| Suite | Result |
|---|---|
| JVM (`testDebugUnitTest`, with `assembleDebug assembleRelease`), code `5368546` | 109 tests, 0 failures |
| Compose UI and Keystore (`:app:connectedDebugAndroidTest`, emulator-5554), code `1862ea3` | 10 tests, 0 failures |

## Differences that remain

None of these is accepted. Each needs a named owner.

| # | Difference | Screen | Evidence |
|---|---|---|---|
| 1 | Drift above 1%: base home 1.61%, dark home 1.75%, font scale 2.0 home 1.68%, thread 1.45% and profile 1.26%, empty home 1.38% | Home, thread, profile | Results |
| 2 | After a failed refresh, native shows the Discover notice and the load-more error; RN shows the end-of-feed block | Home (error) | "Error home" |
| 3 | RN empty profile not captured (RN stays on the loading placeholder) | Profile (empty) | "Not measured" |
| 4 | Interactions home screenshots are taken at different moments of the feed load | Home (interactions) | Results |
| 5 | Relative time: native shows "now" where RN shows "1m" or "2m" (tick start differs) | Home, thread, profile | Masked |
| 6 | Header dropdown and share glyph shapes (Lucide) | All | Glyph masks |
| 7 | Code-drawn fallback avatars | All | Not masked; counted |
| 8 | Scroll indicator after navigation | Profile | RN shows a scrollbar; Compose lists show none |

## Accepted design differences

| Difference | Platform | RN behaviour | Native behaviour | Reason | Accepted by | Parity check |
|---|---|---|---|---|---|---|
| UI icons | Android and iOS | Central icon system glyphs | Lucide (ISC) glyphs | licensing: ASSETS.md sections 2 and 3 | awaiting owner acceptance | Glyph-box masks |
| Header logo | Android and iOS | Bluesky butterfly | Neutral placeholder mark | licensing: ASSETS.md sections 2 and 3 | awaiting owner acceptance | Masked as a glyph |
| Fallback avatar | Android and iOS | Bluesky default avatar artwork | Silhouette drawn in code | licensing: ASSETS.md sections 2 and 3 | awaiting owner acceptance | Not masked |

The proposed out-of-scope rows in `plan/slice-plan.md` also await owner acceptance.

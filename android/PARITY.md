# Parity report: Android native vs RN Android

Every number in this file was measured with the commands below. Nothing is estimated. Where a result is not valid evidence, the row says so and gives the reason.

Acceptance: **awaiting owner acceptance.**

## Verdict

- **Home passes the 2% gate with masks, in light and dark:** 1.61% and 1.75% (`drift`: above 1%, so a person who did not write the port must sign off). Before this round it was 3.75% with the same masks.
- Thread and profile pass or drift below 2% in light and dark.
- **Font scale 2.0 fails** (home 6.24%, profile 2.17%, measured on `41c1923`). The cause is now found and fixed in code (`29055d7`), but not re-captured. See "Font scale".
- **Error home fails** (5.34%): the RN baseline shows a different moment of the error flow, so this row is not valid evidence.
- **No screen passes raw.**
- **Events: MISMATCH, 4 differences on the last capture** (see "Event diff"). All 4 are addressed in code (`1bb20f6`, `1862ea3`) and covered by JVM tests. They are not re-captured.
- **Not re-captured on the final code `1862ea3`: every state.** All result rows below come from `1bb20f6` (base, noise) or `41c1923` (other states). On 2026-09-15, four capture attempts (20:29, 20:30, 20:36 and 20:40 IST, the last after a 2-minute wait) failed with `Maestro Android driver did not start up in time` (tcp:7001). Logcat shows the cause: the Maestro driver crashed in `Instrumentation.getUiAutomation` ("Cannot call disconnect() while connecting UiAutomation"). Android allows one UiAutomation client. During every attempt, `com.mobilenext.mobilecli.DeviceServer` (`app_process`, PID 16593) was running on emulator-5554, with the adb forward `tcp:52038 -> localabstract:mobilecli-server`. Three `npm exec @mobilenext/mobile-mcp@latest` processes on the host (PIDs 16991, 98290, 84936) belong to other sessions, so none was stopped. Connected tests did run on the final code.

## Setup

| Item | Value |
|---|---|
| Device | emulator-5554, Pixel 10 Pro AVD, Android 16 (API 36.1), 1280x2856, density 480 (3 px = 1 dp) |
| RN app | `xyz.blueskyweb.app`, bluesky-social-app at `2bfb94d` (react-native 0.86.0), e2e build, Metro on 8081 |
| Native app | `io.nativestarter.showcase.bluesky`, debug build. Base and noise: commit `1bb20f6` (final). Other states: `41c1923` (see Verdict) |
| Server | dev network PDS `http://localhost:3000`, AppView DID `did:plc:bw7ad3erl7btq6qwf66yqiov`, reseeded by every flow under `/tmp/bsky-seed.lock` |
| Pack tools | `migration-pack/skills/check-parity/scripts/capture.mjs`, `diff.mjs` (threshold 0.02, fuzz 5%), `report.mjs`, `events.mjs` |
| Total pixels per image | 3,655,680 |

## Commands

Run from `android/parity`. `<pack>` is `../../../../migration-pack/skills/check-parity/scripts`.

1. Build and install (under `/tmp/native-build.lock`):
   ```
   ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx3g :app:testDebugUnitTest :app:installDebug
   ```
2. Capture one state for one app. The script takes the seed lock, sets dark mode or font scale, runs `capture.mjs` with a flow in `flows/pack/` and resets the device:
   ```
   ./run-pack.sh native <base|dark|font2|interactions|empty|error> out
   ./run-pack.sh rn <state> out
   ```
3. RN side of each diff: the committed baselines in `parity/baseline/rn/`, copied to `out/<state>/rnbase/android/{home,thread,profile}.png`:

   | State | RN files |
   |---|---|
   | base | `android-home`, `android-thread`, `android-profile` |
   | dark | `android-dark-r2-*` |
   | font2 | `android-font2-r2-*` |
   | interactions | `android-interactions-home`, `android-interactions-r3-thread` |
   | empty | `android-empty-home`, `android-empty-r3-profile` |
   | error | `android-error-r2-home`, `android-error-r2-thread` |

4. Masks, diffs and report:
   ```
   python3 make_masks.py masks-spec.json <state> out/<state>/rnbase/android out/<state>/native/android out/masks-<state>.json
   node <pack>/diff.mjs --rn out/<state>/rnbase/android --native out/<state>/native/android --out out/<state>/diff-raw
   node <pack>/diff.mjs --rn out/<state>/rnbase/android --native out/<state>/native/android --out out/<state>/diff-masked --mask out/masks-<state>.json
   node <pack>/report.mjs --diff out/<state>/diff-masked/results.json --out out/<state>/parity-report.md --title "<state> (Android)"
   ```
5. Layout measurement (used for the line-box work): `adb -s emulator-5554 shell uiautomator dump`. It gives the RN Android text view bounds. `magick`/PIL row profiles give border rows and ink rows.

## Masks

`make_masks.py` is the Android port of `ios/parity/make_masks.py`. Each mask is measured on both images and merged. Bands are declared per state and screen in `masks-spec.json`. Font scale 2.0 has its own bands, which follow the larger layout.

| Kind | What it hides | Reason |
|---|---|---|
| `status` | y 0 to 150 | Status bar: clock, battery, signal |
| `glyphs` | Ink clusters of icon glyphs, at least `minw` px wide (36 in post control rows, 40 in the thread anchor row). The "more" ellipsis has its own band. The bottom bar band starts below the notification badge | Licence substitution: Lucide glyphs for the Central icon set, and the neutral header mark (ASSETS.md sections 2 and 3) |
| `tail` | The right-aligned relative time of a post meta line | Capture time |
| `rect` | The thread anchor date line and its globe glyph | Capture time; the globe is a Lucide glyph |

Not masked: counts (the "1" next to the reply glyph, stats rows, profile metrics, the badge), names, handles, post text, buttons and their chevrons, borders, backgrounds and the code-drawn fallback avatars.

## Results

Fuzz 5%, threshold 0.02. The mask area uses the `diff.mjs` pixel rule (`floor`/`ceil` of the fractions).

| State | Screen | Raw px (ratio) | Masked px (ratio) | Status | Mask area | Valid? |
|---|---|---|---|---|---|---|
| base | home | 117,589 (3.22%) | 58,780 (1.61%) | drift | 10.51% | Yes |
| base | thread | 59,801 (1.64%) | 27,164 (0.74%) | pass | 8.70% | Yes |
| base | profile | 114,872 (3.14%) | 34,940 (0.96%) | pass | 6.86% | Partly. The RN baseline shows "2 followers"; this native run shows "1 follower" (see "Seed race") |
| dark | home | 119,132 (3.26%) | 64,050 (1.75%) | drift | 10.48% | Yes |
| dark | thread | 67,224 (1.84%) | 34,546 (0.94%) | pass | 8.70% | Yes |
| dark | profile | 55,240 (1.51%) | 36,753 (1.01%) | drift | 6.86% | Yes |
| font scale 2.0 | home | 310,586 (8.50%) | 228,220 (6.24%) | **fail** | 12.21% | Yes |
| font scale 2.0 | thread | 102,765 (2.81%) | 52,873 (1.45%) | drift | 9.99% | Yes. "Everybody can reply" wraps to its own line in both apps |
| font scale 2.0 | profile | 173,941 (4.76%) | 79,299 (2.17%) | **fail** | 6.92% | Partly. RN r2 shows "1 following", native "2 following" (seed race) |
| empty | home | 78,394 (2.14%) | 50,380 (1.38%) | drift | 7.25% | Yes |
| empty | profile | 117,198 (3.21%) | 35,741 (0.98%) | pass | 6.83% | Partly. Both show "No posts yet" and "Write a post"; RN r3 shows "1 follower", native "2 followers" (seed race) |
| error | home | 252,694 (6.91%) | 195,355 (5.34%) | **fail** | 10.62% | **No.** RN r2 shows the full feed with the end-of-feed block; native shows the Discover notice and the load-more error "There was an issue fetching posts". The two captures are at different moments of the reseed |
| error | thread | 32,311 (0.88%) | 24,120 (0.66%) | pass | 6.16% | Yes. Both show "Error loading post" and Retry |
| interactions | home, thread | — | — | not measured | — | The final native capture failed (see Verdict); the run deleted the earlier native images. The RN r3 thread baseline is valid |

Every mask area is under the 15% limit.

### Home before and after (P2)

Same masks (`base` bands), same RN baseline:

| Native build | Raw | Masked |
|---|---|---|
| `2c45d66` (before) | 194,006 (5.31%) | 137,239 (3.75%) |
| `1bb20f6` (final) | 117,589 (3.22%) | 58,780 (1.61%) |

With the old whole-row masks (`compare.sh`), home went from 3.38% to 1.29%, and dark home from 3.96% to 1.42%.

## Noise floor

Two native base captures, diffed with the same commands and masks: `out2/base/native` (build `41c1923`, 18:36 IST) and `out/base/native` (build `1bb20f6`, 18:42 IST). The two builds draw the same UI; only the `post:view` position code differs. A second capture of `1bb20f6` itself could not be taken (see Verdict).

```
node <pack>/diff.mjs --rn out2/base/native/android --native out/base/native/android --out out/noise/raw
node <pack>/diff.mjs --rn out2/base/native/android --native out/base/native/android --out out/noise/masked --mask out/masks-base.json
```

| Screen | Raw | Masked |
|---|---|---|
| home | 6,813 (0.19%) | 0 (0.00%) |
| thread | 2,766 (0.08%) | 0 (0.00%) |
| profile | 2,786 (0.08%) | 0 (0.00%) |

The raw pixels are the status bar clock, the relative times and the thread date line. In this pair both runs showed "2 followers". An earlier pair of the same build gave 10,592 px (0.29%) masked on profile, because the follower count changed between the runs (see "Seed race").

## Line boxes (P2)

`designsystem/text/RnLineBox.kt` ports the React Native 0.86 Android text measurement in one place (review A19: no per-screen offsets):

- Font size: `ceil(PixelUtil.toPixelFromSP(fontSize))` (`TextAttributes.effectiveFontSize`). For example, text_sm 13.1 sp is painted at 40 px, not 39.3 px.
- Explicit line height: `CustomLineHeightSpan` — line box `ceil(lineHeight)`, `ascent -= ceil(L/2)`, `descent += floor(L/2)`, first top and last bottom set to them, so `includeFontPadding` has no effect.
- No line height: `StaticLayout`/`BoringLayout` with `includeFontPadding` (RN default true): pitch `descent - ascent`, plus `ascent - top` above and `bottom - descent` below.
- The metrics come from `Paint.getFontMetricsInt` at runtime, for the same Inter file RN uses. Intrinsic heights return the RN box, because feed rows use `IntrinsicSize.Min`.
- ALF `Text` always applies `leading_snug` (Typography.tsx). Legacy `Text` (Discover notice, end of feed, load-more retry) uses `legacyText`.

Measured with `uiautomator dump` on RN: meta 51 px, post text 60 px, counts 51 px, Discover notice 120 px, end-of-feed message 135 px. `RnLineBoxTest` predicts each of these from the font file metrics.

After the change, every home border is on the RN pixel row: 614, 1131, 1402, 1673, 2641 in both apps. Post names and post text are on the same rows as RN.

## Font scale (`29055d7`, not re-captured)

The rule of this review was "native rows sit 18 to 21 px higher, fix the line box". Measurement of the `41c1923` font scale 2.0 images shows that the line boxes are not the cause:

- Home: the tab bar border is at y 471 in both apps. Every row below the composer prompt has the same height in both apps (705, 348, 348, 349 px between borders). Ink heights of the text are equal (for example 76 px for "What's up?").
- The whole offset (18 px) is above the composer prompt. RN `useHeaderOffset` (src/components/hooks/useHeaderOffset.ts) gives the list `paddingTop` as `52 + 23 + 20 * fontScale - 4` dp. `fontScale` from `useWindowDimensions` is the linear `Configuration.fontScale`. The tab text is a real text node with lineHeight 20, and on Android 16 `PixelUtil.toPixelFromSP` sends it through the nonlinear `FontScaleConverter`: 20 sp is 34 dp (102 px), not 40 dp (120 px). 120 - 102 = 18 px.
- RN `DisplayMetricsHolder` keeps `fontScaleConverter` (`setTo`, and `getRealMetrics` does not clear it), so RN text is nonlinear, the same as `RnText`.
- Profile font scale 2.0: the ink rows match RN down to y 1336. Below that the RN r2 baseline shows no posts, and it shows "1 following" (seed race). That row needs a new RN baseline, not a layout change.

Fix: `RnFontScale.jsLineHeightExcessPx` in `designsystem/text/RnLineBox.kt` gives the difference between a JS `lineHeight * fontScale` prediction and the real line box. Home adds it to the measured header height, for the content top and for `FeedFooter`. It is 0 px at 1.0, 7 px at 1.3 and 18 px at 2.0. `RnLineBoxTest` checks line boxes at 1.0, 1.3 and 2.0 with the Android 16 converter tables.

## Seed race

`?users&follows&posts&feeds&thread` creates Bob's follow of Alice, and the AppView indexes it asynchronously. The server returns `followersCount: 2` (checked with `app.bsky.actor.getProfile` as alice.test). The native app loads the profile within a few seconds of the seed, so some runs show "1 follower" and some "2 followers". Two native runs of the same build showed both. RN starts later and showed "2 followers" in base. The same race changes the following count in the r2/r3 profile baselines. It is a capture timing effect, not a port difference, but it is counted in the profile ratios above.

Flow change (not yet run): `flows/pack/wait-profile-counts.yml` waits for `profileHeaderFollowersButton` with "2 followers" and `profileHeaderFollowsButton` with "2 following" before the profile screenshot, in `readonly.yml` and `empty.yml`. If the profile loaded before indexing, it pulls to refresh, up to 4 times, then asserts. The counts are not masked. Server check on 2026-09-15: alice.test has 2 followers (bob, carla) and 2 follows.

## Event diff

**Result: MISMATCH, 4 differences.** The RN side is derived from source, not captured. The RN `.env` sets `EXPO_PUBLIC_LOG_DEBUG=session`, which hides `metric` logs, and changing upstream is not allowed. The derivation follows `ios/PARITY.md` "Event diff": the shell remount on sign-in and `thread:preferences:load`. `post:view` positions follow `PostFeed.tsx` `getPostPosition`.

```
adb -s emulator-5554 shell run-as io.nativestarter.showcase.bluesky cat files/events.jsonl > out/interactions/native-events.jsonl
node <pack>/events.mjs --rn events/rn-expected-interactions.jsonl --native out/interactions/native-events.jsonl \
  --ignore uri,authorDid,initMs,likerClout,postClout --out out/interactions/events-result.json
```

The native log is from the interactions flow on build `41c1923` (14 events). Output:

```
MISMATCH (unordered): rn 17 event(s), native 14 event(s), ignoring uri, authorDid, initMs, likerClout, postClout
  missing in native: init {} (rn line 2)
  missing in native: router:navigate {} (rn line 1)
  missing in native: thread:preferences:load {"sort":"hotness","view":"linear"} (rn line 15)
  props differ:      post:view (rn line 7, native line 5): position rn=0 native=1
```

| Difference | State |
|---|---|
| `post:view` position for the thread reply: RN counts root rows before the slice's first row | **Fixed** in `1bb20f6` (`PostViewPosition`, test expects `[0, 1, 2, 3]`). Not re-captured |
| Second `router:navigate {}` and `init` after sign-in | **Fixed** in `1862ea3`. Confirmed in source: App.tsx renders the tree under `<Fragment key={currentAccount?.did}>`, and Navigation.tsx `onNavigationReady` (`useCallOnce`) runs once per mount. `NavigationReadyEvents` logs on every account key change (`EventRulesTest`). Not re-captured |
| `thread:preferences:load {sort: hotness, view: linear}` when the thread opens | **The RN expected line was wrong for this flow; removed** in `1862ea3`. `useThreadPreferences.ts:50-71` logs only when `serverPrefs` changes after mount. `usePreferencesQuery` uses `replaceEqualDeep`, and Home already loaded the preferences, so `useState(serverPrefs)` starts with the same object and nothing is logged. Native now follows that rule through `PreferencesCache`: it logs the raw sort and `view` once, only for preferences loaded after the screen opened, and never on a failed load (N22; `Cp02ThreadTest`). **This differs from the iOS port**, which logs once per thread screen (`9b950d0`). One of the two readings must be confirmed with an RN capture that shows `metric` logs |
| Ordered `post:view` (N6) | **Fixed in code** in `1862ea3`: rows at 40% visibility for 500 ms, reported in index order (RN `List` `viewabilityConfig`). `events.mjs --ordered` is not yet run |

`likerClout` and `postClout` are ignored because they depend on the follower count at tap time (seed race).

## States covered

| State | RN | Native | Notes |
|---|---|---|---|
| Home, thread, profile (light) | yes | yes (final build) | |
| Dark (system dark = dim) | yes (r2) | yes (`41c1923`) | |
| Font scale 2.0 | yes (r2) | yes (`41c1923`) | Fails on home and profile |
| Empty seed (`?users&follows`) | yes (home; profile r3) | yes (`41c1923`) | |
| After like and repost | yes (home; thread r3) | no valid final capture | See Verdict |
| Error (reseed after sign-in) | yes (r2) | yes (`41c1923`) | Home row invalid |
| Loading, load-more error, profile error | no | no | Covered by JVM and Compose UI tests |

New RN baselines this round: `parity/baseline/rn/android-interactions-r3-thread.png` (the flow waits for `postThreadItem-by-carla.test`) and `android-empty-r3-profile.png` (it waits for "No posts yet"). No existing baseline was overwritten.

## Tests (final code)

| Suite | Result |
|---|---|
| JVM (`testDebugUnitTest`, with `assembleDebug assembleRelease`), code `1862ea3` | 108 tests, 0 failures |
| Compose UI and Keystore (`:app:connectedDebugAndroidTest`, emulator-5554), code `1862ea3` | 10 tests, 0 failures |

## Differences that remain

None of these is accepted. Each needs a named owner.

| # | Difference | Screen | Evidence |
|---|---|---|---|
| 1 | Rows 18 px higher than RN at font scale 2.0 on home; profile baseline shows no posts | Home, profile | "Font scale". Home fixed in code (`29055d7`), not re-captured. Profile needs a new RN baseline |
| 2 | `thread:preferences:load` rule differs between the Android and iOS ports | Events | "Event diff". Needs an RN capture with `metric` logs |
| 3 | Relative time: native shows "now" where RN shows "16s" to "21s" | Home, thread, profile | Same tick semantics (P4, `MinuteTickerTest`). RN's tick starts when the JS app is ready, 16 to 21 s after the seed; the native one starts within 5 s. Masked |
| 4 | Discover notice text 1 px higher | Home | Ink rows 1999/2054 vs RN 2000/2055 |
| 5 | Header dropdown and share glyph shapes (Lucide) | All | Glyph masks |
| 6 | Code-drawn fallback avatars | All | Not masked; counted |
| 7 | Scroll indicator after navigation | Profile | RN shows a scrollbar at the right edge; Compose lists show none |
| 8 | Profile description links and mentions are not detected | Profile | RN runs `detectFacets`; the seeded bio has none |
| 9 | Follower and following counts vary with the seed race | Profile | "Seed race" |

## Accepted design differences

| Difference | Platform | RN behaviour | Native behaviour | Reason | Accepted by | Parity check |
|---|---|---|---|---|---|---|
| UI icons | Android and iOS | Central icon system glyphs | Lucide (ISC) glyphs | licensing: ASSETS.md sections 2 and 3 | awaiting owner acceptance | Glyph-box masks |
| Header logo | Android and iOS | Bluesky butterfly | Neutral placeholder mark | licensing: ASSETS.md sections 2 and 3 | awaiting owner acceptance | Masked as a glyph |
| Fallback avatar | Android and iOS | Bluesky default avatar artwork | Silhouette drawn in code | licensing: ASSETS.md sections 2 and 3 | awaiting owner acceptance | Not masked |

The proposed out-of-scope rows in `plan/slice-plan.md` also await owner acceptance.

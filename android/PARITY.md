# Parity report: Android native vs RN Android

Every number in this file was measured with the commands below. Nothing is estimated. Where a result is not valid evidence, the row says so and gives the reason.

Acceptance: **awaiting owner acceptance.**

## Verdict

- **Only three states have evidence: base, dark and interactions.** For those, both captures are from
  the same run and from a build that includes every fix they credit. **font scale 2.0, empty and
  error are withdrawn**: their captures on disk predate the fixes their rows credited, so the numbers
  that stood here could not be reproduced and are not restated. They need a new capture. The
  previous version of this file claimed "every state is measured in the same run as its RN capture".
  That was false for font scale 2.0, whose native capture predates its RN capture by a day.
- **No screen fails in the three measured states.** The `error home` failure that stood here is
  withdrawn with the rest of that state, not resolved.
- **Drift, above 1% (a person who did not write the port must sign off):** base home 1.92%, dark home
  1.86%, interactions home 1.93% and thread 1.24%.
- **Everything else passes masked.** No screen passes raw.
- **Events: no flow matches. Nothing in this file may claim event parity today.**
  - Readonly flow: 21 RN events against 19 native. Three real differences, all port defects. See
    "Event diff".
  - Interactions flow: this file claimed "MATCH, 16 of 16". That claim is false and contradicted by
    its own result file, which records `match: false`. Re-measured it reports a mismatch and exits 1.
  - Both earlier claims were written without checking the measurement beside them. The comparator
    that produced them was also defective, and is fixed (Pack `32666de`, `26d1a80`).
- **Noise floor: 0 px masked on every screen, for both apps.** The same app captured twice is pixel-identical after masks, so every number above is signal, not capture noise.
- **Every ratio was re-measured on 2026-10-21**, because the comparator was reading a number that had
  stopped meaning what it said. `diff.mjs` took its count from `magick compare -metric AE`, which on
  ImageMagick 7.1.2-32 is a sum of normalised per-channel magnitudes, not a pixel count: a 40x40 red
  rectangle over white (1600 differing pixels) reports `AE = 800`. The Pack now counts the pixels
  itself and records the ImageMagick build; see the Pack commit "Count differing pixels instead of
  reading magick's AE metric". The ratios here rose by roughly 0.1 to 0.3 percentage points. **No
  verdict changed** in the three measured states.

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
| base | home | 127,479 (3.49%) | 70,182 (1.92%) | drift | Yes |
| base | thread | 66,914 (1.83%) | 30,476 (0.83%) | pass | Yes |
| base | profile | 157,144 (4.30%) | 34,842 (0.95%) | pass | Yes. Both apps waited for "2 followers" and "2 following" |
| dark | home | 124,878 (3.42%) | 68,173 (1.86%) | drift | Yes |
| dark | thread | 66,128 (1.81%) | 31,465 (0.86%) | pass | Yes |
| dark | profile | 49,791 (1.36%) | 33,937 (0.93%) | pass | Yes |
| interactions | home | 130,156 (3.56%) | 70,615 (1.93%) | drift | Yes. Both apps wait for `FollowingEndOfFeed` before the screenshot |
| interactions | thread | 84,563 (2.31%) | 45,319 (1.24%) | drift | Yes |
| font scale 2.0 | home, thread, profile | — | — | **withdrawn** | No. The native capture on disk is dated 2026-09-15 18:31, before the fix `29055d7` (2026-09-15 20:21) that these rows credit. Its RN capture is dated 2026-09-16 14:53. They are not the same run |
| empty | home, profile | — | — | **withdrawn** | No. Both captures predate `b4054fb` (2026-09-16 04:07), the fix the `empty profile` row credits |
| error | home, thread | — | — | **withdrawn** | No. Both captures predate `b4054fb`, and the RN side is `rnbase`, a baseline from an older run |

### Fixed this round

| Screen | Before | After | Fix |
|---|---|---|---|
| interactions home | 162,854 (4.45%) | 70,615 (1.93%) | `b4054fb`: both apps wait for the end-of-feed state |

The `font scale 2.0 home` and `empty profile` rows that stood here are removed. Both cited an "after"
number from a capture that is not on disk: what is on disk for those states predates the fix being
credited. The fixes are in the code; the measurement that showed them working is not reproducible,
so it is not claimed.

## Noise floor

Two base runs of each app, same build, same batch, base masks:

| Screen | Native masked | RN masked |
|---|---|---|
| home | 0 (0.00%) | 0 (0.00%) |
| thread | 0 (0.00%) | 0 (0.00%) |
| profile | 0 (0.00%) | 0 (0.00%) |

Every masked noise floor is zero: two runs of the same app, same build, same batch, are
pixel-identical once the masks are applied. The thread floor used to read 0.10% native and 0.16% RN;
that was the anchor date line moving between the two runs, and the widened date band now covers it.
Every drift above is therefore the port, not the capture. Only masked noise is kept: the raw noise
numbers that stood here were measured by the old comparator and were not re-run.

## Error home

**This state is withdrawn and carries no number.** The captures on disk predate `b4054fb`,
and the RN side is a baseline from an older run, so the 4.91% that stood here is not reproducible.
The behaviour difference described below was observed on the device and is kept as an open
question; it needs a fresh capture before any ratio is quoted for it.

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

The RN side of the **interactions** comparison is derived from source: it runs against the expected
list in `events/rn-expected-interactions.jsonl`.

The RN side of the **readonly** comparison is **captured from the device**, not derived. This file
said otherwise until 2026-09-16, claiming the RN `.env` sets `EXPO_PUBLIC_LOG_DEBUG=session` and
hides `metric` logs. That is false for this flow, and the claim mattered: it was used to argue that a
difference might be an error in our own expectation rather than a defect. The evidence:
`run-round.sh:40-41` runs `rn-events.mjs` over the RN capture's own device log, and `rn-events.mjs:87`
parses `(metric) event:` out of it; the resulting `evidence/base/rn-events-all.jsonl` carries
telemetry this slice never ports (`feature:viewed`, `experiment:viewed`) and real un-seeded DIDs,
which no derived list could invent.

**Where the RN log is captured, it is ground truth, and a real difference is a port defect.**

**This file claimed a match on the interactions capture. The claim is false, and it was false
against its own evidence.** It printed:

```
MATCH (unordered): rn 16 event(s), native 16 event(s)
MATCH (ordered):   rn 16 event(s), native 16 event(s)
```

The result file written by that run, `parity/out/interactions/events-result.json`, records
`match: false` with `rn 17, native 14`. So the printed claim never matched the measurement beside
it, and this is independent of the comparator defect described below.

Re-measured on 2026-09-16 with the corrected comparator, same ignore list:

```
MISMATCH (ordered): rn 16 event(s), native 14 event(s)
  missing in native: router:navigate {} (rn line 4)
  missing in native: init {} (rn line 5)
  props differ:      post:view (rn line 7, native line 5): position rn=0 native=1
  exit 1
```

Two cautions on reading this. `parity/out/` is not committed, so the native log above may come from
a different round than the original claim, and the expected list has changed since (17 events then,
16 now). The differences are therefore **not established as port defects** the way the readonly
flow's are. What is established: **the interactions flow does not match, no run on record shows it
matching, and the claim must not be repeated until a fresh capture settles it.**

### The readonly flow does not match

Measured on 2026-09-16, same ignore list. Evidence: `parity/evidence/base/` and
`parity/evidence/dark/` (`events-result.json` with its two input logs).

The comparator that produced the first result was itself defective. Both results are kept, because
the difference between them is the point.

```
# events.mjs before the fix: compares the two logs by position
rn 21 event(s), native 19 event(s), match false
missing 2 | mismatches 3 | order mismatches 9          <- 14 lines, 11 of them false

# events.mjs after the fix: aligns the logs first
missing in native: home:feedDisplayed {...} (rn line 7)
missing in native: feed:endReached {...} (rn line 20)
order differs at 16: router:navigate moved earlier in native,
                     from rn position 16 (line 17) to native position 14 (line 15)
```

One event absent near the head shifted every later index and manufactured property and order
mismatches that did not exist. The fix aligns the two logs with a longest common subsequence before
classifying anything (Pack commits `32666de` and `26d1a80`). The corrected result is exactly the
hand alignment below, produced automatically.

Files: `parity/evidence/{base,dark}/events-result.json` is the original, defective output, kept as
the record. `events-result-aligned.json` beside it is the corrected output. Both states were
captured in separate runs and give **byte-identical** lines in both tools, so the delta is a stable
property of the flow, not capture noise.

The hand alignment that the corrected comparator now reproduces:

| RN line | Native line | Event |
|---|---|---|
| 1-6 | 1-6 | identical |
| **7** | — | `home:feedDisplayed`, no `reason` key. The port emits nothing |
| 8-15 | 7-14 | identical, one position apart |
| 16 | 16 | `home:feedDisplayed`, `reason: focus` |
| 17 | 15 | `router:navigate {from: PostThread}`. **Inverted against RN** |
| 18-19 | 17-18 | identical |
| **20** | — | `feed:endReached`, author feed. The port emits nothing |
| 21 | 19 | `post:view`, author feed, position 0. Identical |

So two events are genuinely absent and one pair is genuinely inverted. The causes:

| # | Verdict | Cause |
|---|---|---|
| 1 | **Port defect** | RN has two `home:feedDisplayed` call sites: `Home.tsx:178` (`reason: 'focus'`) and `Home.tsx:199` (`onPageSelected`, no `reason`), which ViewPager2 fires for the initial page. The port has one, `FeatureRoutes.kt:72`, with `reason` hardcoded to `focus`. This is a missing event, not an ordering fault |
| 1b | **Port defect** | On back-navigation RN logs the focus event **before** `router:navigate {from: PostThread}`; the port reverses them, because `AppShell.kt:296-304` logs from the shell effect, which runs before the destination's own effect. This mirrors the rule already recorded below |
| 2 | **No defect. This file was wrong** | The port's following-feed `post:view` positions are `0, 1, 2, 3` with `isReply` `true, false, false, false`, which equals RN exactly. `PostFeedList.kt:174-180` already implements RN's `getPostPosition` rule from `PostFeed.tsx:991-1011`. The reported mismatch was produced entirely by the offset from difference 1. The iOS position defect does not carry over |
| 3a | **No defect. This file was wrong** | The profile `post:view` **is** emitted, `native-events-cmp.jsonl:19`, equal to RN's line 21. It was reported missing only as the tail of a shorter log |
| 3b | **Port defect** | `feed:endReached` is genuinely absent. RN fires it on first layout while `isFetching` is still false, because RN auto-paginates from a `useEffect` (`util.ts:29,39`) after render. The port calls `autoPaginate` inline in the load coroutine (`FeedPager.kt:85`), setting `isFetching = true` before recomposition, so the gate at `ProfileScreen.kt:165` never opens. The call site exists (`ProfileViewModel.kt:221`); the flow cannot reach it |

Corrected count: **three port defects (1, 1b, 3b), and two claims this file made in error (2, 3a).**

The mechanism behind 3b is read from source; the absence of the event is measured. A runtime trace of
`FeedPagerState` where `ProfileScreen.kt:165` first evaluates would show which branch applies. That
changes the exact fix, not the verdict.

Fixing 1 or 3b changes the interactions flow as well. That flow does **not** match today, contrary
to what this file claimed, so it must be captured and measured again from scratch rather than
compared against the present expected list, which has drifted from the one the old claim used.

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
| 1 | Drift above 1%: base home 1.92%, dark home 1.86%, interactions home 1.93% and thread 1.24% | Home, thread | Results |
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

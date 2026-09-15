# Parity report: Android native vs RN Android

Every number in this file was measured with the commands below. Nothing is estimated. Where a result is not valid evidence, the row says so and gives the reason.

Acceptance: **awaiting owner acceptance.**

## Setup

| Item | Value |
|---|---|
| Device | emulator-5554, Pixel 10 Pro AVD, Android 16 (API 36.1), 1280x2856, density 480 (3 px = 1 dp) |
| RN app | `xyz.blueskyweb.app`, bluesky-social-app at `2bfb94d`, e2e build |
| Native app | `io.nativestarter.showcase.bluesky`, debug build of commit `4f0e80f` or later |
| RN baselines | `showcases/bluesky-native/parity/baseline/rn/android-*.png` |
| Seed | `setupServer.js` posts `?users&follows&posts&feeds&thread` to `localhost:1986` at the start of every flow, under `/tmp/bsky-seed.lock` |
| Diff tool | `android/parity/compare.sh` (ImageMagick `magick compare -metric AE -fuzz 5%`), no resizing |
| Total pixels per image | 3,655,680 |

## Commands

Run from `android/parity`. Build and install first: `ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon :app:installDebug` (under `/tmp/native-build.lock`).

1. Default screens:
   ```
   maestro --device emulator-5554 test -e PLATFORM=android capture-native.yml
   ./compare.sh 5%
   ```
2. Extra states for native, and RN re-captures under new names (existing baselines are never overwritten):
   ```
   ./capture-states.sh io.nativestarter.showcase.bluesky native-android "" interactions dark font2 empty error
   RUN=r2 ./capture-states.sh xyz.blueskyweb.app rn-android ../../parity/baseline/rn dark font2 error
   STATE=interactions ./compare.sh 5%
   STATE=empty ./compare.sh 5%
   STATE=dark RN_STATE=dark-r2 ./compare.sh 5%      # same for font2 and error
   ```
3. Noise floor: copy `shots/native-android-<screen>.png` to `noise/run1/android-<screen>.png`, capture again with the same build, then:
   ```
   RN_DIR=noise/run1 OUT_DIR=noise/diff ./compare.sh <fuzz>
   ```
4. Row offsets (text-band scan, name and text column x 210 to 600, grey below 110):
   ```
   python3 bandscan.py ../../parity/baseline/rn/android-home.png shots/native-android-home.png
   ```
   Method: a row is ink when a pixel in the window is darker than 110 on every channel; consecutive ink rows form a band; bands are paired in order.

## Masks

The same rectangles are painted on both images (`compare.sh`, coordinates from the RN default-state baselines).

| Mask set | Regions | Why |
|---|---|---|
| dynamic | Status bar (0,0 to 1280,150); relative-time column on each post row; thread anchor timestamp | Clock, "now" and "21s" change between runs |
| logo | Home header mark (570,165 to 710,280) | Accepted licensing difference |
| icons (second number only) | Header buttons, composer camera and image, post control rows, info icon, button chevron, FAB, bottom bar, and the code-drawn fallback avatars | Accepted licensing differences (Lucide glyphs, placeholder mark, drawn avatar) |

Limits of the masks, which the numbers inherit:

- An icon mask covers the whole control row, so the icon-masked number also hides the counts in that row. The first number keeps them.
- The masks are fixed rectangles. They do not follow content that moves (home rows below, and every rectangle in the font scale 2.0 captures).

## Noise floor

Two captures of the same native build, compared with each other: **0 px at fuzz 0%, 2% and 5% on home, thread and profile.** Measured during step A (before the CP-01 rewrite) with command 3. It was not repeated for the final build. The capture is deterministic because the seed, the account and the masked regions are the only inputs that change, so every pixel counted below is a real difference between the apps.

## Default screens

Fuzz 5%. Each cell is px and percent of the image.

| Build | Home: dynamic + logo | Home: + icons | Thread: dynamic + logo | Thread: + icons | Profile: dynamic + logo | Profile: + icons |
|---|---|---|---|---|---|---|
| First masked capture (before step A) | 237,128 (6.49%) | 187,021 (5.12%) | 74,616 (2.04%) | 47,877 (1.31%) | 71,977 (1.97%) | 54,708 (1.50%) |
| CP-00 commit (RN Android text line boxes, tab layout) | — | 126,714 (3.47%) | — | 0.89% | — | 35,661 (0.98%) |
| CP-01 commit | 180,661 (4.94%) | 126,714 (3.47%) | 61,479 (1.68%) | 33,057 (0.90%) | 58,642 (1.60%) | 35,661 (0.98%) |
| CP-02 thread before the hairline fix | 180,661 (4.94%) | 126,714 (3.47%) | 54,951 (1.50%) | 26,510 (0.73%) | 58,642 (1.60%) | 35,661 (0.98%) |
| Hairlines drawn full colour (rejected) | 188,312 (5.15%) | 131,301 (3.59%) | 51,831 (1.42%) | 24,286 (0.66%) | 59,487 (1.63%) | 36,361 (0.99%) |
| Hairlines at RN anti-aliased coverage (CP-02 commit) | 180,638 (4.94%) | 123,627 (3.38%) | 49,831 (1.36%) | 22,088 (0.60%) | 59,487 (1.63%) | 36,361 (0.99%) |
| **Final (CP-03, shared list and button)** | **180,638 (4.94%)** | **123,627 (3.38%)** | **50,681 (1.39%)** | **22,992 (0.63%)** | **57,661 (1.58%)** | **34,535 (0.94%)** |

"—" means that column was not recorded for that build; "0.89%" was recorded without its pixel count. Thread moved from 0.60% to 0.63% between two captures without a thread code change; the cause was not measured.

Rejected iterations, kept for the record:

- Font padding on every Compose text made each feed row taller and the offset grew down the page (home 9.24%).
- Tuned per-screen offsets on home were reverted (review A19). The remaining home offsets are listed below instead of hidden.
- `Hairline` used `Dp.Hairline`, which is zero-sized, so no border was drawn anywhere. Full-colour 2 px lines made home worse; RN draws a 1 px stroke on a pixel edge at half coverage (RN rows are (238, 241, 245), which is contrast_100 at 50% over white). The fix reserves 1 px and draws 2 px at 50% alpha.

### Home row offsets (final build)

Native top y minus RN top y, in px.

| Band | RN top y | Native top y | Offset |
|---|---|---|---|
| Tab labels | 343 | 343 | 0 |
| Composer "What's up?" | 503 | 503 | 0 |
| Post 1 name / text | 652 / 720 | 654 / 722 | +2 / +2 |
| Post 2 name / text | 898 / 966 | 901 / 969 | +3 / +3 |
| Post 3 name / text | 1169 / 1237 | 1173 / 1241 | +4 / +4 |
| Post 4 name / text | 1440 / 1508 | 1445 / 1513 | +5 / +5 |
| Post 5 name / text | 1710 / 1779 | 1715 / 1785 | +5 / +6 |
| Discover notice | 2000 / 2055 | 2000 / 2055 | 0 / 0 |
| End-of-feed text | 2283 / 2353 | 2274 / 2346 | −9 / −7 |
| "Find accounts" button | 2458 | 2448 | −10 |

Each post row is about 1 px taller than RN (the drift grows by 1 px per row). The end-of-feed block sits 7 to 10 px higher. Home stays above the 2% target with icons masked; most of the remaining pixels are text edges shifted by these offsets.

## Extra states

Native: final build. RN: the file named in the table. Fuzz 5%.

| State | Screen | RN baseline | Dynamic + logo | + icons | Valid? |
|---|---|---|---|---|---|
| After like and repost | Home | `android-interactions-home` | 182,277 (4.99%) | 123,627 (3.38%) | Yes. Both show the green repost count and pink heart. |
| After like and repost | Thread | `android-interactions-thread` | 83,443 (2.28%) | 59,317 (1.62%) | No. The RN baseline was taken before the reply loaded (the old flow waited for "Thread reply", which the home feed also shows). Both show the "1 repost · 1 like" stats row. |
| Dark (system dark = dim) | Home | `android-dark-r2-home` | 202,047 (5.53%) | 144,777 (3.96%) | Yes. |
| Dark | Thread | `android-dark-r2-thread` | 55,511 (1.52%) | 27,851 (0.76%) | Yes. |
| Dark | Profile | `android-dark-r2-profile` | 59,652 (1.63%) | 36,606 (1.00%) | Yes. |
| Font scale 2.0 | Home | `android-font2-r2-home` | 311,773 (8.53%) | 242,721 (6.64%) | Partly. The masks do not follow the larger layout. |
| Font scale 2.0 | Thread | `android-font2-r2-thread` | 175,837 (4.81%) | 138,398 (3.79%) | Partly, as above. "Everybody can reply" wraps to its own line in both apps. |
| Font scale 2.0 | Profile | `android-font2-r2-profile` | 184,748 (5.05%) | 143,054 (3.91%) | Partly, as above. |
| Empty feed | Home | `android-empty-home` | 212,362 (5.81%) | 129,607 (3.55%) | Yes. Both show the composer, the Discover notice and the end-of-feed block. |
| Empty feed | Profile | `android-empty-profile` | 82,688 (2.26%) | 62,604 (1.71%) | No. The RN baseline shows no Posts content yet and "2 following"; native shows "No posts yet", "Write a post" and "1 following". The capture moment and the seed state differ. |
| Error (session and posts reset) | Home | `android-error-r2-home` | 268,979 (7.36%) | 209,003 (5.72%) | Not inspected image by image. |
| Error | Thread | `android-error-r2-thread` | 39,542 (1.08%) | 25,788 (0.71%) | Yes. Both show "Error loading post", the message and Retry. |

Not captured: loading placeholders, load-more error and the profile error screen (they need a slow or failing server, which the shared mock server must not be changed for). These states are covered by the JVM tests and the Compose UI tests instead (`Cp01FeedLogicTest`, `Cp02ThreadTest`, `Cp03ProfileTest`, `ScreenTests`).

## Event diff

RN events could not be captured live without changing upstream: the RN `.env` sets `EXPO_PUBLIC_LOG_DEBUG=session`, and logcat had 0 `(metric)` lines during the RN flows. The diff is therefore static: the native log is checked name by name and prop by prop against the RN definitions in `src/analytics/metrics/types.ts`:

```
python3 event_diff.py ../../../bluesky-social-app/src/analytics/metrics/types.ts events.jsonl
```
 A prop typed `| undefined` counts as optional, because JSON drops undefined values and the native log drops nulls.

Native log from one interactions run (sign in, like, repost, open the thread), pulled with `adb shell run-as io.nativestarter.showcase.bluesky cat files/events.jsonl`: 14 lines.

| Event | RN props (required; optional) | Native count | Native props | Result |
|---|---|---|---|---|
| `init` | initMs; – | 1 | initMs | Match |
| `router:navigate` | –; from | 2 | from (second event) | Match |
| `account:loggedIn` | logContext, withPassword; – | 1 | logContext, withPassword | Match |
| `home:feedDisplayed` | feedType, feedUrl, index; reason | 1 | all four | Match |
| `post:view` | authorDid, isReply, logContext, uri; feedDescriptor, position | 6 (4 FeedItem, 1 Post, 1 PostThreadItem) | as RN per context | Match |
| `post:like` | authorDid, logContext, uri; doesLikerFollowPoster, doesPosterFollowLiker, feedDescriptor, likerClout, postClout | 1 | all | Match |
| `post:repost` | authorDid, logContext, uri; feedDescriptor | 1 | all | Match |
| `post:clickthroughItem` | authorDid, logContext, uri; feedDescriptor, position | 1 | authorDid, feedDescriptor, logContext, uri | Match |

**Mismatches: 0** (no unknown event names, no extra props, no missing required props).

Not logged in this run, and covered by JVM tests with exact props instead: `feed:refresh` (also logged in an earlier native run, match), `feed:endReached`, `composerPrompt:press`, `nav:click`, `splash:signInPressed`, `post:clickthroughAuthor`, `post:clickReply`, `post:unlike`, `post:unrepost`, `thread:click:showOtherReplies`.

## Known differences (open)

| Difference | Screen | Evidence |
|---|---|---|
| Feed rows 1 px taller per row; end-of-feed block 7 to 10 px higher | Home | Row offsets table |
| Anchor author block alignment at font scale 2.0 | Thread | `diff/states/diff-font2-thread-*.png` |
| Header dropdown icon glyph and weight | Thread | Accepted licensing difference below; not masked in the header on thread |
| Profile description links and mentions are not detected | Profile | RN runs `detectFacets` client-side; the seeded bio has none |
| Scroll indicator after navigation | Profile | RN shows a scrollbar at the right edge; Compose lists show none |

## Accepted design differences

| Difference | Platform | RN behaviour | Native behaviour | Reason | Accepted by | Parity check |
|---|---|---|---|---|---|---|
| UI icons | Android and iOS | Central icon system glyphs from `assets/icons/` | Lucide (ISC) glyphs, closest match per icon | licensing: upstream icons and marks are not licensed for redistribution (ASSETS.md sections 2 and 3) | Coordinator instruction, 2026-09-15 | Numbers reported with and without icon regions |
| Header logo | Android and iOS | Bluesky butterfly logomark | Neutral placeholder mark | licensing: upstream icons and marks are not licensed for redistribution (ASSETS.md sections 2 and 3) | Coordinator instruction, 2026-09-15 | Logo region masked in both numbers |
| Fallback avatar | Android and iOS | Bluesky default avatar artwork | Silhouette drawn in code | licensing: upstream icons and marks are not licensed for redistribution (ASSETS.md sections 2 and 3) | Coordinator instruction, 2026-09-15 | Avatar regions masked in the second number only |

Proposed differences, not accepted: none. Owner acceptance of this report is pending.

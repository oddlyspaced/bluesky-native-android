# Parity report: Android native vs RN Android

Every number in this file was measured with the commands below. Nothing is estimated.

## Setup

| Item | Value |
|---|---|
| Device | emulator-5554, Pixel 10 Pro AVD, Android 16 (API 36.1), 1280x2856, density 480 |
| RN baseline | `showcases/bluesky-native/parity/baseline/rn/android-{home,thread,profile}.png`, same device, same seed |
| Native capture | `android/parity/capture-native.yml`, same steps as `bsky-parity/flows/capture-baseline.yml`, appId `io.nativestarter.showcase.bluesky` |
| Seed | `setupServer.js` posts `?users&follows&posts&feeds&thread` to `localhost:1986` at the start of every capture |
| Diff tool | `android/parity/compare.sh` (ImageMagick `magick compare -metric AE -fuzz <f>`) |
| Screen size | RN and native captures are both 1280x2856, so no resizing is applied |

## Commands

1. Build and install:
   ```
   cd android
   ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon :app:installDebug
   ```
2. Capture (reseeds the server):
   ```
   cd android/parity
   maestro --device emulator-5554 test -e PLATFORM=android capture-native.yml
   ```
3. Compare against the RN baseline:
   ```
   ./compare.sh 5%
   ```
4. Noise floor (two runs of the same build): copy `shots/native-android-<screen>.png` to `noise/run1/android-<screen>.png`, capture again, then run:
   ```
   RN_DIR=noise/run1 OUT_DIR=noise/diff ./compare.sh <fuzz>
   ```

## Masks

The masks are the same rectangles on both images. They are defined in `compare.sh`, with coordinates taken from the RN baselines.

| Mask set | Regions | Why |
|---|---|---|
| dynamic | Status bar (0,0 to 1280,150); relative time column on each post row; thread anchor timestamp | Clock and "now"/"10s" change between runs |
| logo | Home header mark (570,165 to 710,280) | Accepted licensing difference |
| icons (second number only) | Header buttons, composer camera and image, every post control row, info icon, button chevron, FAB glyph, bottom bar | Accepted licensing difference (Lucide instead of upstream glyphs) |

Each mask covers a whole control row, so the icon-masked number also hides the post counts ("1" reply) in those rows. The first number keeps them.

## Results

Total pixels per screen: 3,655,680. Fuzz: 5%.

| Iteration | Change | Home: dynamic + logo | Home: + icons | Thread: dynamic + logo | Thread: + icons | Profile: dynamic + logo | Profile: + icons |
|---|---|---|---|---|---|---|---|
| 0 | First native capture, no masks (raw) | 254,401 px (6.96%) | n/a | 100,776 px (2.76%) | n/a | 154,478 px (4.23%) | n/a |
| 1 | PostMeta: handle no longer truncates, time right-aligned; thread anchor paddings; profile button padding and header bottom | 237,128 px (6.49%) | 187,021 px (5.12%) | 74,616 px (2.04%) | 47,877 px (1.31%) | 71,977 px (1.97%) | 54,708 px (1.50%) |
| 2 | Font padding on all text (rejected) | 394,630 px (10.79%) | 337,863 px (9.24%) | 91,579 px (2.51%) | 63,032 px (1.72%) | 113,046 px (3.09%) | 96,593 px (2.64%) |
| 3 | Font padding reverted; tab indicator drawn without changing tab width; screen header gutters 16dp | 237,128 px (6.49%) | 187,021 px (5.12%) | 73,185 px (2.00%) | 46,499 px (1.27%) | 64,266 px (1.76%) | 46,997 px (1.29%) |

Iteration 0 is not directly comparable: it has no masks, and PostMeta was still wrong.

Iteration 2 was rejected. RN Android `Text` keeps font padding on, but enabling it on every Compose text made each feed row taller, and the offset grew down the page (see the home diff image of that run).

### Home row offsets (iteration 3)

Measured with a text-band scan in the name and text column (x 210 to 600, grey level below 110). The value is native minus RN, in px (3 px = 1 dp):

| Row | RN top y | Native top y | Offset |
|---|---|---|---|
| Tab labels | 343 | 340 | −3 |
| Composer "What's up?" | 503 | 507 | +4 |
| Post 1 name | 652 | 660 | +8 |
| Post 2 name | 898 | 905 | +7 |
| Post 3 name | 1169 | 1174 | +5 |
| Post 4 name | 1440 | 1443 | +3 |
| Post 5 name | 1710 | 1711 | +1 |
| Discover notice | 2000 | 1992 | −8 |
| End-of-feed text | 2283 | 2263 | −20 |
| "Find accounts" button | 2458 | 2433 | −25 |

A follow-up change to reduce these offsets is built and installed. It adjusts the home header height, composer padding, notice padding, end-of-feed spacing and button line height. It is not measured yet (see Status).

## Noise floor

Not measured yet. The second run of the same build failed at sign-in because the PDS on `localhost:3000` went down during the flow (the app showed "Sign in failed: unexpected end of stream"). `compare.sh` then compared run 1 with itself and printed 0 px. That result is invalid and is not used.

## Status

| Item | State |
|---|---|
| Home, thread and profile flows | Pass on the native app with the shared Maestro flow |
| Iteration 4 (home spacing) | Built and installed, not captured: PDS down |
| Noise floor | Not measured: PDS down |

## Known differences

| Difference | Screen | RN | Native | Status |
|---|---|---|---|---|
| Home rows | Home | See offsets table | Up to 25 px off at the bottom of the page | Open, iteration 4 pending |
| Default avatar shape | All | Bluesky default avatar artwork | Generic silhouette (disc, head, shoulders) | Accepted (licensing) |
| Home icon when selected | Bottom bar | Filled house with door cut-out | Lucide `house` filled, no cut-out | Accepted (licensing) |
| Scroll indicator | Profile | RN shows a scrollbar at the right edge after navigation | Compose lists show no scrollbar | Open, minor |

## Accepted design differences

| Difference | Platform | RN behaviour | Native behaviour | Reason | Accepted by | Parity check |
|---|---|---|---|---|---|---|
| UI icons | Android and iOS | Central icon system glyphs from `assets/icons/` | Lucide (ISC) glyphs, closest match per icon | licensing: upstream icons and marks are not licensed for redistribution (ASSETS.md sections 2 and 3) | Coordinator instruction, 2026-09-15 | Numbers reported with and without icon regions |
| Header logo | Android and iOS | Bluesky butterfly logomark | Neutral placeholder mark (rounded square, brand blue) | licensing: upstream icons and marks are not licensed for redistribution (ASSETS.md sections 2 and 3) | Coordinator instruction, 2026-09-15 | Logo region masked |
| Fallback avatar | Android and iOS | Bluesky default avatar artwork | Generic silhouette drawn in code | licensing: upstream icons and marks are not licensed for redistribution (ASSETS.md sections 2 and 3) | Coordinator instruction, 2026-09-15 | Not masked; counted in both numbers |

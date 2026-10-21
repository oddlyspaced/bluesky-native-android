# Bluesky, ported to Jetpack Compose

Three screens of the [Bluesky](https://github.com/bluesky-social/social-app) React Native app —
home feed, post thread and profile — rebuilt in Jetpack Compose, then measured against the running
React Native app on the same device, the same build and the same seeded data.

Built with the [Nativestarter](https://nativestarter.io) Migration Pack, one checkpoint at a time.
The iOS half of the same slice is in
[bluesky-native-ios](https://github.com/oddlyspaced/bluesky-native-ios).

Not affiliated with, endorsed by, or officially connected to Bluesky Social PBC. See `NOTICE.md`.

## What was measured

Six states, fifteen screens, captured 2026-10-22. Both apps are captured in the same batch, on the
same build, against the same local server with fake accounts. Masked ratio is differing pixels after
masks, over 3,655,680 pixels per screen. Pass is at or below 1%, drift is 1% to 2% and needs a human
signature, above 2% is a fail.

**6 pass, 5 drift, 4 fail.**

| State | Screen | Raw | Masked | Status |
|---|---|---|---|---|
| base | home | 3.92% | **2.36%** | fail |
| base | thread | 1.72% | 0.83% | pass |
| base | profile | 4.29% | 0.95% | pass |
| dark | home | 3.42% | 1.86% | drift |
| dark | thread | 1.81% | 0.86% | pass |
| dark | profile | 1.36% | 0.93% | pass |
| font scale 2.0 | home | 4.24% | **2.32%** | fail |
| font scale 2.0 | thread | 2.90% | 1.71% | drift |
| font scale 2.0 | profile | 5.28% | 1.66% | drift |
| empty | home | 2.77% | **2.10%** | fail |
| empty | profile | 4.34% | 0.94% | pass |
| after like and repost | home | 3.56% | 1.93% | drift |
| after like and repost | thread | 2.31% | 1.24% | drift |
| error | home | 6.71% | **5.24%** | fail |
| error | thread | 1.01% | 0.76% | pass |

**Every noise floor is 0 px**, on all six states, for both apps. The same app captured twice is
pixel-identical after masks, so each number above is the port and not the capture.

## Events match

**The read-only flow matches: 21 React Native events against 21 native, in order**, reproduced on
three consecutive runs. It did not match before — it ran 21 against 19, with two events missing and
one out of order. All three were defects in this port, and each was found by reading React Native's
own device log rather than an expectation of it:

| Defect | Cause |
|---|---|
| The second `home:feedDisplayed` was never sent | RN logs it from two call sites: `useFocusEffect` with `reason: "focus"`, and the pager's `onPageSelected` with no `reason`. The port had only the first. |
| `router:navigate` preceded the destination's events on a pop | RN is not symmetric. React Navigation reports state after it commits, so a push leads with the navigate event and a pop trails it. |
| `feed:endReached` was never sent | The pager filled short pages inside its own fetch, consuming the next page before the list had laid out once. RN fills from an effect after render, so the layout pass reports the end while a next page still exists. |

`feed:endReached` is timing-dependent in React Native too: over four runs RN sent it in three, and
the port sent it in the same three.

## What this does not claim

**Four screens fail and none is signed off.** `error home` at 5.24% is a real behaviour difference:
after a reseed the port refetches the feed and shows the load-more retry row, where React Native
keeps the end-of-feed state. The other three are the home screen between 2.10% and 2.36%, just over
the gate, with two causes: the placeholder mark that stands in for Bluesky's logo, and React
Native's scroll indicator, which Android draws for a ScrollView and a Compose list does not.

**That scroll indicator is deliberately not masked.** It is worth 0.40 to 0.44% of the home screen,
enough to turn all three of those failures into drift. A mask for it was written and then removed,
because `make_masks.py` refused it: on the profile screen both apps draw real content at the same
right edge, and the mask would have hidden it. The refusal is the check working.

**Nothing is signed off.** Every drift needs a written acceptance from someone who did not write the
port. `plan/owner-signoff.md` is the form, and it is empty.

## The tooling was wrong more often than the code

Worth saying plainly, because it is the reason to trust the numbers above rather than a reason not
to. Over this port the measuring tools were caught being wrong three times, each time by checking
the measurement instead of the result:

- `magick compare -metric AE` stopped being a pixel count in ImageMagick 7.1.2-32. A 40x40 red
  rectangle over white — 1600 differing pixels — reported 800, and in `rgb(254,254,254)` reported
  4.71. Every ratio derived from it was wrong by a factor that depended on which channels differed.
- Three states' numbers came from captures that predated the fixes they credited. Diffing them gave
  6.99% where the fix was supposed to have produced 1.68%.
- The event comparator aligned logs by index, so one missing event near the head cascaded: it
  reported 14 difference lines where 3 were real.

Each is in the commit history with the measurement that proved it.

## Reproduce the measurement

The capture and diff scripts under `android/parity/` call the Migration Pack, which is a commercial
product and is not in this repository. The screenshots, event logs and result files it produced
**are** committed under `android/parity/evidence/`, and the React Native baselines it was measured
against are in `parity/baseline/rn/`, so every number above can be checked against the images
without buying anything.

To run the apps:

1. Start the upstream e2e dev network from `bluesky-social/social-app` at commit `2bfb94d`:
   `pnpm e2e:mock-server`. Keep ports 2584 and 3000-3003 free; the AppView DID depends on them.
2. Seed: `curl -X POST "http://localhost:1986/?users&follows&posts&feeds&thread"`.
3. Forward the ports: `adb reverse tcp:3000 tcp:3000` and `adb reverse tcp:8081 tcp:8081`.
4. Build and install: `./gradlew :app:installDebug`.

See `android/README.md` for the module layout and `android/PARITY.md` for the full evidence.

## Scope

The upstream app has 78 screens. This port covers the foundation — design tokens, XRPC client,
session, tab shell — and three screens. Tabs outside the slice show a labelled placeholder.
`plan/full-app-plan.md` is the 127-checkpoint plan for the whole app; `plan/slice-plan.md` is the
four checkpoints this showcase covers.

## Licence

MIT, see `LICENSE`. Portions derive from bluesky-social/social-app, also MIT. Bluesky's logo, icon
set and other marks are **not** in this repository and are not covered by that licence; `NOTICE.md`
lists each one and what replaced it.

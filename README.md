# Bluesky, ported to Jetpack Compose

Three screens of the [Bluesky](https://github.com/bluesky-social/social-app) React Native app —
home feed, post thread and profile — rebuilt in Jetpack Compose, then measured against the running
React Native app on the same device, the same build and the same seeded data.

Built with the [Nativestarter](https://nativestarter.io) Migration Pack, one checkpoint at a time.
The iOS half of the same slice is in
[bluesky-native-ios](https://github.com/oddlyspaced/bluesky-native-ios).

Not affiliated with, endorsed by, or officially connected to Bluesky Social PBC. See `NOTICE.md`.

## What was measured

Captured 2026-09-16, re-measured 2026-10-21 after the comparator was corrected. Both apps are
captured in the same batch, on the same build, against the same local server with fake accounts.
Masked ratio is differing pixels after masks, over 3,655,680 pixels per screen. Pass is at or below
1%, drift is 1% to 2% and needs a human signature, above 2% is a fail.

| State | Screen | Raw | Masked | Status |
|---|---|---|---|---|
| base | home | 3.49% | **1.92%** | drift |
| base | thread | 1.83% | 0.83% | pass |
| base | profile | 4.30% | 0.95% | pass |
| dark | home | 3.42% | **1.86%** | drift |
| dark | thread | 1.81% | 0.86% | pass |
| dark | profile | 1.36% | 0.93% | pass |
| after like and repost | home | 3.56% | **1.93%** | drift |
| after like and repost | thread | 2.31% | **1.24%** | drift |

**The noise floor is 0.00% on every screen.** The same app captured twice, same build, same batch,
is pixel-identical after masks. That is what makes the numbers above mean anything: a difference is
the port, not the capture.

## What this does not claim

Three states — font scale 2.0, empty seed, and the error state — **are withdrawn and carry no
number**. Their captures on disk predate the fixes those numbers credited, so they were not
reproducible. They need a fresh capture. `android/PARITY.md` records exactly why.

**No flow matches on events.** The read-only flow logs 21 React Native events against 19 native.
All three differences are real defects in this port, not comparison artefacts, and each is traced
to a file and line in `android/PARITY.md`.

**Nothing is signed off.** Every drift above needs a written acceptance from someone who did not
write the port. `plan/owner-signoff.md` is the form, and it is empty.

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

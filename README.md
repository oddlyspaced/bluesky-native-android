# Bluesky, ported to Jetpack Compose

Three screens of the [Bluesky](https://github.com/bluesky-social/social-app) React Native app —
home feed, post thread and profile — rebuilt in Jetpack Compose, then measured against the running
React Native app on the same device, the same build and the same seeded data.

Built with the [Nativestarter](https://nativestarter.io) Migration Pack, one checkpoint at a time.
The iOS half of the same slice is in
[bluesky-native-ios](https://github.com/oddlyspaced/bluesky-native-ios).

Not affiliated with, endorsed by, or officially connected to Bluesky Social PBC. See `NOTICE.md`.

## What was measured

Six states, fifteen screens. Both apps are captured in the same batch, on the same build, against
the same local server with fake accounts. Masked ratio is differing pixels after masks, over
3,655,680 pixels per screen. Pass is at or below 1%, drift is 1% to 2% and needs a human signature,
above 2% is a fail.

**6 pass, 5 drift, 4 fail.**

| State | Screen | Raw | Masked | Status |
|---|---|---|---|---|
| base | home | 3.92% | **2.36%** | fail |
| base | thread | 1.74% | 0.83% | pass |
| base | profile | 4.30% | 0.95% | pass |
| dark | home | 3.87% | **2.30%** | fail |
| dark | thread | 1.80% | 0.86% | pass |
| dark | profile | 1.39% | 0.93% | pass |
| font scale 2.0 | home | 4.24% | **2.32%** | fail |
| font scale 2.0 | thread | 2.91% | 1.71% | drift |
| font scale 2.0 | profile | 5.28% | 1.66% | drift |
| empty | home | 2.78% | **2.10%** | fail |
| empty | profile | 4.34% | 0.94% | pass |
| after like and repost | home | 3.56% | 1.93% | drift |
| after like and repost | thread | 2.28% | 1.24% | drift |
| error | home | 3.02% | 1.56% | drift |
| error | thread | 1.00% | 0.76% | pass |

**The noise floor inside a round is 0 px**, on every state, for both apps: the same app captured
twice in one batch is pixel-identical after masks.

**Between rounds, the same build moves by up to 0.44 points.** That is not in the figure above,
which only measures one batch. Measured on `dark home`: 1.86% in one round and 2.30% in another,
with nothing changed in between. All four remaining failures sit between 2.10% and 2.36% against a 2%
gate, which is inside that spread, so those four verdicts are worth no more than the spread.

## Events match

**Every flow matches, in order**: base 21 React Native events against 21 native, dark 21, font
scale 2.0 21, after like and repost 17, empty 9, and the error flow 20 against 20. The read-only
flow did not match before — it ran 21 against 19, with two events missing and one out of order. All three were defects in this port, and each was found by reading React Native's
own device log rather than an expectation of it:

| Defect | Cause |
|---|---|
| The second `home:feedDisplayed` was never sent | RN logs it from two call sites: `useFocusEffect` with `reason: "focus"`, and the pager's `onPageSelected` with no `reason`. The port had only the first. |
| `router:navigate` preceded the destination's events on a pop | RN is not symmetric. React Navigation reports state after it commits, so a push leads with the navigate event and a pop trails it. |
| `feed:endReached` was never sent | The pager filled short pages inside its own fetch, consuming the next page before the list had laid out once. RN fills from an effect after render, so the layout pass reports the end while a next page still exists. |

The error flow was the last to match. It ran 64 native events against React Native's 20, because
the port held `hasNextPage` true after a failed refresh and fired `feed:endReached` 44 times; the
fix below closed it.

`feed:endReached` on the author feed is timing-dependent in React Native too: over four runs RN
sent it in three, and the port sent it in the same three. One round here came back 20 against 21
for that reason, and its re-run matched at 21 against 21 with identical ratios. That is the kind of
result worth re-running rather than reporting once.

## What this does not claim

**Four screens fail and none is signed off.** None of the four is a behaviour difference. The one
that was has been fixed.

**`error home` was the real defect, and it is closed: 4.87% to 1.56%.** After a reseed React Native
kept its end-of-feed block and this port drew blank space where it belongs, one region of 83,049
pixels. The port also logged 44 `feed:endReached` events against React Native's none.

Three explanations for it were wrong before the right one, which is worth more than the fix. The
first said TanStack reports an error only when a query has no data;
`parity/probes/tanstack-failed-refetch.mjs`, run against the real `@tanstack/query-core` React
Native builds on, disproves it. The second treated that probe's answer as React Native's final
state, when it lasts 24 milliseconds. The third reverted the correct fix because
`truncateAndInvalidate` cuts the cache to page 1 before invalidating, so the truncation looked
permanent.

It is not permanent. A cache write is not a successful fetch, so a failed refetch re-emits the
untruncated data. Instrumenting React Native on the device and capturing the flow again showed it
plainly, 24 milliseconds after the soft-reset:

```
hasNextPage=true   isError=true   pages=1 items=4
hasNextPage=false  isError=false  pages=2 items=4
```

Both pages back, no error, end-of-feed drawn — and `items=4`, where the feed held 5 before the
refresh, so the fallback marker does not come back. Restoring it too was tried, drew a row React
Native does not draw, and took the screen to 7.59%; that capture is why it is left out. The
instrumented React Native capture was pixel-identical to the committed baseline, so nothing about
the comparison moved.

**The four that remain are the same picture in the same place.** `base`, `dark`, `font scale 2.0`
and `empty` home, between 2.10% and 2.36%. `parity/composition.py` grades every differing pixel by how
far apart the two apps are and groups the strong ones into regions. On all four, only a quarter of
the difference is strong, spread across 739 to 917 regions whose largest is about 1,000 pixels,
three hundredths of one percent of the screen. A whole-image shift search over plus or minus three
pixels finds its minimum at no offset at all, and the text bands pair one to one within a pixel.
What is left is the scroll indicator below, the fallback avatars, and glyph-edge rasterisation.

An earlier version of this section named "the placeholder mark and the scroll indicator" as the two
causes. The placeholder mark is masked, so it cannot be one; the scroll indicator is 0.44%. That
was an estimate, and the paragraph above replaces it with a measurement.

**That scroll indicator is deliberately not masked.** It is worth 0.39 to 0.44% of the home screen,
enough to turn all four remaining failures into drift. A mask for it was written and then removed, because
`make_masks.py` refused it: on the profile screen both apps draw real content at the same right
edge, and the mask would have hidden it. The refusal is the check working.

**Nothing is signed off.** Every drift needs a written acceptance from someone who did not write the
port. `plan/owner-signoff.md` is the form, and it is empty.

## A scroll indicator, built and then removed

React Native renders its feeds in a `ScrollView`, so Android draws it a 4 dp scrollbar that a
Compose list does not get. A matching thumb was written for this port. Its position and width were
exact, measured at 12 px from x 1268 at 480 dpi. Its length was not: a `LazyColumn` cannot know the
height of items it has not composed, while a `ScrollView` knows its content height, so the thumb
had to be sized from an average of what was on screen.

| Home screen | Without | With |
|---|---|---|
| base | 2.36% | 2.04% |
| after like and repost | 1.93% | 2.24% |
| font scale 2.0 | 2.32% | 2.48% |
| empty | 2.10% | 2.10% |

It helped the screen it was tuned against and hurt two others, because the estimate is
state-sensitive. Net it was worse, so it was removed instead of being kept as an improvement the
measurement did not support. The missing indicator stays listed as a difference.

`parity/scroll-thumb.py` measures it rather than estimating it. On base and dark home the thumb is
12 px at x 1268-1279, y 429-1755: 15,924 pixels, 0.44% of the screen, and the largest single
contiguous block of differing pixels there. React Native draws it only on the three states whose
flow scrolls, and on none of the eight iOS states. Its length cannot be derived, which is the whole
problem: the thumb sits at offset 0 with length 1327 in a track of about 2212, so React Native's
`ScrollView` believes its content is about 3687 pixels tall while the end-of-feed block is already
on screen. A `LazyColumn` estimates content height from average item size and cannot reproduce
that number.

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

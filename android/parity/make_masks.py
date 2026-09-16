#!/usr/bin/env python3
"""Builds check-parity masks.json files for Android from declared bands, measured on both images.

Port of ios/parity/make_masks.py for emulator-5554 (1280x2856). Masks hide only what the parity gate allows
(migration-pack/skills/check-parity/references/masking.md):

  status   the status bar (clock, battery, signal)
  glyphs   icon glyph boxes: ink clusters in a band that are wide enough to be a glyph AND icon-shaped.
           Post counts and text next to an icon stay visible: a count glyph is narrower than `minw`, and a
           cluster is split where the gap is more than GAP px. Clusters that start at x >= `right_x` use
           `minw_right` (the "more" ellipsis is narrow).
           Reason: licence substitution (Lucide glyphs for the Central icon set, neutral mark, drawn avatar).
  tail     the last `count` ink clusters of a line: the relative time ("now", "16s"), which depends on capture time
  rect     an explicit rectangle, with the reason in the spec

Clusters are measured on the RN image and on the native image and merged, so one mask covers both. Ink is a pixel
whose luma differs from the row background by more than 40. The background is sampled at `bg_x` (default 6).

Two rules make the tool fail instead of hiding a real difference:

  * `glyphs` (review A3, A14). A cluster that is wide enough to be masked must also be icon-shaped: its height is
    within [`minh`, `maxh`] and its aspect ratio within [`ar_min`, `ar_max`]. A cluster that would be masked but
    is not icon-shaped is text, so the band is wrong for this state and the run fails, naming the measured box.
    A cluster that is not wide enough is not a glyph: it stays visible, which is the point of `minw`.
    Real Lucide glyphs on these screens measure 36-90 px wide and 40-88 px tall, aspect 0.85-1.95. Text ink in the
    same bands is shorter: the end-of-feed copy on the error screen measures 59x32, aspect 1.84.
  * `rect` (review A10). The declared rectangle is checked against the ink in both images: ink that touches an
    edge of the rectangle means the element is larger than the mask assumes, so a layout shift could be hidden.
    The run fails and reports the measured ink box on each image.

Usage: make_masks.py <spec.json> <state> <rn-dir> <native-dir> <out-masks.json>
Prints the masked fraction per screen; the gate is at most 15% per screen.
"""
import json
import sys

from PIL import Image

GAP = 6
PAD = 4
STATUS_H = 150

# Defaults for the "icon-shaped" test. Measured on the RN Android baselines (see the module docstring).
MIN_GLYPH_H = 36
MAX_GLYPH_H = 200
MIN_GLYPH_AR = 0.5
MAX_GLYPH_AR = 2.2


class BandError(SystemExit):
    pass


def load(path):
    img = Image.open(path).convert("L")
    return img.size, img.load()


def ink_columns(px, box, bg_x):
    x0, y0, x1, y1 = box
    cols = {}
    for y in range(y0, y1):
        bg = px[bg_x, y]
        for x in range(x0, x1):
            if abs(px[x, y] - bg) > 40:
                lo, hi = cols.get(x, (y, y))
                cols[x] = (min(lo, y), max(hi, y))
    return cols


def clusters(cols, gap=GAP):
    out = []
    for x in sorted(cols):
        lo, hi = cols[x]
        if out and x - out[-1][1] <= gap:
            c = out[-1]
            out[-1] = [c[0], x, min(c[2], lo), max(c[3], hi)]
        else:
            out.append([x, x, lo, hi])
    return out


def merge(a, b, gap=GAP):
    out = []
    for c in sorted(a + b):
        if out and c[0] <= out[-1][1] + gap:
            p = out[-1]
            out[-1] = [p[0], max(p[1], c[1]), min(p[2], c[2]), max(p[3], c[3])]
        else:
            out.append(list(c))
    return out


def rect(size, x0, y0, x1, y1, reason, pad=PAD):
    W, H = size
    x0, y0 = max(0, x0 - pad), max(0, y0 - pad)
    x1, y1 = min(W, x1 + pad + 1), min(H, y1 + pad + 1)
    return {"x": round(x0 / W, 5), "y": round(y0 / H, 5), "w": round((x1 - x0) / W, 5),
            "h": round((y1 - y0) / H, 5), "reason": reason, "px": [x0, y0, x1, y1]}


def ink_bounds(px, box, bg_x):
    """Bounding box of the ink inside `box`, or None when the box holds no ink."""
    cols = ink_columns(px, box, bg_x)
    if not cols:
        return None
    xs = sorted(cols)
    return [xs[0], min(v[0] for v in cols.values()), xs[-1], max(v[1] for v in cols.values())]


def check_rect(where, band, box, rn, nat, bg_x):
    """review A10: the declared rectangle must contain the element's ink with room to spare on both images."""
    x0, y0, x1, y1 = box
    for label, px in (("rn", rn), ("native", nat)):
        bounds = ink_bounds(px, box, bg_x)
        if bounds is None:
            continue
        bx0, by0, bx1, by1 = bounds
        touching = [
            name for name, hit in (
                ("left", bx0 <= x0), ("top", by0 <= y0), ("right", bx1 >= x1 - 1), ("bottom", by1 >= y1 - 1),
            ) if hit
        ]
        if touching:
            raise BandError(
                f"{where}: rect {box} is not big enough on the {label} image: ink {bounds} touches "
                f"{', '.join(touching)}. The element reaches past the mask, so a layout shift there would be "
                f"hidden. Widen the rectangle or re-measure this state."
            )


def check_glyph(where, band, cluster, is_right=False):
    """review A3/A14: a cluster wide enough to be masked must be icon-shaped, or the band is wrong.

    A band's right-hand slot can hold a differently shaped glyph from its left-hand ones: the "more" ellipsis is
    three dots, so it is wide and short where a feed icon is square. Each limit therefore has a `<name>_right`
    form that applies only to clusters at or past `right_x`, so relaxing the ellipsis never relaxes the icons and
    counts beside them (review A14).
    """
    x0, x1, y0, y1 = cluster
    w, h = x1 - x0 + 1, y1 - y0 + 1
    suffix = "_right" if is_right else ""

    def limit(name, default):
        return band.get(f"{name}{suffix}", band.get(name, default))

    minh = limit("minh", MIN_GLYPH_H)
    maxh = limit("maxh", MAX_GLYPH_H)
    ar_min = limit("ar_min", MIN_GLYPH_AR)
    ar_max = limit("ar_max", MAX_GLYPH_AR)
    ar = w / h if h else 0
    if minh <= h <= maxh and ar_min <= ar <= ar_max:
        return
    raise BandError(
        f"{where}: the cluster at ({x0},{y0}) measures {w}x{h} px, aspect {ar:.2f}, which is not icon-shaped "
        f"(height {minh}-{maxh}, aspect {ar_min}-{ar_max}). It is wide enough to be masked, so this band would "
        f"hide text on this state. Declare bands for this state instead of reusing another state's."
    )


def build(band, size, rn, nat, where="band"):
    W, H = size
    kind = band["kind"]
    if kind == "status":
        return [{"x": 0, "y": 0, "w": 1, "h": round(STATUS_H / H, 5), "reason": "status bar: clock, battery, signal",
                 "px": [0, 0, W, STATUS_H]}]
    if kind == "rect":
        x0, y0, x1, y1 = band["box"]
        check_rect(where, band, band["box"], rn, nat, band.get("bg_x", 6))
        return [rect(size, x0, y0, x1, y1, band["reason"], pad=0)]
    box = band["box"]
    bg_x = band.get("bg_x", 6)
    gap = band.get("gap", GAP)
    both = merge(clusters(ink_columns(rn, box, bg_x), gap), clusters(ink_columns(nat, box, bg_x), gap), gap)
    if kind == "glyphs":
        minw = band.get("minw", 36)
        right_x = band.get("right_x", 10_000)
        minw_right = band.get("minw_right", 12)
        maxw = band.get("maxw", 200)
        reason = band.get("reason", "icon glyph (Lucide substitute)")
        kept = [c for c in both if (minw_right if c[0] >= right_x else minw) <= c[1] - c[0] + 1 <= maxw]
        for c in kept:
            check_glyph(where, band, c, is_right=c[0] >= right_x)
        return [rect(size, c[0], c[2], c[1], c[3], reason) for c in kept]
    if kind == "tail":
        count = band.get("count", 1)
        per = []
        for px in (rn, nat):
            cs = clusters(ink_columns(px, box, bg_x), gap)
            if len(cs) >= count:
                tail = cs[-count:]
                per.append((tail[0][0], tail[-1][1], min(c[2] for c in tail), max(c[3] for c in tail)))
        if not per:
            return []
        return [rect(size, min(p[0] for p in per), min(p[2] for p in per), max(p[1] for p in per),
                     max(p[3] for p in per), band.get("reason", "relative timestamp (capture time)"))]
    raise BandError(f"{where}: unknown band kind {kind}")


def coverage(size, rects):
    W, H = size
    mask = bytearray(W * H)
    for r in rects:
        x0, y0, x1, y1 = r["px"]
        for y in range(y0, min(y1, H)):
            mask[y * W + x0:y * W + min(x1, W)] = b"\x01" * (min(x1, W) - x0)
    return sum(mask) / (W * H)


def main():
    spec_path, state, rn_dir, nat_dir, out = sys.argv[1:6]
    spec = json.load(open(spec_path))
    if state not in spec:
        raise BandError(f"{spec_path} declares no bands for state {state}. Bands are per state (review A3).")
    result = {}
    for screen, bands in spec[state].items():
        size, rn = load(f"{rn_dir}/{screen}.png")
        nsize, nat = load(f"{nat_dir}/{screen}.png")
        if size != nsize:
            raise BandError(f"{screen}: RN {size} and native {nsize} differ")
        rects = []
        for i, band in enumerate(bands):
            rects += build(band, size, rn, nat, where=f"{state}/{screen} band {i} ({band['kind']})")
        print(f"{screen}: {len(rects)} masks, {coverage(size, rects) * 100:.2f}% of the screen")
        result[screen] = [{k: v for k, v in r.items() if k != "px"} for r in rects]
    with open(out, "w") as fh:
        json.dump(result, fh, indent=1)


if __name__ == "__main__":
    main()

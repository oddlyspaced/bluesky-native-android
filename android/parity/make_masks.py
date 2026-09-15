#!/usr/bin/env python3
"""Builds check-parity masks.json files for Android from declared bands, measured on both images.

Port of ios/parity/make_masks.py for emulator-5554 (1280x2856). Masks hide only what the parity gate allows
(migration-pack/skills/check-parity/references/masking.md):

  status   the status bar (clock, battery, signal)
  glyphs   icon glyph boxes: ink clusters in a band, at least `minw` px wide. Post counts and text next to an icon
           stay visible: a count glyph is narrower than `minw`, and a cluster is split where the gap is more than
           GAP px. Clusters that start at x >= `right_x` use `minw_right` (the "more" ellipsis is narrow).
           Reason: licence substitution (Lucide glyphs for the Central icon set, neutral mark, drawn avatar).
  tail     the last `count` ink clusters of a line: the relative time ("now", "16s"), which depends on capture time
  rect     an explicit rectangle, with the reason in the spec

Clusters are measured on the RN image and on the native image and merged, so one mask covers both. Ink is a pixel
whose luma differs from the row background by more than 40. The background is sampled at `bg_x` (default 6).

Usage: make_masks.py <spec.json> <state> <rn-dir> <native-dir> <out-masks.json>
Prints the masked fraction per screen; the gate is at most 15% per screen.
"""
import json
import sys

from PIL import Image

GAP = 6
PAD = 4
STATUS_H = 150


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


def build(band, size, rn, nat):
    W, H = size
    kind = band["kind"]
    if kind == "status":
        return [{"x": 0, "y": 0, "w": 1, "h": round(STATUS_H / H, 5), "reason": "status bar: clock, battery, signal",
                 "px": [0, 0, W, STATUS_H]}]
    if kind == "rect":
        x0, y0, x1, y1 = band["box"]
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
        return [rect(size, c[0], c[2], c[1], c[3], reason) for c in both
                if (minw_right if c[0] >= right_x else minw) <= c[1] - c[0] + 1 <= maxw]
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
    raise SystemExit(f"unknown band kind {kind}")


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
    spec = json.load(open(spec_path))[state]
    result = {}
    for screen, bands in spec.items():
        size, rn = load(f"{rn_dir}/{screen}.png")
        nsize, nat = load(f"{nat_dir}/{screen}.png")
        if size != nsize:
            raise SystemExit(f"{screen}: RN {size} and native {nsize} differ")
        rects = []
        for band in bands:
            rects += build(band, size, rn, nat)
        print(f"{screen}: {len(rects)} masks, {coverage(size, rects) * 100:.2f}% of the screen")
        result[screen] = [{k: v for k, v in r.items() if k != "px"} for r in rects]
    with open(out, "w") as fh:
        json.dump(result, fh, indent=1)


if __name__ == "__main__":
    main()

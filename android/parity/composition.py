#!/usr/bin/env python3
"""What is a masked diff made of? Grades it, locates it, and tests it for a whole-image shift.

Usage: composition.py <rn.png> <native.png> <masks.json> <screen> [--shift]

A parity ratio says how much of a screen differs. It does not say whether the two apps drew
different things or the same thing with a different rasteriser, and that is the question a reader
of PARITY.md needs answered. This reports three things:

1. Strength. Each differing pixel is graded by the largest channel distance between the two apps:
   strong above 128, mid above 48, faint above 12. Text rasterised a fraction of a pixel
   differently lands in faint and mid. Ink present in one app and absent in the other lands in
   strong.
2. Regions. The strong pixels are grouped into 8-connected regions (gap 2 px, so one glyph or one
   bar is reported once). A screen whose largest region is a few hundred px has no missing block.
   A screen with one region of tens of thousands of px does.
3. Placement. Row strips and column windows, so a region can be found in the image.

With --shift, the masked differing pixels are counted again at each whole-image offset from -3 to
+3 px. A minimum away from (0, 0) means the two apps agree on content and disagree on position; a
minimum at (0, 0) rules that out. The search is the slow part, so it is opt-in.

The totals agree with check-parity's own count to within about 1%, which is what makes the
breakdown usable as evidence: the denominator is the whole screen, as `diff.mjs` uses it.
"""
import json
import sys
from collections import deque

from PIL import Image

FUZZ = 12        # 5% of 255, the `-fuzz 5%` that check-parity passes to the comparator
STRONG = 128
MID = 48
GAP = 2          # regions this close are one region: a glyph is not reported stroke by stroke


def load_masked(rn_path, nat_path, masks_path, screen):
    rn = Image.open(rn_path).convert("RGB")
    nat = Image.open(nat_path).convert("RGB")
    if rn.size != nat.size:
        sys.exit(f"size mismatch: rn {rn.size}, native {nat.size}")
    w, h = rn.size
    covered = [[False] * w for _ in range(h)]
    for m in json.load(open(masks_path)).get(screen, []):
        x0 = int(round(m["x"] * w)); y0 = int(round(m["y"] * h))
        x1 = min(w, x0 + int(round(m["w"] * w))); y1 = min(h, y0 + int(round(m["h"] * h)))
        for y in range(max(0, y0), y1):
            row = covered[y]
            for x in range(max(0, x0), x1):
                row[x] = True
    return rn.load(), nat.load(), covered, w, h


def delta(p, q):
    return max(abs(p[0] - q[0]), abs(p[1] - q[1]), abs(p[2] - q[2]))


def bar(n, total):
    return "#" * min(40, round(40 * n / total)) if total else ""


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    do_shift = "--shift" in sys.argv
    if len(args) < 4:
        sys.exit(__doc__)
    rn_path, nat_path, masks_path, screen = args[:4]
    a, b, covered, w, h = load_masked(rn_path, nat_path, masks_path, screen)

    strong, mid, faint = set(), 0, 0
    for y in range(h):
        row = covered[y]
        for x in range(w):
            if row[x]:
                continue
            d = delta(a[x, y], b[x, y])
            if d > STRONG:
                strong.add((x, y))
            elif d > MID:
                mid += 1
            elif d > FUZZ:
                faint += 1
    total = len(strong) + mid + faint
    masked = sum(r.count(True) for r in covered)
    print(f"{screen}: {total} differing px, {100 * total / (w * h):.2f}% of the screen "
          f"(masks cover {100 * masked / (w * h):.2f}%)")
    for label, n in (("strong (ink in one app only)", len(strong)), ("mid", mid), ("faint", faint)):
        print(f"  {label:<30} {n:8d}  {100 * n / max(1, total):5.1f}% of the diff"
              f"  {100 * n / (w * h):5.2f}% of the screen")

    seen, regions = set(), []
    for start in strong:
        if start in seen:
            continue
        queue = deque([start]); seen.add(start); cell = []
        while queue:
            x, y = queue.popleft(); cell.append((x, y))
            for dy in range(-GAP, GAP + 1):
                for dx in range(-GAP, GAP + 1):
                    nb = (x + dx, y + dy)
                    if nb in strong and nb not in seen:
                        seen.add(nb); queue.append(nb)
        regions.append(cell)
    regions.sort(key=len, reverse=True)
    print(f"\n  {len(regions)} strong regions; the 8 largest:")
    print("        px     x0    x1     y0    y1    share of screen")
    for cell in regions[:8]:
        xs = [x for x, _ in cell]; ys = [y for _, y in cell]
        print(f"    {len(cell):7d}  {min(xs):5d} {max(xs):5d}  {min(ys):5d} {max(ys):5d}"
              f"    {100 * len(cell) / (w * h):.2f}%")

    print("\n  rows                 px      share")
    strip = h // 20
    for i in range(20):
        lo, hi = i * strip, (i + 1) * strip if i < 19 else h
        n = sum(1 for _, y in strong if lo <= y < hi)
        if n:
            print(f"  {lo:5d}-{hi:5d}  {n:8d}  {100*n/len(strong):5.1f}%  {bar(n, len(strong))}")
    print("\n  cols                 px      share")
    col = w // 10
    for i in range(10):
        lo, hi = i * col, (i + 1) * col if i < 9 else w
        n = sum(1 for x, _ in strong if lo <= x < hi)
        if n:
            print(f"  {lo:5d}-{hi:5d}  {n:8d}  {100*n/len(strong):5.1f}%  {bar(n, len(strong))}")

    if not do_shift:
        print("\n  (pass --shift to search for a whole-image offset)")
        return
    print("\n  masked differing px with the native image moved by (dx, dy):")
    print("    dy\\dx " + "".join(f"{dx:>10d}" for dx in (-1, 0, 1)))
    margin = 4
    for dy in range(-3, 4):
        cells = []
        for dx in (-1, 0, 1):
            n = 0
            for y in range(margin, h - margin):
                row = covered[y]; yy = y + dy
                for x in range(margin, w - margin):
                    if row[x]:
                        continue
                    if delta(a[x, y], b[x + dx, yy]) > FUZZ:
                        n += 1
            cells.append(n)
        print(f"    {dy:>4d}   " + "".join(f"{c:>10d}" for c in cells))


if __name__ == "__main__":
    main()

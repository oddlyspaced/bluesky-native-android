#!/usr/bin/env python3
"""Measure the platform scroll indicator in a capture: its columns, its rows and its colour.

Usage: scroll-thumb.py <png> [x0 x1]

RN's feed is an Android ScrollView, which draws a scrollbar thumb while the list is scrolling and
for a moment after. A Compose `LazyColumn` draws none, so the thumb is a difference, and this
measures it instead of estimating it (PARITY.md difference 6).

The thumb is a solid run in the right-edge columns whose luma differs from the page background.
A run is reported only when it covers at least 8 of those columns and at least 20 rows, which
keeps a page background or a full-width band out of the result. Check the printed colour and
width: a 20 px wide white "thumb" is the background, not a thumb.
"""
import sys

from PIL import Image

WIDE = 8      # a thumb inks at least this many of the sampled columns
TALL = 20     # and runs at least this many rows
OFF = 25      # luma distance from the page background that counts as the thumb


def luma(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def main():
    path = sys.argv[1]
    x0 = int(sys.argv[2]) if len(sys.argv) > 2 else 1260
    x1 = int(sys.argv[3]) if len(sys.argv) > 3 else 1280
    im = Image.open(path).convert("RGB")
    w, h = im.size
    p = im.load()
    ref = luma(p[w // 2, h // 2])

    rows = []
    for y in range(120, h - 60):
        inked = sum(1 for x in range(x0, min(x1, w)) if abs(luma(p[x, y]) - ref) > OFF)
        rows.append(inked >= WIDE)

    runs, start = [], None
    for i, on in enumerate(rows):
        if on and start is None:
            start = i
        elif not on and start is not None:
            if i - start >= TALL:
                runs.append((120 + start, 120 + i - 1))
            start = None
    if start is not None and len(rows) - start >= TALL:
        runs.append((120 + start, 120 + len(rows) - 1))

    print(path)
    if not runs:
        print("  no thumb in these columns")
        return
    for top, bottom in runs:
        mid = (top + bottom) // 2
        cols = [x for x in range(x0, min(x1, w)) if abs(luma(p[x, mid]) - ref) > OFF]
        area = (bottom - top + 1) * (max(cols) - min(cols) + 1)
        print(f"  y {top}-{bottom}  length {bottom - top + 1}  "
              f"cols {min(cols)}-{max(cols)} (w {max(cols) - min(cols) + 1})  "
              f"colour {p[min(cols) + 1, mid]}  area {area} px, {100 * area / (w * h):.2f}% of the screen")


if __name__ == "__main__":
    main()

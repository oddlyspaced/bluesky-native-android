#!/usr/bin/env python3
"""Text-band scan: top y of each dark text band in a column window, RN baseline vs native capture.

Usage: bandscan.py <rn.png> <native.png> [x0 x1 [grey]]
A row is "ink" when any pixel in x0..x1 (step 2) has all channels below grey. Bands of consecutive ink rows are
listed with their top y; bands are paired in order and the offset is native minus RN, in px (3 px = 1 dp here).
"""
import sys
from PIL import Image


def bands(path, x0, x1, grey, y0=150, y1=2640):
    im = Image.open(path).convert("RGB")
    ink = []
    for y in range(y0, y1):
        dark = any(max(im.getpixel((x, y))) < grey for x in range(x0, x1, 2))
        ink.append(dark)
    out, start = [], None
    for i, dark in enumerate(ink):
        if dark and start is None:
            start = i
        elif not dark and start is not None:
            if i - start >= 6:
                out.append((y0 + start, i - start))
            start = None
    return out


def main():
    rn, native = sys.argv[1], sys.argv[2]
    x0 = int(sys.argv[3]) if len(sys.argv) > 3 else 210
    x1 = int(sys.argv[4]) if len(sys.argv) > 4 else 600
    grey = int(sys.argv[5]) if len(sys.argv) > 5 else 110
    a, b = bands(rn, x0, x1, grey), bands(native, x0, x1, grey)
    print(f"bands RN {len(a)}, native {len(b)} (x {x0}-{x1}, grey < {grey})")
    print("| # | RN top y (height) | Native top y (height) | Offset |")
    print("|---|---|---|---|")
    for i in range(max(len(a), len(b))):
        ra = f"{a[i][0]} ({a[i][1]})" if i < len(a) else "-"
        nb = f"{b[i][0]} ({b[i][1]})" if i < len(b) else "-"
        off = f"{b[i][0] - a[i][0]:+d}" if i < len(a) and i < len(b) else "-"
        print(f"| {i + 1} | {ra} | {nb} | {off} |")


if __name__ == "__main__":
    main()

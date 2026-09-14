#!/usr/bin/env python3
"""Convert Lucide (ISC) stroke SVG icons into Android VectorDrawables.

Usage: lucide2vd.py <lucide icons dir> <res/drawable dir> <lucide-name>=<res-name>[:filled] ...

Lucide icons are 24x24, stroke 2, round caps and joins, no fill. VectorDrawable has only <path>, so
circle, rect, line, polyline, polygon and ellipse are rewritten as path data. `:filled` also fills
every closed shape (used for the active heart, home and bell states).
"""
import os
import re
import sys


def attrs(tag):
    return dict(re.findall(r'([\w-]+)="([^"]*)"', tag))


def f(v):
    return float(v)


def to_path(name, a):
    if name == 'path':
        return a['d']
    if name == 'circle':
        cx, cy, r = f(a['cx']), f(a['cy']), f(a['r'])
        return f'M{cx - r},{cy}a{r},{r} 0 1,0 {2 * r},0a{r},{r} 0 1,0 {-2 * r},0'
    if name == 'ellipse':
        cx, cy, rx, ry = f(a['cx']), f(a['cy']), f(a['rx']), f(a['ry'])
        return f'M{cx - rx},{cy}a{rx},{ry} 0 1,0 {2 * rx},0a{rx},{ry} 0 1,0 {-2 * rx},0'
    if name == 'line':
        return f"M{a['x1']},{a['y1']}L{a['x2']},{a['y2']}"
    if name in ('polyline', 'polygon'):
        pts = re.findall(r'-?[\d.]+', a['points'])
        pairs = [f'{pts[i]},{pts[i + 1]}' for i in range(0, len(pts), 2)]
        return 'M' + 'L'.join(pairs) + ('Z' if name == 'polygon' else '')
    if name == 'rect':
        x, y, w, h = f(a.get('x', 0)), f(a.get('y', 0)), f(a['width']), f(a['height'])
        rx = f(a.get('rx', a.get('ry', 0)))
        ry = f(a.get('ry', rx))
        if rx == 0:
            return f'M{x},{y}h{w}v{h}h{-w}Z'
        return (f'M{x + rx},{y}h{w - 2 * rx}a{rx},{ry} 0 0 1 {rx},{ry}v{h - 2 * ry}'
                f'a{rx},{ry} 0 0 1 {-rx},{ry}h{-(w - 2 * rx)}a{rx},{ry} 0 0 1 {-rx},{-ry}'
                f'v{-(h - 2 * ry)}a{rx},{ry} 0 0 1 {rx},{-ry}Z')
    raise ValueError(f'unsupported element {name}')


def main():
    src, out = sys.argv[1], sys.argv[2]
    for spec in sys.argv[3:]:
        lucide, res = spec.split('=')
        filled = res.endswith(':filled')
        res = res.removesuffix(':filled')
        svg = open(os.path.join(src, lucide + '.svg')).read()
        body = []
        for name, tag in re.findall(r'<(path|circle|ellipse|line|polyline|polygon|rect)\b([^>]*)/?>', svg):
            d = to_path(name, attrs(tag))
            fill = ' android:fillColor="#FF000000"' if filled else ''
            body.append(
                f'  <path android:pathData="{d}"{fill}\n'
                '      android:strokeColor="#FF000000" android:strokeWidth="2"\n'
                '      android:strokeLineCap="round" android:strokeLineJoin="round"/>'
            )
        xml = (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- Lucide "{lucide}" (ISC licence, see android/licenses/Lucide-LICENSE.txt). -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp" android:height="24dp"\n'
            '    android:viewportWidth="24" android:viewportHeight="24">\n'
            + '\n'.join(body) + '\n</vector>\n'
        )
        with open(os.path.join(out, res + '.xml'), 'w') as fh:
            fh.write(xml)
        print(res, len(body))


if __name__ == '__main__':
    main()

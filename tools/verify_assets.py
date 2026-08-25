#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Check that bundled assets are real files rather than placeholders.

Three ONNX models and one font once shipped as zero-filled stubs with plausible
magic bytes. The app loaded them, the load failed, and a catch-all fallback
swallowed the error, so everything appeared to work while no neural model was
ever running. Nothing in the build or the test suite noticed.

Fonts are checked by actual glyph coverage rather than file size, because size
is a bad proxy: the real Noto Sans Ol Chiki is only 15 KB, since the script has
just 48 code points.

    python tools/verify_assets.py
"""
from __future__ import annotations

import pathlib
import struct
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'app' / 'src' / 'main' / 'assets'

# name -> (first code point, last code point, human label)
FONTS = {
    'fonts/NotoSansOlChiki-Regular.ttf':    (0x1C5A, 0x1C77,  'Ol Chiki'),
    'fonts/NotoSansWarangCiti-Regular.ttf': (0x118A0, 0x118DF, 'Warang Citi'),
    'fonts/NotoSansDevanagari-Regular.ttf': (0x0905, 0x0939,  'Devanagari'),
    'fonts/NotoSansNagMundari-Regular.ttf': (0x1E4D0, 0x1E4EB, 'Nag Mundari'),
}


def font_coverage(data: bytes, lo: int, hi: int) -> tuple[int, int]:
    """Return (covered, wanted) code points, read from the font's cmap."""
    num_tables = struct.unpack('>H', data[4:6])[0]
    cmap_off = None
    for i in range(num_tables):
        rec = 12 + i * 16
        if data[rec:rec + 4] == b'cmap':
            cmap_off = struct.unpack('>I', data[rec + 8:rec + 12])[0]
    if cmap_off is None:
        return 0, hi - lo + 1

    covered: set[int] = set()
    n_sub = struct.unpack('>H', data[cmap_off + 2:cmap_off + 4])[0]
    for i in range(n_sub):
        rec = cmap_off + 4 + i * 8
        sub = cmap_off + struct.unpack('>I', data[rec + 4:rec + 8])[0]
        fmt = struct.unpack('>H', data[sub:sub + 2])[0]
        if fmt == 4:
            seg2 = struct.unpack('>H', data[sub + 6:sub + 8])[0]
            seg = seg2 // 2
            ends = [struct.unpack('>H', data[sub + 14 + j * 2:sub + 16 + j * 2])[0] for j in range(seg)]
            sp = sub + 16 + seg2
            starts = [struct.unpack('>H', data[sp + j * 2:sp + 2 + j * 2])[0] for j in range(seg)]
            for s, e in zip(starts, ends):
                if e != 0xFFFF:
                    covered.update(range(s, e + 1))
        elif fmt == 12:
            n_groups = struct.unpack('>I', data[sub + 12:sub + 16])[0]
            for j in range(n_groups):
                g = sub + 16 + j * 12
                s, e = struct.unpack('>II', data[g:g + 8])
                covered.update(range(s, min(e, s + 4000) + 1))

    wanted = set(range(lo, hi + 1))
    return len(wanted & covered), len(wanted)


def main() -> int:
    failures: list[tuple[str, str]] = []

    for rel, (lo, hi, label) in FONTS.items():
        path = ASSETS / rel
        if not path.exists():
            failures.append((rel, 'missing'))
            continue
        data = path.read_bytes()
        if data[:4] not in (b'\x00\x01\x00\x00', b'OTTO', b'true', b'ttcf'):
            failures.append((rel, 'not a TrueType/OpenType signature'))
            continue
        got, want = font_coverage(data, lo, hi)
        if got < want:
            failures.append((rel, '%s coverage %d/%d, so some glyphs would render as boxes' % (label, got, want)))
        else:
            print('  ok    font   %-38s %10d bytes  %s %d/%d' % (rel, len(data), label, got, want))

    for rel, why in failures:
        print('  FAIL         %-38s %s' % (rel, why))

    total = len(FONTS)
    print('\n%d of %d assets are real.' % (total - len(failures), total))
    if failures:
        print('\nA stub font means Ol Chiki characters render as empty boxes.')
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())

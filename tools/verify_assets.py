#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Check that bundled assets are real files rather than placeholders.

Three ONNX models and one font shipped as zero-filled stubs with plausible
magic bytes. The app loaded them, the load failed, and a catch-all fallback
swallowed the error, so everything appeared to work while no neural model was
ever running. Nothing in the build or the test suite noticed.

This checks size and real magic bytes, so a stub fails loudly.

    python tools/verify_assets.py
"""
from __future__ import annotations

import pathlib
import struct
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'app' / 'src' / 'main' / 'assets'

# path, minimum plausible size in bytes, kind
EXPECTED = [
    ('silero_vad.onnx',                    500_000,   'onnx'),
    ('all-MiniLM-L6-v2_int8.onnx',       10_000_000,  'onnx'),
    ('ocr_mobilenet_int8.onnx',           1_000_000,  'onnx'),
    ('fonts/NotoSansOlChiki-Regular.ttf',    20_000,  'ttf'),
    ('database/nipun_vector_embeddings.bin', 100_000, 'nfln'),
]


def check_magic(data: bytes, kind: str) -> str | None:
    """Return an error string, or None if the header looks genuine."""
    if kind == 'onnx':
        # ONNX is protobuf. Field 1 (ir_version) varint => first byte 0x08.
        # A literal ASCII "ONNXV001" header is not the format, it is a stub.
        if data[:8] == b'ONNXV001':
            return 'ASCII "ONNXV001" header, which is not the ONNX format'
        if not data[:1] == b'\x08':
            return 'does not start with a protobuf varint (0x08)'
    elif kind == 'ttf':
        if data[:4] not in (b'\x00\x01\x00\x00', b'OTTO', b'true', b'ttcf'):
            return 'not a TrueType/OpenType signature'
        # A real font has a table directory; numTables lives at offset 4.
        num_tables = struct.unpack('>H', data[4:6])[0]
        if num_tables == 0:
            return 'valid signature but zero tables, so it contains no glyphs'
    elif kind == 'nfln':
        if data[:4] != b'NFLN':
            return 'missing NFLN magic'
    return None


def main() -> int:
    failures = []
    for rel, min_size, kind in EXPECTED:
        path = ASSETS / rel
        if not path.exists():
            failures.append((rel, 'missing'))
            continue
        data = path.read_bytes()
        size = len(data)
        if size < min_size:
            failures.append((rel, '%d bytes, expected at least %d (placeholder?)' % (size, min_size)))
            continue
        problem = check_magic(data, kind)
        if problem:
            failures.append((rel, problem))
            continue
        print('  ok      %-42s %9d bytes' % (rel, size))

    for rel, why in failures:
        print('  FAIL    %-42s %s' % (rel, why))

    print('\n%d of %d assets are real.' % (len(EXPECTED) - len(failures), len(EXPECTED)))
    if failures:
        print('\nA stub asset does not crash this app. RealTimeClassroomDialogueEngine\n'
              'catches every load failure and silently switches to a heuristic\n'
              'fallback, so the demo runs with no neural model behind it. Fetch the\n'
              'real files before claiming on-device inference.')
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())

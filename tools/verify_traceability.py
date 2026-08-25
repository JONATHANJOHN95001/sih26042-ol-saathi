#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Check that every source file the traceability matrix cites actually exists.

The previous matrix claimed 100% compliance while citing fifteen classes that
had been renamed months earlier, so every citation in the judge-facing document
was broken. A reviewer can check that in a minute, and it costs more credibility
than the gaps it was hiding.

Run before any submission:

    python tools/verify_traceability.py

Exits non-zero if a citation does not resolve.
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MATRIX = ROOT / 'PROBLEM_STATEMENT_TRACEABILITY_MATRIX.md'

# Anything in backticks that looks like a source path rather than prose.
CITATION = re.compile(r'`([A-Za-z0-9_/.-]+\.(?:kt|kts|json|xml|ttf|onnx|bin))`')


def main() -> int:
    if not MATRIX.exists():
        print('MISSING: %s' % MATRIX.name)
        return 2

    text = MATRIX.read_text(encoding='utf-8')
    cited = sorted(set(CITATION.findall(text)))
    if not cited:
        print('No file citations found. Has the matrix lost its evidence column?')
        return 2

    missing = []
    for rel in cited:
        # Citations are written relative to the repo root.
        if not (ROOT / rel).exists():
            missing.append(rel)

    print('citations checked : %d' % len(cited))
    print('resolved          : %d' % (len(cited) - len(missing)))
    print('broken            : %d' % len(missing))

    if missing:
        print('\nThese are cited but do not exist:')
        for rel in missing:
            print('  %s' % rel)
        print('\nEither the file was renamed and the matrix was not updated, or the '
              'claim is not backed by code. Fix whichever it is before submitting.')
        return 1

    print('\nEvery cited file exists.')
    return 0


if __name__ == '__main__':
    sys.exit(main())

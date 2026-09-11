# -*- coding: utf-8 -*-
"""
Fetch the Noto font each shipped script needs, into assets/fonts.

    python tools/fetch_fonts.py            # everything langs.py asks for
    python tools/fetch_fonts.py --check    # report only, download nothing

WHY THIS IS A BUILD STEP RATHER THAN A COMMIT
---------------------------------------------
Android ships no font for several of these scripts, so the app has to carry
its own or a child sees empty boxes. Fetching them from the upstream Noto
repository at build time keeps provenance obvious: every file is traceable to
a release of notofonts.github.io rather than to a binary someone dropped in a
folder once.

Fonts are the real cost of a language. The JSON pack for a language is about
30 KB; its font is 55 KB to 244 KB. That is why tools/langs.py is organised
by script rather than by language, and why several languages share one file.

The licence is the SIL Open Font License 1.1 for every Noto family, which
permits bundling in an APK. That matters for a submission whose whole claim is
that its provenance is checkable.
"""
from __future__ import annotations

import argparse
import pathlib
import sys
import urllib.error
import urllib.request

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import langs  # noqa: E402

REPO = pathlib.Path(__file__).resolve().parent.parent
FONT_DIR = REPO / 'app' / 'src' / 'main' / 'assets' / 'fonts'

BASE = ('https://raw.githubusercontent.com/notofonts/notofonts.github.io/'
        'main/fonts/{family}/hinted/ttf/{family}-Regular.ttf')

# A TTF starts with one of these. Checked because GitHub answers a wrong path
# with an HTML 404 page, and a 404 saved as a .ttf fails much later, at render
# time on a device, as blank text rather than as a download error.
TTF_MAGIC = (b'\x00\x01\x00\x00', b'true', b'ttcf', b'OTTO')


def wanted():
    """Every font file the shipped languages need, as {filename: family}."""
    out = {}
    for code in langs.LANGUAGES:
        sp = langs.spec(code)
        out[sp['font']] = sp['font'].replace('-Regular.ttf', '')
    return out


def fetch(family, dest):
    url = BASE.format(family=family)
    req = urllib.request.Request(url, headers={'User-Agent': 'ol-saathi-build'})
    with urllib.request.urlopen(req, timeout=60) as r:
        data = r.read()
    if not data.startswith(TTF_MAGIC):
        raise ValueError('not a TTF (got %r), the family name is probably wrong'
                         % data[:16])
    dest.write_bytes(data)
    return len(data)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--check', action='store_true',
                    help='report what is missing without downloading')
    ap.add_argument('--force', action='store_true',
                    help='re-download fonts that already exist')
    args = ap.parse_args()

    FONT_DIR.mkdir(parents=True, exist_ok=True)
    need = wanted()

    have, missing, total = [], [], 0
    for filename, family in sorted(need.items()):
        path = FONT_DIR / filename
        if path.exists() and not args.force:
            have.append((filename, path.stat().st_size))
            total += path.stat().st_size
        else:
            missing.append((filename, family))

    for filename, size in have:
        print('  have    %-34s %8d bytes' % (filename, size))

    if args.check:
        for filename, _ in missing:
            print('  MISSING %-34s' % filename)
        print('\n%d present (%.2f MB), %d missing'
              % (len(have), total / 1048576.0, len(missing)))
        return 1 if missing else 0

    failed = []
    for filename, family in missing:
        try:
            n = fetch(family, FONT_DIR / filename)
            total += n
            print('  fetched %-34s %8d bytes' % (filename, n))
        except (urllib.error.URLError, ValueError, OSError) as exc:
            failed.append((filename, str(exc)[:90]))
            print('  FAILED  %-34s %s' % (filename, str(exc)[:60]))

    print('\n%d fonts in assets, %.2f MB total'
          % (len(have) + len(missing) - len(failed), total / 1048576.0))
    if failed:
        print('\nA language whose font failed will be listed as unavailable by')
        print('the manifest rather than offered as boxes. Re-run to retry.')
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())

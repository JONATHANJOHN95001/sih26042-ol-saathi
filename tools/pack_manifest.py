# -*- coding: utf-8 -*-
"""
Keep packs.json's audio counts in step with the packs themselves.

The app reads `audioEntries` from packs.json to decide whether to tell a teacher
"No audio in Tamil yet". build_pack_indictrans.py writes that manifest when it
builds the packs, which was before any audio existed, and the tools that later
add audio only ever touched the pack files. So on 10 Sep 2026 the Tamil pack
carried 52 playable clips while the language bar said it had none.

This recounts every language from its pack file and rewrites only that field,
leaving the rest of the manifest as its builder wrote it. The audio tools call
it after every write, and verify_assets.py fails if the two ever disagree.

    python tools/pack_manifest.py            report and fix
    python tools/pack_manifest.py --check    report only, exit 1 on drift
"""
import argparse
import io
import json
import os
import sys

PACK_DIR = os.path.join('app', 'src', 'main', 'assets', 'pack')
MANIFEST = os.path.join(PACK_DIR, 'packs.json')


def audio_counts(pack_dir=PACK_DIR):
    """Language code to the number of entries with an audio field, from disk."""
    counts = {}
    for name in sorted(os.listdir(pack_dir)):
        if name.startswith('pack.') and name.endswith('.json') and name != 'packs.json':
            code = name[len('pack.'):-len('.json')]
            data = json.load(io.open(os.path.join(pack_dir, name), encoding='utf-8'))
            counts[code] = sum(1 for e in data.get('entries', {}).values() if e.get('audio'))
    return counts


def drift(pack_dir=PACK_DIR):
    """(code, manifest says, pack has) for every language that disagrees."""
    manifest = json.load(io.open(os.path.join(pack_dir, 'packs.json'), encoding='utf-8'))
    counts = audio_counts(pack_dir)
    return [(lang['code'], lang.get('audioEntries', 0), counts[lang['code']])
            for lang in manifest.get('languages', [])
            if lang['code'] in counts and lang.get('audioEntries', 0) != counts[lang['code']]]


def refresh_audio_counts(pack_dir=PACK_DIR):
    """Rewrite audioEntries from the packs. Returns the list of changes made."""
    path = os.path.join(pack_dir, 'packs.json')
    manifest = json.load(io.open(path, encoding='utf-8'))
    counts = audio_counts(pack_dir)
    changed = []
    for lang in manifest.get('languages', []):
        have = counts.get(lang['code'])
        if have is not None and lang.get('audioEntries', 0) != have:
            changed.append((lang['code'], lang.get('audioEntries', 0), have))
            lang['audioEntries'] = have
    if changed:
        with io.open(path, 'w', encoding='utf-8', newline='\n') as fh:
            fh.write(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    return changed


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--check', action='store_true', help='report drift and exit 1, change nothing')
    args = ap.parse_args()
    if args.check:
        bad = drift()
        for code, said, has in bad:
            print('  DRIFT  %-4s manifest says %d with audio, pack has %d' % (code, said, has))
        print('packs.json audio counts: %s' % ('match the packs' if not bad else '%d out of date' % len(bad)))
        return 1 if bad else 0
    changed = refresh_audio_counts()
    for code, old, new in changed:
        print('  %-4s audioEntries %d -> %d' % (code, old, new))
    print('packs.json: %s' % ('%d languages updated' % len(changed) if changed else 'already up to date'))
    return 0


if __name__ == '__main__':
    sys.exit(main())

# -*- coding: utf-8 -*-
"""
Give every language's flashcards the pictures Santali already has.

tools/fetch_flashcard_icons.py was run against pack.sat.json only, so Santali
carries a drawable on all 53 entries while the other sixteen packs carry one on
just the ten lesson entries. A flashcard without a picture still prints, and
FlashcardPdf reflows the text into the space rather than leaving a hole, but a
picture is most of what makes a card usable by a child who cannot yet read
either script on it.

The entry ids are identical across every pack, and a picture is a picture in
any language: the icon for "house" does not change because the caption is
Tamil. So the mapping can simply be copied.

    python tools/propagate_flashcard_icons.py              # report only
    python tools/propagate_flashcard_icons.py --write      # apply

WHAT THIS DOES NOT DO
---------------------
It copies a picture, never a translation. No target string, provenance field or
review flag is touched, and an entry that already has an image is left alone so
a language-specific choice can never be overwritten by the Santali one.
"""

import argparse
import glob
import io
import json
import os

PACK_DIR = os.path.join('app', 'src', 'main', 'assets', 'pack')
DRAWABLE_DIR = os.path.join('app', 'src', 'main', 'res', 'drawable')
SOURCE = 'pack.sat.json'
FIELDS = ('image', 'imageSource')


def drawables_on_disk():
    """Base names of every drawable the APK actually carries.

    An image field naming a drawable that was never created is worse than an
    empty one: it reads as "this card has a picture" everywhere except on the
    page. The non-Santali packs shipped ten of these each, placeholder slugs
    like "grandmother" from before the icons were fetched, so the lesson cards
    in every language but Santali have always printed blank.
    """
    names = set()
    for path in glob.glob(os.path.join(DRAWABLE_DIR, '*')):
        base = os.path.basename(path)
        names.add(base.split('.')[0])
    return names


def load(path):
    with io.open(path, encoding='utf-8') as f:
        return json.load(f)


def save(path, data):
    with io.open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write('\n')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--write', action='store_true',
                    help='apply the changes; without it nothing is written')
    ap.add_argument('--only', default='',
                    help='comma-separated language codes, e.g. tam,mal,tel,ben')
    args = ap.parse_args()

    src_path = os.path.join(PACK_DIR, SOURCE)
    if not os.path.exists(src_path):
        raise SystemExit('no source pack at %s' % src_path)
    source_entries = load(src_path)['entries']

    wanted = [c.strip() for c in args.only.split(',') if c.strip()]
    on_disk = drawables_on_disk()

    total_added = 0
    for path in sorted(glob.glob(os.path.join(PACK_DIR, 'pack.*.json'))):
        name = os.path.basename(path)
        if name == SOURCE:
            continue
        code = name[len('pack.'):-len('.json')]
        if wanted and code not in wanted:
            continue

        pack = load(path)
        entries = pack.get('entries', {})
        added, repaired, already, absent = 0, 0, 0, 0

        for eid, entry in entries.items():
            current = entry.get('image')
            resolves = bool(current) and current in on_disk
            if resolves:
                already += 1
                continue
            src = source_entries.get(eid)
            if not src or not src.get('image') or src['image'] not in on_disk:
                absent += 1
                continue
            for field in FIELDS:
                if src.get(field):
                    entry[field] = src[field]
            if current:
                repaired += 1
            else:
                added += 1

        total_added += added + repaired
        print('%-5s %3d entries: %3d given a picture, %3d broken reference(s) repaired, '
              '%3d already good, %3d have none to copy'
              % (code, len(entries), added, repaired, already, absent))

        if args.write and (added or repaired):
            save(path, pack)

    print()
    if args.write:
        print('Wrote %d new picture references.' % total_added)
        print('Re-run tools/verify_traceability.py, and rebuild before trusting a sheet.')
    else:
        print('%d entries would gain a picture. Nothing written; pass --write.' % total_added)


if __name__ == '__main__':
    main()

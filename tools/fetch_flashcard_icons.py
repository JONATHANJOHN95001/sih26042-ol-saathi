# -*- coding: utf-8 -*-
"""
Give every flashcard a picture.

Why this exists
---------------
The flashcards carry Hindi, Ol Chiki and an English gloss, and nothing else.
The children they are made for are six years old and are learning to read. A
card that is only text asks them to decode before they can engage, which is
backwards: the picture is what lets a pre-literate child connect the spoken
word to the written one. Every primary literacy programme in the world puts a
picture on the card.

Where the icons come from
-------------------------
Iconify's public API, no key required. We use Fluent Emoji Flat, which is MIT
licensed, so the icons can be redistributed inside the APK with no attribution
or share-alike burden. They are colour, recognisable, and already designed to
read at small sizes.

    https://api.iconify.design/fluent-emoji-flat/<name>.svg

Chosen by measurement, not preference. Across a twelve-icon sample, the share
that converted cleanly to VectorDrawable was:

    fluent-emoji-flat  11/12   MIT
    twemoji             9/12   CC BY 4.0
    openmoji            7/12   CC BY-SA 4.0
    noto                4/12   Apache 2.0

Noto was the first choice and lost. Its icons lean on gradients, ellipses and
clipPaths, none of which map onto a VectorDrawable path.

Same architecture as the translations: fetch once at build time, ship the
result inside the APK, make no network call at runtime.

Format
------
SVG converts to Android VectorDrawable rather than PNG. Vectors stay sharp at
any size, weigh a few hundred bytes each, and need no rasteriser installed.
These icons are one or two paths with no gradients, so the conversion is a
direct mapping.

Nothing is written without --write, and an icon that does not resolve is
reported and skipped rather than shipped as a broken reference.
"""
import argparse
import io
import json
import os
import re
import sys
import urllib.parse
import urllib.request

PACK = 'app/src/main/assets/pack/pack.sat.json'
DRAWABLE = 'app/src/main/res/drawable'
API = 'https://api.iconify.design'
SET = 'fluent-emoji-flat'   # MIT, colour, converts cleanly (see above)
UA = {'User-Agent': 'ol-saathi-build/1.0'}

# Entry id -> Noto Emoji name. Chosen for what a six-year-old would recognise,
# not for what is literally closest to the English gloss.
ICONS = {
    # classroom routine
    'p01': 'waving-hand',          'p02': 'chair',
    'p03': 'thinking-face',        'p04': 'school',
    'p05': 'house',                'p06': 'open-book',
    'p07': 'closed-book',          'p08': 'pencil',
    'p14': 'raised-hand',          'p15': 'shushing-face',
    'p16': 'busts-in-silhouette',  'p17': 'counterclockwise-arrows-button',
    'p18': 'thumbs-up',            'p19': 'clapping-hands',
    'p20': 'check-mark-button',    'p21': 'slightly-smiling-face',
    'p38': 'hugging-face',         'p39': 'potable-water',
    'p40': 'house-with-garden',
    # oral language
    'p09': 'speaking-head',        'p10': 'ear',
    'p11': 'loudspeaker',          'p22': 'thinking-face',
    'p23': 'magnifying-glass-tilted-left',
    'p24': 'name-badge',           'p25': 'person-raising-hand',
    'p26': 'speech-balloon',       'p35': 'books',
    'p36': 'speech-balloon',       'p37': 'speaking-head',
    # writing, reading, decoding
    'p12': 'memo',                 'p13': 'framed-picture',
    'p32': 'input-latin-letters',  'p33': 'input-latin-letters',
    'p34': 'speaker-high-volume',
    # numeracy
    'p27': 'abacus',               'p28': 'abacus',
    'p29': 'plus',                 'p30': 'chart-increasing',
    'p31': 'abacus',
    # the Neema and grandmother lesson
    'neema-dadi.l1': 'school',              'neema-dadi.l2': 'older-woman',
    'neema-dadi.l3': 'house',               'neema-dadi.l4': 'leg',
    'neema-dadi.l5': 'hourglass-not-done',  'neema-dadi.l6': 'pot-of-food',
    'neema-dadi.l7': 'kite',                'neema-dadi.l8': 'older-woman',
    'neema-dadi.l9': 'probing-cane',        'neema-dadi.l10': 'footprints',
    'neema-dadi.c1': 'house',               'neema-dadi.c2': 'leg',
    'neema-dadi.c3': 'round-pushpin',
}

# Fallbacks tried in order when the first choice does not exist in the set.
FALLBACK = {
    'older-woman': ['old-woman', 'woman'],
    'probing-cane': ['walking-stick', 'cane', 'old-man'],
    'person-raising-hand': ['raising-hand', 'raised-hand'],
    'input-latin-letters': ['input-latin-uppercase', 'abcd', 'memo'],
    'potable-water': ['droplet', 'glass-of-milk'],
    'house-with-garden': ['house'],
    'counterclockwise-arrows-button': ['repeat-button', 'arrows-counterclockwise'],
}


def fetch(url):
    req = urllib.request.Request(url, headers=UA)
    return urllib.request.urlopen(req, timeout=30).read().decode('utf-8', 'replace')


def search(term):
    """Ask Iconify for the closest icon in our set. Used when a name misses."""
    try:
        url = '%s/search?query=%s&prefix=%s&limit=8' % (
            API, urllib.parse.quote(term.replace('-', ' ')), SET)
        hits = json.loads(fetch(url)).get('icons', [])
        return [h.split(':', 1)[1] for h in hits]
    except Exception:
        return []


def resolve(name):
    """
    Return the first icon that exists AND converts.

    Tries the chosen name, then hand-written fallbacks, then whatever the
    search endpoint suggests. An icon that exists but cannot be converted is
    not good enough, so conversion is part of the test rather than a later
    step that fails after we have committed to a name.
    """
    tried = []
    for candidate in [name] + FALLBACK.get(name, []) + search(name):
        if candidate in tried:
            continue
        tried.append(candidate)
        try:
            svg = fetch('%s/%s/%s.svg?height=128' % (API, SET, candidate))
        except Exception:
            continue
        if not svg.strip().startswith('<svg'):
            continue
        try:
            to_vector_drawable(svg)
        except ValueError:
            continue
        return candidate, svg
    return None, None


def to_vector_drawable(svg, size_dp=48):
    """
    Convert a simple Iconify SVG to an Android VectorDrawable.

    Handles exactly what these icons use: a viewBox and one or more <path>
    elements with a solid fill. Anything else raises, rather than silently
    producing a drawable that renders as nothing.
    """
    vb = re.search(r'viewBox="([\d.\-]+)\s+([\d.\-]+)\s+([\d.]+)\s+([\d.]+)"', svg)
    if not vb:
        raise ValueError('no viewBox')
    vw, vh = vb.group(3), vb.group(4)

    unsupported = set(re.findall(r'<(\w+)', svg)) - {'svg', 'path', 'g', 'defs', 'title'}
    if unsupported:
        raise ValueError('unsupported elements: %s' % sorted(unsupported))

    paths = re.findall(r'<path\b([^>]*?)/?>', svg)
    if not paths:
        raise ValueError('no paths')

    out = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Fluent Emoji Flat, MIT. Fetched from Iconify at build time by',
        '     tools/fetch_flashcard_icons.py. Do not edit by hand. -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="%ddp"' % size_dp,
        '    android:height="%ddp"' % size_dp,
        '    android:viewportWidth="%s"' % vw,
        '    android:viewportHeight="%s">' % vh,
    ]
    for attrs in paths:
        d = re.search(r'\bd="([^"]+)"', attrs)
        if not d:
            continue
        fill = re.search(r'\bfill="([^"]+)"', attrs)
        colour = fill.group(1) if fill else '#000000'
        if colour in ('currentColor', 'none'):
            colour = '#37474F'
        out.append('    <path')
        out.append('        android:fillColor="%s"' % colour)
        out.append('        android:pathData="%s" />' % d.group(1))
    out.append('</vector>')
    return '\n'.join(out) + '\n'


def drawable_name(entry_id):
    """Android resource names allow only lowercase, digits and underscore."""
    return 'ic_card_' + re.sub(r'[^a-z0-9]+', '_', entry_id.lower()).strip('_')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--write', action='store_true', help='write drawables and update the pack')
    args = ap.parse_args()

    pack = json.load(io.open(PACK, encoding='utf-8'))
    entries = pack['entries']

    missing_map = [e for e in entries if e not in ICONS]
    unknown_id = [e for e in ICONS if e not in entries]

    ok, failed = {}, []
    for eid in sorted(ICONS):
        if eid not in entries:
            continue
        wanted = ICONS[eid]
        got, svg = resolve(wanted)
        if not got:
            failed.append((eid, wanted, 'no such icon in set'))
            continue
        ok[eid] = (got, to_vector_drawable(svg))

    print('Icon set   : %s (MIT)' % SET)
    print('Source     : %s' % API)
    print('Entries    : %d in pack' % len(entries))
    print()
    print('Converted  : %d' % len(ok))
    for eid, (name, xml) in sorted(ok.items())[:6]:
        print('   %-16s %-32s %d bytes' % (eid, SET + ':' + name, len(xml)))
    if len(ok) > 6:
        print('   ... and %d more' % (len(ok) - 6))

    if failed:
        print()
        print('FAILED (%d), these will not ship:' % len(failed))
        for eid, name, why in failed:
            print('   %-16s %-28s %s' % (eid, name, why))
    if missing_map:
        print()
        print('No icon chosen for %d entries: %s' % (len(missing_map), ', '.join(sorted(missing_map))))
    if unknown_id:
        print()
        print('Mapped ids not in the pack: %s' % ', '.join(sorted(unknown_id)))

    total = sum(len(x) for _, x in ok.values())
    print()
    print('Coverage   : %d of %d entries (%.0f%%), %.1f KB total'
          % (len(ok), len(entries), 100.0 * len(ok) / len(entries), total / 1024.0))

    if not args.write:
        print()
        print('Nothing written. Re-run with --write to apply.')
        return

    if not os.path.isdir(DRAWABLE):
        os.makedirs(DRAWABLE)
    for eid, (name, xml) in ok.items():
        res = drawable_name(eid)
        with io.open(os.path.join(DRAWABLE, res + '.xml'), 'w',
                     encoding='utf-8', newline='\n') as fh:
            fh.write(xml)
        entries[eid]['image'] = res
        entries[eid]['imageSource'] = '%s:%s' % (SET, name)

    pack.setdefault('provenance', {})['iconSet'] = 'Fluent Emoji Flat (MIT) via Iconify'
    with io.open(PACK, 'w', encoding='utf-8', newline='\n') as fh:
        json.dump(pack, fh, ensure_ascii=False, indent=2, sort_keys=True)
        fh.write('\n')

    print()
    print('Wrote %d drawables to %s and updated the pack.' % (len(ok), DRAWABLE))


if __name__ == '__main__':
    main()

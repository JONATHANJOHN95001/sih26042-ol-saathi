# -*- coding: utf-8 -*-
"""
Ol Chiki to Devanagari pronunciation bridge.

## What this is, and what it is not

This is a **transliteration**, not a translation. It rewrites Santali written in
Ol Chiki into Devanagari letters so a teacher who reads Hindi but not Ol Chiki
can say the line out loud. The meaning is not touched and nothing is
interpreted. The app already has a provenance state for exactly this,
`Provenance.TRANSLITERATED`, labelled "Transliterated, not a translation".

## Why the mapping is derived rather than invented

Ol Chiki letter names in Unicode encode their own phonetic values, in a regular
pattern. The six letters whose names begin with L are the vowels; every other
letter's name is its vowel row followed by its consonant:

    LA  LAA  LI  LU  LE  LO          the six vowels
    AT AG ANG AL                     t  g  ng l
    AAK AAJ AAM AAW                  k  j  m  w
    IS IH INY IR                     s  h  ny r
    UC UD UNN UY                     c  d  nn y
    EP EDD EN ERR                    p  dd n  rr
    OTT OB OV OH                     tt b  v  h

So the table below is read off the Unicode names, not guessed. Verify with
`python tools/olchiki_bridge.py --table`.

## The part that needs a speaker

Ol Chiki is alphabetic: every vowel is a full letter. Devanagari is an abugida:
a consonant carries an inherent 'a' and other vowels attach as matras. Going
from one to the other means deciding when a written Ol Chiki 'a' becomes an
explicit matra and when it is left as the inherent vowel.

This script always writes it explicitly, which is systematic and predictable.
A Santali speaker may prefer the inherent form in some words: the design
mockups render ᱟᱯᱟᱱ as आपन where this produces आपान. Both are readable; only a
speaker can say which a Jharkhand teacher would rather see.

**Treat the output as a draft for review, not as finished content.** It is
labelled TRANSLITERATED on screen for that reason.
"""
import argparse
import io
import json
import sys
import unicodedata

# Independent vowel, and the matra that attaches to a preceding consonant.
VOWELS = {
    'ᱚ': ('ओ', 'ो'),   # LA   o
    'ᱟ': ('आ', 'ा'),   # LAA  aa
    'ᱤ': ('इ', 'ि'),   # LI   i
    'ᱩ': ('उ', 'ु'),   # LU   u
    'ᱮ': ('ए', 'े'),   # LE   e
    'ᱳ': ('ओ', 'ो'),   # LO   o
}

CONSONANTS = {
    'ᱛ': 'त',   # AT   t
    'ᱜ': 'ग',   # AG   g
    'ᱝ': 'ङ',   # ANG  ng
    'ᱞ': 'ल',   # AL   l
    'ᱠ': 'क',   # AAK  k
    'ᱡ': 'ज',   # AAJ  j
    'ᱢ': 'म',   # AAM  m
    'ᱣ': 'व',   # AAW  w
    'ᱥ': 'स',   # IS   s
    'ᱦ': 'ह',   # IH   h
    'ᱧ': 'ञ',   # INY  ny
    'ᱨ': 'र',   # IR   r
    'ᱪ': 'च',   # UC   c
    'ᱫ': 'द',   # UD   d
    'ᱬ': 'ण',   # UNN  nn
    'ᱭ': 'य',   # UY   y
    'ᱯ': 'प',   # EP   p
    'ᱰ': 'ड',   # EDD  dd
    'ᱱ': 'न',   # EN   n
    'ᱲ': 'ड़',  # ERR  rr, Devanagari DDA + nukta
    'ᱴ': 'ट',   # OTT  tt
    'ᱵ': 'ब',   # OB   b
    'ᱶ': 'व',   # OV   v
    'ᱷ': 'ह',   # OH   h
}

# Modifiers and punctuation.
VIRAMA = '्'
MARKS = {
    'ᱸ': 'ं',   # MU TTUDDAG, nasalisation
    'ᱹ': '',         # GAAHLAA TTUDDAAG, glottal. No Devanagari equivalent.
    'ᱺ': 'ं',   # MU-GAAHLAA TTUDDAAG
    'ᱻ': '',         # RELAA
    'ᱼ': '',         # PHAARKAA
    'ᱽ': '',         # AHAD
    '᱾': '।',   # MUCAAD, danda
    '᱿': '॥',   # DOUBLE MUCAAD
}
DIGITS = {chr(0x1C50 + i): chr(0x0966 + i) for i in range(10)}


def transliterate(text):
    """Ol Chiki to Devanagari. Characters outside the block pass through."""
    out = []
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        if ch in CONSONANTS:
            out.append(CONSONANTS[ch])
            nxt = text[i + 1] if i + 1 < n else ''
            if nxt in VOWELS:
                out.append(VOWELS[nxt][1])   # matra
                i += 2
                continue
            # A consonant with no vowel after it takes a virama only inside a
            # word. At the end of a word Devanagari convention leaves the final
            # consonant bare, and a trailing virama reads as an error to anyone
            # who knows the script: जोहार्, not जोहार.
            if nxt in CONSONANTS:
                out.append(VIRAMA)
            i += 1
            continue
        if ch in VOWELS:
            out.append(VOWELS[ch][0])        # independent form
            i += 1
            continue
        if ch in MARKS:
            out.append(MARKS[ch])
            i += 1
            continue
        if ch in DIGITS:
            out.append(DIGITS[ch])
            i += 1
            continue
        out.append(ch)
        i += 1
    return ''.join(out)


def print_table():
    print('Ol Chiki letters, with the Unicode name the value is read from:\n')
    for cp in range(0x1C5A, 0x1C78):
        ch = chr(cp)
        name = unicodedata.name(ch).replace('OL CHIKI LETTER ', '')
        if ch in VOWELS:
            print('  %s  %-5s vowel       %s / %s' % (ch, name, VOWELS[ch][0], VOWELS[ch][1]))
        else:
            print('  %s  %-5s consonant   %s' % (ch, name, CONSONANTS.get(ch, '?')))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--table', action='store_true', help='print the derived mapping')
    ap.add_argument('--pack', help='pack json to add a devanagari bridge to')
    ap.add_argument('--write', action='store_true', help='actually write the pack')
    ap.add_argument('--text', help='transliterate one string and exit')
    a = ap.parse_args()

    if a.table:
        print_table()
        return
    if a.text:
        print(transliterate(a.text))
        return
    if not a.pack:
        ap.error('give --table, --text or --pack')

    d = json.load(io.open(a.pack, encoding='utf-8'))
    n = 0

    def walk(node):
        nonlocal n
        if isinstance(node, dict):
            if 'target' in node and isinstance(node['target'], str):
                if any(0x1C50 <= ord(c) <= 0x1C7F for c in node['target']):
                    node['bridge'] = transliterate(node['target'])
                    node['bridgeProvenance'] = 'transliterated'
                    n += 1
            for v in node.values():
                walk(v)
        elif isinstance(node, list):
            for v in node:
                walk(v)

    walk(d.get('entries', {}))
    if a.write:
        io.open(a.pack, 'w', encoding='utf-8', newline='\n').write(
            json.dumps(d, indent=2, ensure_ascii=False, sort_keys=True) + '\n')
        print('wrote %d bridges into %s' % (n, a.pack))
    else:
        print('%d entries would gain a bridge. Re-run with --write.' % n)


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()

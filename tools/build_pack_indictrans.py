# -*- coding: utf-8 -*-
"""
Build the real Santali content pack for Ol Saathi.

Runs AI4Bharat IndicTrans2 locally against an ungated mirror, so no Hugging
Face account and no Bhashini approval are needed.

Source is the human-written English already in the repo rather than a machine
translation of the Hindi. One model hop from clean text beats two hops with
compounding error, and the gated indic-indic model routes through English
internally anyway.

The important part is the contamination guard. A smoke test of three sentences
produced Arabic letters mid-sentence in one of them, so anything that is not Ol
Chiki, a space, a digit or basic punctuation is treated as a defect. Defective
outputs are retried with different decoding, and if they stay dirty the entry is
dropped rather than shipped. An entry that is absent shows the teacher
"no verified translation"; an entry full of Arabic looks correct and is not.

SETUP

    This produced the pack that ships today. It lives in the repo so the content
    is reproducible rather than something that happened once on a laptop.

    Needs Python 3.11, because torch has no 3.14 wheels, and transformers 4.x,
    because IndicTrans2 loads through trust_remote_code and that code predates
    transformers 5.

        py -3.11 -m venv C:/it2env
        C:/it2env/Scripts/pip install torch --index-url https://download.pytorch.org/whl/cpu
        C:/it2env/Scripts/pip install "transformers==4.46.3" sentencepiece einops
        C:/it2env/Scripts/python tools/build_pack_indictrans.py

    Keep the venv at a short path. A deep one breaks the install on Windows with
    WinError 206, filename too long.
"""
import json
import pathlib
import re
import statistics
import sys
import unicodedata as ud

import torch
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

REPO = pathlib.Path(__file__).resolve().parent.parent
OUT = REPO / 'app' / 'src' / 'main' / 'assets' / 'pack' / 'pack.sat.json'

# The distilled 200M contaminated 4 of 53 with Arabic, rendered every
# 'grandmother' as ayo (mother), and dropped 'knees' entirely. The 1B fixes
# all of it and uses native Santali vocabulary rather than Hindi loanwords:
# goṛom ayo for grandmother, kukli for question, hasu for pain.
MODEL = 'prajdabre/rotary-indictrans2-en-indic-1B'
SRC, TGT = 'eng_Latn', 'sat_Olck'

OLCK = range(0x1C50, 0x1C80)          # letters, digits and punctuation
ALLOWED_ASCII = set(' 0123456789.,?!:;-()\'"/')


def collect():
    """Everything the app needs translated, in pack-entry shape."""
    items = []
    ph = json.loads((REPO / 'tools' / 'phrases.hi.json').read_text(encoding='utf-8'))
    for p in ph['phrases']:
        items.append(dict(id=p['id'], hi=p['hi'], en=p['en'],
                          nipun=p['nipun'], kind='phrase'))
    ls = json.loads((REPO / 'content' / 'lessons.json').read_text(encoding='utf-8'))
    for L in ls['lessons']:
        for line in L.get('lines', []):
            items.append(dict(id='%s.%s' % (L['id'], line['id']), hi=line['hi'],
                              en=line.get('en', ''), nipun='FL-RD', kind='lesson',
                              lesson=L['id'], image=line.get('image', '')))
        for n, q in enumerate(L.get('checks', []), 1):
            items.append(dict(id='%s.c%d' % (L['id'], n), hi=q['hi'],
                              en=q.get('en', ''), nipun='FL-OL', kind='check',
                              lesson=L['id']))
    return items


def foreign(text):
    """Characters that have no business in Santali output."""
    bad = []
    for ch in text:
        if ord(ch) in OLCK or ch in ALLOWED_ASCII:
            continue
        bad.append(ch)
    return bad


def has_olck_letter(text):
    return any(0x1C5A <= ord(c) <= 0x1C77 for c in text)


def main():
    items = collect()
    print('strings to translate:', len(items))

    print('loading %s ...' % MODEL, flush=True)
    tok = AutoTokenizer.from_pretrained(MODEL, trust_remote_code=True)
    model = AutoModelForSeq2SeqLM.from_pretrained(MODEL, trust_remote_code=True).eval()
    print('  loaded, %.0fM params\n' % (sum(p.numel() for p in model.parameters()) / 1e6))

    def run(sentences, **gen):
        tagged = ['%s %s %s' % (SRC, TGT, s) for s in sentences]
        enc = tok(tagged, truncation=True, padding=True, max_length=256, return_tensors='pt')
        with torch.no_grad():
            out = model.generate(**enc, max_length=256, min_length=0, **gen)
        return tok.batch_decode(out, skip_special_tokens=True)

    # first pass
    targets = []
    B = 8
    for i in range(0, len(items), B):
        chunk = [it['en'] for it in items[i:i + B]]
        targets += run(chunk, num_beams=5)
        print('  %d/%d' % (min(i + B, len(items)), len(items)), end='\r', flush=True)
    print('  %d/%d translated' % (len(targets), len(items)))

    # retry anything contaminated, with different decoding
    dirty = [n for n, t in enumerate(targets) if foreign(t) or not has_olck_letter(t)]
    print('\ncontaminated on first pass: %d' % len(dirty))
    for n in dirty:
        for attempt in ({'num_beams': 1}, {'num_beams': 10}, {'num_beams': 4, 'no_repeat_ngram_size': 3}):
            got = run([items[n]['en']], **attempt)[0]
            if not foreign(got) and has_olck_letter(got):
                print('  recovered %-14s with %s' % (items[n]['id'], attempt))
                targets[n] = got
                break
        else:
            print('  UNRECOVERABLE %-14s %r' % (items[n]['id'], targets[n][:50]))

    # ── checks ────────────────────────────────────────────────────────
    print('\n' + '=' * 62)
    bad_foreign, bad_noolck, dropped = [], [], []
    for it, t in zip(items, targets):
        f = foreign(t)
        if f:
            bad_foreign.append((it, t, f))
            dropped.append(it['id'])
        elif not has_olck_letter(t):
            bad_noolck.append((it, t))
            dropped.append(it['id'])

    seen, collisions = {}, []
    for it, t in zip(items, targets):
        if it['id'] in dropped:
            continue
        if t in seen and seen[t] != it['en']:
            collisions.append((seen[t], it['en'], t))
        seen[t] = it['en']

    digits_inside = [(it['id'], t) for it, t in zip(items, targets)
                     if it['id'] not in dropped
                     and any(0x1C50 <= ord(c) <= 0x1C59 for c in re.sub(r'\s', '', t))]

    ratios = [len(t) / max(1, len(it['en'])) for it, t in zip(items, targets)
              if it['id'] not in dropped]
    med = statistics.median(ratios) if ratios else 0

    print('foreign script contamination : %d  (dropped)' % len(bad_foreign))
    print('no Ol Chiki at all           : %d  (dropped)' % len(bad_noolck))
    print('distinct sources colliding   : %d' % len(collisions))
    print('Ol Chiki digits inside words : %d' % len(digits_inside))
    print('median en->sat char ratio    : %.2f' % med)
    print('usable entries               : %d of %d' % (len(items) - len(dropped), len(items)))

    for it, t, f in bad_foreign[:5]:
        names = ', '.join(sorted({ud.name(c, '?').split(' LETTER')[0] for c in f}))
        print('   dropped %-14s %s   [%s]' % (it['id'], t[:38], names))
    for a, b, t in collisions[:3]:
        print('   collision: %r and %r -> %s' % (a[:28], b[:28], t[:28]))

    print('\nRead these:')
    shown = 0
    for it, t in zip(items, targets):
        if it['id'] in dropped:
            continue
        print('   %-38s' % it['en'][:37])
        print('     %s' % t)
        shown += 1
        if shown >= 10:
            break

    # ── write ─────────────────────────────────────────────────────────
    import datetime
    now = datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00', 'Z')
    pack = {
        'language': 'sat', 'source': 'hi', 'generated': now,
        'provenance': {
            'translationService': MODEL,
            'ttsService': None,
            'platform': 'AI4Bharat IndicTrans2, ungated MIT mirror. Same model family '
                        'Bhashini serves for this language pair.',
            'pivot': 'Human-authored English, machine translated to Santali',
            'note': 'Machine translation. Not checked by a Santali speaker. '
                    'Entries whose output contained non-Ol-Chiki characters were '
                    'dropped rather than shipped.',
            'dropped': dropped,
        },
        'entries': {},
    }
    for it, t in zip(items, targets):
        if it['id'] in dropped:
            continue
        e = dict(source=it['hi'], target=t, en=it['en'], nipun=it['nipun'],
                 kind=it['kind'], service=MODEL, at=now)
        if it.get('lesson'):
            e['lesson'] = it['lesson']
        if it.get('image'):
            e['image'] = it['image']
        pack['entries'][it['id']] = e

    OUT.write_text(json.dumps(pack, ensure_ascii=False, indent=2), encoding='utf-8')
    print('\nwrote %s with %d entries' % (OUT.name, len(pack['entries'])))


if __name__ == '__main__':
    sys.exit(main())

# -*- coding: utf-8 -*-
"""
Rank the pack by how likely each translation is to be wrong.

We cannot read Santali, so we cannot check the pack. What we can do is send it
back the other way: translate the shipped Ol Chiki into English with the reverse
IndicTrans2 model and compare that against the English we started from. Where
the round trip comes back saying something different, the translation is
suspect.

This proves nothing on its own. A good translation can round-trip badly and a
bad one can round-trip well. What it does is **rank suspicion**, which is worth
a great deal, because it means the one hour of a Santali speaker's time goes to
the twelve worst entries instead of being spent alphabetically.

Writes back-translation-report.json, and the review sheet reads it to sort
worst-first.

SETUP
    Same environment as tools/build_pack_indictrans.py.

        C:/it2env/Scripts/python tools/backtranslate_qa.py
"""
import difflib
import json
import pathlib
import re
import sys

import torch
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

REPO = pathlib.Path(__file__).resolve().parent.parent
PACK = REPO / 'app' / 'src' / 'main' / 'assets' / 'pack' / 'pack.sat.json'
OUT = REPO / 'verification' / 'back-translation-report.json'

# The reverse of the model that built the pack. Ungated, complete tokenizer.
MODEL = 'prajdabre/rotary-indictrans2-indic-en-1B'
SRC, TGT = 'sat_Olck', 'eng_Latn'

STOP = {'a', 'an', 'the', 'is', 'are', 'was', 'were', 'be', 'to', 'of', 'in',
        'on', 'at', 'it', 'this', 'that', 'do', 'does', 'did', 'your', 'you'}


def words(text):
    return [w for w in re.findall(r"[a-z']+", text.lower()) if w not in STOP]


def similarity(a, b):
    """0 to 1. Content-word overlap and sequence similarity, averaged."""
    wa, wb = set(words(a)), set(words(b))
    jaccard = len(wa & wb) / len(wa | wb) if (wa or wb) else 0.0
    seq = difflib.SequenceMatcher(None, a.lower(), b.lower()).ratio()
    return round((jaccard + seq) / 2, 3)


def main():
    pack = json.loads(PACK.read_text(encoding='utf-8'))
    entries = pack['entries']
    ids = sorted(entries)
    print('pack        :', PACK.name, '|', len(ids), 'entries')
    print('forward     :', pack['provenance'].get('translationService'))
    print('backward    :', MODEL)
    print()

    print('loading the reverse model, this pulls ~4.5 GB the first time ...', flush=True)
    tok = AutoTokenizer.from_pretrained(MODEL, trust_remote_code=True)
    model = AutoModelForSeq2SeqLM.from_pretrained(MODEL, trust_remote_code=True).eval()
    print('  %.0fM params' % (sum(p.numel() for p in model.parameters()) / 1e6))

    def back(sentences, batch=8):
        out = []
        for i in range(0, len(sentences), batch):
            chunk = ['%s %s %s' % (SRC, TGT, s) for s in sentences[i:i + batch]]
            enc = tok(chunk, truncation=True, padding=True, max_length=256,
                      return_tensors='pt')
            with torch.no_grad():
                gen = model.generate(**enc, num_beams=5, max_length=256, min_length=0)
            out += tok.batch_decode(gen, skip_special_tokens=True)
            print('  %d/%d' % (min(i + batch, len(sentences)), len(sentences)),
                  end='\r', flush=True)
        return out

    print('back-translating ...', flush=True)
    returned = back([entries[i]['target'] for i in ids])
    print('  %d/%d done' % (len(returned), len(ids)))

    rows = []
    for eid, got in zip(ids, returned):
        e = entries[eid]
        rows.append({
            'id': eid,
            'kind': e.get('kind', ''),
            'original_en': e.get('en', ''),
            'santali': e['target'],
            'back_en': got,
            'score': similarity(e.get('en', ''), got),
        })
    rows.sort(key=lambda r: r['score'])

    scores = [r['score'] for r in rows]
    median = sorted(scores)[len(scores) // 2]
    weak = [r for r in rows if r['score'] < 0.5]

    print()
    print('=' * 68)
    print('median round-trip similarity : %.2f' % median)
    print('below 0.50 (worth a look)    : %d of %d' % (len(weak), len(rows)))
    print()
    print('The twelve most suspicious, worst first:')
    print()
    for r in rows[:12]:
        print('  %-16s score %.2f' % (r['id'], r['score']))
        print('    we wrote   : %s' % r['original_en'])
        print('    came back  : %s' % r['back_en'])
        print()

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({
        'forward': pack['provenance'].get('translationService'),
        'backward': MODEL,
        'median': median,
        'note': 'Round-trip similarity ranks suspicion, it does not measure '
                'correctness. A low score means a human should look, not that '
                'the translation is wrong.',
        'rows': rows,
    }, ensure_ascii=False, indent=2), encoding='utf-8')
    print('wrote %s' % OUT.relative_to(REPO))
    print('Now rerun tools/make_verification_sheet.py to sort the review worst-first.')


if __name__ == '__main__':
    sys.exit(main())

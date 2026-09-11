# -*- coding: utf-8 -*-
"""
Try several English wordings of one phrase and report which survive the guard.

    python tools/probe_phrase.py --langs ben,tam,guj \
        "What is your name?" "Tell me your name." "What are you called?"

WHY THIS EXISTS
---------------
"What is your name?" was dropped in 13 of the 17 shipped languages, every time
for Latin contamination: the model reaches for a romanised placeholder when it
translates the idea of a name. One phrase, dropped almost everywhere, and it is
among the most useful sentences a teacher says on the first day of school.

Losing an entry is the correct behaviour once the model has produced something
defective; the pack must never ship it. But the model is not the only variable.
The English source is written by us, and a different wording often decodes
cleanly. This tool makes that a two-minute experiment instead of a guess.

Nothing is written. Read the table, pick a wording that survives across the
languages you care about, edit tools/phrases.hi.json, and rebuild.

Uses the same virtualenv as the pack builder:

    .venv-tts/Scripts/python tools/probe_phrase.py --langs ben,tam "..." "..."
"""
from __future__ import annotations

import argparse
import pathlib
import sys

import torch
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import langs  # noqa: E402

MODEL = 'prajdabre/rotary-indictrans2-en-indic-1B'
SRC = 'eng_Latn'


def main():
    ap = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('wordings', nargs='+', help='English wordings to compare')
    ap.add_argument('--langs', default='ben,tam,guj,mar,ory',
                    help='comma separated language codes to test against')
    ap.add_argument('--model', default=MODEL)
    args = ap.parse_args()

    codes = [c.strip() for c in args.langs.split(',') if c.strip()]
    specs = {c: langs.spec(c) for c in codes}

    print('loading %s ...' % args.model, flush=True)
    tok = AutoTokenizer.from_pretrained(args.model, trust_remote_code=True)
    model = AutoModelForSeq2SeqLM.from_pretrained(
        args.model, trust_remote_code=True).eval()
    print()

    def translate(text, sp):
        tagged = '%s %s %s' % (SRC, sp['tag'], text)
        enc = tok([tagged], truncation=True, padding=True, max_length=256,
                  return_tensors='pt')
        with torch.no_grad():
            out = model.generate(**enc, max_new_tokens=96, min_length=0,
                                 num_beams=5)
        return langs.to_target_script(
            tok.batch_decode(out, skip_special_tokens=True)[0], sp)

    best, results = None, []
    for wording in args.wordings:
        clean, dirty = [], []
        rows = []
        for c in codes:
            sp = specs[c]
            got = translate(wording, sp)
            bad = langs.foreign(got, sp)
            has = langs.has_script_letter(got, sp)
            ok = not bad and has
            (clean if ok else dirty).append(c)
            rows.append((c, ok, got, ''.join(sorted(set(bad)))[:12]))
        results.append((wording, len(clean), len(codes), rows))
        if best is None or len(clean) > best[1]:
            best = (wording, len(clean))

    for wording, n_ok, n_all, rows in results:
        print('=' * 66)
        print('%-52s %d/%d clean' % ('"%s"' % wording, n_ok, n_all))
        print('=' * 66)
        for c, ok, got, bad in rows:
            mark = 'ok  ' if ok else 'DROP'
            note = ('  <- %s' % bad) if bad else ''
            print('  %s %-4s %s%s' % (mark, c, got[:44], note))
        print()

    print('Best wording: "%s" (%d of %d clean)' % (best[0], best[1], len(codes)))
    print('If that beats what is in tools/phrases.hi.json, edit the "en" field')
    print('there and rebuild those languages. Leave the Hindi alone: the teacher')
    print('reads the Hindi, and only the English is a translation pivot.')
    return 0


if __name__ == '__main__':
    sys.exit(main())

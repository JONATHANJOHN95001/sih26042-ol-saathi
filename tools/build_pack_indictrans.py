# -*- coding: utf-8 -*-
"""
Build a content pack for Ol Saathi, in any language IndicTrans2 can reach.

Runs AI4Bharat IndicTrans2 locally against an ungated mirror, so no Hugging
Face account and no Bhashini approval are needed.

Source is the human-written English already in the repo rather than a machine
translation of the Hindi. One model hop from clean text beats two hops with
compounding error, and the gated indic-indic model routes through English
internally anyway.

    python tools/build_pack_indictrans.py                  # Santali, the default
    python tools/build_pack_indictrans.py --lang mar       # Marathi
    python tools/build_pack_indictrans.py --all-devanagari # the whole free set

Every language known to the build lives in tools/langs.py. Adding one is an
edit there, not here.

THE CONTAMINATION GUARD
-----------------------
A smoke test of three sentences once produced Arabic letters mid-sentence, so
anything outside the target script is treated as a defect. Defective outputs
are retried with different decoding, and if they stay dirty the entry is
dropped rather than shipped. An entry that is absent shows the teacher
"no verified translation"; an entry full of Arabic looks correct and is not.

That guard is per-script and lives in tools/langs.py, because a guard hardcoded
to Ol Chiki would wave every other language straight through.

THE HONEST ASYMMETRY BETWEEN LANGUAGES
--------------------------------------
Santali gets a strong guarantee. Source is Devanagari, target is Ol Chiki, so
an untranslated echo of the Hindi contains no Ol Chiki at all and is caught.

Marathi, Nepali, Bodo and the rest of the Devanagari family get a weaker one,
because source and target share a script and an echo is valid Devanagari. The
echo check catches a verbatim passthrough, but it cannot tell fluent Marathi
from fluent Hindi. Each pack records which guarantee it got, and the app shows
it, so nobody mistakes the weak case for the strong one.

SETUP

    Needs Python 3.11 and transformers 4.x, because IndicTrans2 loads through
    trust_remote_code and that code predates transformers 5. The TTS virtualenv
    already satisfies both:

        .venv-tts/Scripts/python tools/build_pack_indictrans.py --lang mar

    Keep any new venv at a short path. A deep one breaks the install on
    Windows with WinError 206, filename too long.
"""
import argparse
import datetime
import json
import pathlib
import statistics
import sys
import unicodedata as ud

import torch
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import langs  # noqa: E402

REPO = pathlib.Path(__file__).resolve().parent.parent
PACK_DIR = REPO / 'app' / 'src' / 'main' / 'assets' / 'pack'
FONT_DIR = REPO / 'app' / 'src' / 'main' / 'assets' / 'fonts'

# The distilled 200M contaminated 4 of 53 with Arabic, rendered every
# 'grandmother' as ayo (mother), and dropped 'knees' entirely. The 1B fixes
# all of it and uses native Santali vocabulary rather than Hindi loanwords:
# goṛom ayo for grandmother, kukli for question, hasu for pain.
MODEL = 'prajdabre/rotary-indictrans2-en-indic-1B'
SRC = 'eng_Latn'


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


def defects(target, source, sp):
    """
    Everything wrong with one translation, as a list of reasons.

    Empty means shippable. The reasons are strings rather than a boolean so
    the run report can say which guard fired, because "23 dropped" tells you
    nothing about whether the model is contaminating or merely echoing.
    """
    out = []
    f = langs.foreign(target, sp)
    if f:
        names = sorted({ud.name(c, '?').split(' LETTER')[0] for c in f})
        out.append('foreign script (%s)' % ', '.join(names)[:60])
    if not langs.has_script_letter(target, sp):
        out.append('no %s letters at all' % sp['script_label'])
    if sp['shares_script_with_source'] and langs.echoes_source(target, source):
        out.append('echoed the Hindi unchanged')
    return out


def write_manifest():
    """
    Regenerate packs.json by looking at which packs actually exist.

    Derived from the directory rather than from a list someone maintains, so
    the manifest cannot claim a language the APK does not carry. Fonts are
    checked the same way: a pack whose script has no font on disk is recorded
    as unavailable rather than offered in a dropdown that would render boxes.
    """
    entries = []
    for path in sorted(PACK_DIR.glob('pack.*.json')):
        code = path.name[len('pack.'):-len('.json')]
        try:
            sp = langs.spec(code)
        except KeyError:
            print('  manifest: skipping unknown language file %s' % path.name)
            continue
        data = json.loads(path.read_text(encoding='utf-8'))
        font_ok = (FONT_DIR / sp['font']).exists()
        n_audio = sum(1 for e in data.get('entries', {}).values() if e.get('audio'))
        entries.append(dict(
            code=code,
            english=sp['english'],
            endonym=sp['endonym'],
            display=sp['endonym'] or sp['english'],
            script=sp['script'],
            scriptLabel=sp['script_label'],
            font=sp['font'],
            fontBundled=font_ok,
            entries=len(data.get('entries', {})),
            audioEntries=n_audio,
            guard='strong' if not sp['shares_script_with_source'] else 'weak-shared-script',
            available=font_ok and bool(data.get('entries')),
        ))
    manifest = {
        'generated': datetime.datetime.now(datetime.timezone.utc)
                     .isoformat().replace('+00:00', 'Z'),
        'source': 'hi',
        'note': 'Written by tools/build_pack_indictrans.py from the packs that '
                'exist on disk. A language with no bundled font is listed as '
                'unavailable rather than offered.',
        'languages': entries,
    }
    out = PACK_DIR / 'packs.json'
    out.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n',
                   encoding='utf-8')
    ok = sum(1 for e in entries if e['available'])
    print('\nmanifest: %d packs, %d available' % (len(entries), ok))
    for e in entries:
        flag = '' if e['available'] else '   UNAVAILABLE, font missing: %s' % e['font']
        print('   %-4s %-9s %-6s %3d entries, %3d with audio%s'
              % (e['code'], e['english'], e['script'], e['entries'],
                 e['audioEntries'], flag))
    return manifest


def build_one(code, tok, model, items):
    """Translate everything into one language and write its pack."""
    sp = langs.spec(code)
    tgt = sp['tag']
    print('\n' + '=' * 62)
    print('%s (%s), script %s, guard %s'
          % (sp['english'], code, sp['script_label'],
             'weak, shares script with source' if sp['shares_script_with_source']
             else 'strong'))
    print('=' * 62)

    if not (FONT_DIR / sp['font']).exists():
        print('WARNING: %s is not in assets/fonts. The pack will build, but the '
              'app will list this language as unavailable rather than render '
              'boxes.' % sp['font'])

    def run(sentences, **gen):
        tagged = ['%s %s %s' % (SRC, tgt, s) for s in sentences]
        enc = tok(tagged, truncation=True, padding=True, max_length=256,
                  return_tensors='pt')
        with torch.no_grad():
            # max_NEW_tokens, not max_length. Sanskrit exposed why: the model
            # never emitted a stop token for it, so every sentence ran to the
            # 256-token ceiling and one language took an hour to reach entry
            # 16 of 53 while Nepali finished all 53 in ten minutes. These are
            # one-line classroom sentences; 96 new tokens is already generous,
            # and anything longer is a decoding failure rather than a
            # translation worth waiting for.
            out = model.generate(**enc, max_new_tokens=96, min_length=0, **gen)
        decoded = tok.batch_decode(out, skip_special_tokens=True)
        # IndicTrans2 emits Devanagari for most Indic targets and relies on its
        # official pipeline to transliterate back. Without this, Bengali came
        # out as correct Bengali words spelled in Devanagari and the guard
        # dropped all 53. Converted here rather than after the retry loop so
        # the defect check sees the same text that will be shipped.
        return [langs.to_target_script(t, sp) for t in decoded]

    targets = []
    B = 8
    for i in range(0, len(items), B):
        chunk = [it['en'] for it in items[i:i + B]]
        targets += run(chunk, num_beams=5)
        print('  %d/%d' % (min(i + B, len(items)), len(items)), end='\r', flush=True)
    print('  %d/%d translated' % (len(targets), len(items)))

    dirty = [n for n, t in enumerate(targets)
             if defects(t, items[n]['hi'], sp)]
    print('contaminated on first pass: %d' % len(dirty))
    for n in dirty:
        for attempt in ({'num_beams': 1}, {'num_beams': 10},
                        {'num_beams': 4, 'no_repeat_ngram_size': 3}):
            got = run([items[n]['en']], **attempt)[0]
            if not defects(got, items[n]['hi'], sp):
                print('  recovered %-14s with %s' % (items[n]['id'], attempt))
                targets[n] = got
                break
        else:
            print('  UNRECOVERABLE %-14s %r' % (items[n]['id'], targets[n][:50]))

    # ── checks ────────────────────────────────────────────────────────
    dropped, reasons = [], {}
    for it, t in zip(items, targets):
        d = defects(t, it['hi'], sp)
        if d:
            dropped.append(it['id'])
            reasons[it['id']] = d

    seen, collisions = {}, []
    for it, t in zip(items, targets):
        if it['id'] in dropped:
            continue
        if t in seen and seen[t] != it['en']:
            collisions.append((seen[t], it['en'], t))
        seen[t] = it['en']

    digits_inside = [it['id'] for it, t in zip(items, targets)
                     if it['id'] not in dropped and langs.digits_inside_word(t, sp)]

    ratios = [len(t) / max(1, len(it['en'])) for it, t in zip(items, targets)
              if it['id'] not in dropped]
    med = statistics.median(ratios) if ratios else 0

    by_reason = {}
    for rs in reasons.values():
        for r in rs:
            key = r.split(' (')[0]
            by_reason[key] = by_reason.get(key, 0) + 1

    print('\ndropped                      : %d' % len(dropped))
    for k, v in sorted(by_reason.items()):
        print('   %-26s %d' % (k, v))
    print('distinct sources colliding   : %d' % len(collisions))
    print('digits inside words          : %d' % len(digits_inside))
    print('median en->%-4s char ratio    : %.2f' % (code, med))
    print('usable entries               : %d of %d' % (len(items) - len(dropped), len(items)))

    for eid in dropped[:5]:
        print('   dropped %-14s %s' % (eid, '; '.join(reasons[eid])[:60]))

    print('\nRead these:')
    shown = 0
    for it, t in zip(items, targets):
        if it['id'] in dropped:
            continue
        print('   %-38s' % it['en'][:37])
        print('     %s' % t)
        shown += 1
        if shown >= 5:
            break

    # ── write ─────────────────────────────────────────────────────────
    now = datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00', 'Z')
    guard_note = (
        'Machine translation. Not checked by a %s speaker. Entries whose '
        'output left the %s block were dropped rather than shipped.'
        % (sp['english'], sp['script_label']))
    if sp['shares_script_with_source']:
        guard_note += (
            ' This language shares Devanagari with the Hindi source, so the '
            'script guard is weaker here than for Ol Chiki: it catches a '
            'verbatim echo of the Hindi, but it cannot tell fluent %s from '
            'fluent Hindi.' % sp['english'])

    pack = {
        'language': code,
        'languageEnglish': sp['english'],
        'languageEndonym': sp['endonym'],
        'script': sp['script'],
        'font': sp['font'],
        'guard': 'weak-shared-script' if sp['shares_script_with_source'] else 'strong',
        'source': 'hi',
        'generated': now,
        'provenance': {
            'translationService': MODEL,
            'ttsService': None,
            'platform': 'AI4Bharat IndicTrans2, ungated MIT mirror. Same model family '
                        'Bhashini serves for this language pair.',
            'pivot': 'Human-authored English, machine translated to %s' % sp['english'],
            'note': guard_note,
            'dropped': dropped,
            'droppedReasons': reasons,
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

    out = PACK_DIR / ('pack.%s.json' % code)
    out.write_text(json.dumps(pack, ensure_ascii=False, indent=2), encoding='utf-8')
    print('\nwrote %s with %d entries' % (out.name, len(pack['entries'])))
    return len(pack['entries'])


def main():
    ap = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--lang', default='sat',
                    help='language code from tools/langs.py (default: sat)')
    ap.add_argument('--all-devanagari', action='store_true',
                    help='build every Devanagari language, which needs no new font')
    ap.add_argument('--langs', default='',
                    help='comma separated codes, overrides --lang')
    ap.add_argument('--model', default=MODEL, help='translation model id')
    ap.add_argument('--manifest-only', action='store_true',
                    help='rewrite packs.json from the packs already on disk, '
                         'without loading the model')
    args = ap.parse_args()

    if args.manifest_only:
        write_manifest()
        return 0

    if args.langs:
        codes = [c.strip() for c in args.langs.split(',') if c.strip()]
    elif args.all_devanagari:
        codes = [c for c in langs.LANGUAGES if langs.spec(c)['script'] == 'Deva']
    else:
        codes = [args.lang]

    for c in codes:
        langs.spec(c)  # fail fast on a typo, before loading 4 GB

    items = collect()
    print('strings to translate: %d' % len(items))
    print('languages           : %s' % ', '.join(codes))

    print('\nloading %s ...' % args.model, flush=True)
    tok = AutoTokenizer.from_pretrained(args.model, trust_remote_code=True)
    model = AutoModelForSeq2SeqLM.from_pretrained(
        args.model, trust_remote_code=True).eval()
    print('  loaded, %.0fM params' % (sum(p.numel() for p in model.parameters()) / 1e6))

    for c in codes:
        build_one(c, tok, model, items)

    write_manifest()
    return 0


if __name__ == '__main__':
    sys.exit(main())

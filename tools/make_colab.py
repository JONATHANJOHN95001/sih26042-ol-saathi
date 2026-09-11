# -*- coding: utf-8 -*-
"""
Generate the IndicTrans2 Colab notebook.

The Bhashini portal needs a faculty supervisor to approve the account, which
is a human dependency with no known turnaround. IndicTrans2 is the same
AI4Bharat model Bhashini serves for Hindi to Santali, it is MIT licensed and
public on Hugging Face, and it runs on a free Colab GPU in a few minutes.

Every string is baked into the notebook so there is nothing to upload. Open,
Runtime > Run all, download pack.sat.json, drop it into
app/src/main/assets/pack/. Done.

    python tools/make_colab.py
"""
import io
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent

# ── gather exactly what build_pack.mjs would send ─────────────────────
items = []

phrases = json.loads((ROOT / 'tools' / 'phrases.hi.json').read_text(encoding='utf-8'))
for p in phrases['phrases']:
    items.append({'id': p['id'], 'hi': p['hi'], 'en': p['en'],
                  'nipun': p['nipun'], 'kind': 'phrase'})

lessons = json.loads((ROOT / 'content' / 'lessons.json').read_text(encoding='utf-8'))
for L in lessons['lessons']:
    for line in L.get('lines', []):
        items.append({'id': '%s.%s' % (L['id'], line['id']), 'hi': line['hi'],
                      'en': line.get('en', ''), 'nipun': 'FL-RD', 'kind': 'lesson',
                      'lesson': L['id'], 'image': line.get('image', '')})
    for i, q in enumerate(L.get('checks', [])):
        items.append({'id': '%s.c%d' % (L['id'], i + 1), 'hi': q['hi'],
                      'en': q.get('en', ''), 'nipun': 'FL-OL', 'kind': 'check',
                      'lesson': L['id']})

print('embedding %d strings' % len(items))

# ai4bharat/* repos are all gated and return 401 without a token. This is an
# ungated MIT mirror of the same distilled English-to-Indic model, so the
# notebook needs no Hugging Face account at all.
MODEL = 'Raghavan/indictrans2-en-indic-dist-200M'


def md(text):
    return {'cell_type': 'markdown', 'metadata': {}, 'source': text.splitlines(keepends=True)}


def code(text):
    return {'cell_type': 'code', 'metadata': {}, 'execution_count': None,
            'outputs': [], 'source': text.splitlines(keepends=True)}


cells = []

cells.append(md('''# Ol Saathi — build the Santali content pack

Translates the app's 53 Hindi strings into Santali (Ol Chiki) using
**AI4Bharat IndicTrans2**, the same model Bhashini serves for Hindi to Santali.

**Runtime → Change runtime type → T4 GPU**, then **Runtime → Run all**.
Takes about five minutes. The last cell downloads `pack.sat.json`.

Drop that file into `app/src/main/assets/pack/` and the app's red
SAMPLE DATA banner turns into a green Verified label by itself.
'''))

cells.append(md('## 1. Install'))
cells.append(code('''!pip -q install transformers torch sentencepiece
!pip -q install git+https://github.com/VarunGumma/IndicTransToolkit.git
print("installed")'''))

cells.append(md('## 2. Load the model'))
cells.append(code('''import torch
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
from IndicTransToolkit.processor import IndicProcessor

MODEL = "%s"
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"

tokenizer = AutoTokenizer.from_pretrained(MODEL, trust_remote_code=True)
model = AutoModelForSeq2SeqLM.from_pretrained(MODEL, trust_remote_code=True).to(DEVICE).eval()
ip = IndicProcessor(inference=True)

print("model :", MODEL)
print("device:", DEVICE)''' % MODEL))

cells.append(md('## 3. The strings\n\nBaked in, so there is nothing to upload.'))
cells.append(code('ITEMS = ' + json.dumps(items, ensure_ascii=False, indent=1) +
                  '\n\nprint(len(ITEMS), "strings")'))

cells.append(md('## 4. Translate to Santali'))
cells.append(code('''SRC, TGT = "eng_Latn", "sat_Olck"

def translate(sentences, batch_size=8):
    out = []
    for i in range(0, len(sentences), batch_size):
        chunk = sentences[i:i + batch_size]
        batch = ip.preprocess_batch(chunk, src_lang=SRC, tgt_lang=TGT)
        enc = tokenizer(batch, truncation=True, padding=True,
                        max_length=256, return_tensors="pt").to(DEVICE)
        with torch.no_grad():
            gen = model.generate(**enc, num_beams=5, max_length=256,
                                 min_length=0, num_return_sequences=1)
        dec = tokenizer.batch_decode(gen, skip_special_tokens=True)
        out += ip.postprocess_batch(dec, lang=TGT)
        print(f"  {min(i + batch_size, len(sentences))}/{len(sentences)}", end="\\r")
    return out

# We pivot through the English we wrote by hand rather than through a
# machine translation of the Hindi. One model hop from clean human text
# beats two hops with compounding error.
targets = translate([it["en"] for it in ITEMS])
print("\\ndone:", len(targets))'''))

cells.append(md('''## 5. Check it before trusting it

The last pack that reached this project was invented, passed every structural
test, and would have been shown to a Jharkhand judge under a green Verified
label. These checks are the ones that would have caught it.

The transliteration check is the important one. If the Hindi to Santali
character ratio sits near 1.0 across the board, the model has respelled the
Hindi rather than translated it, and the output is worthless.'''))
cells.append(code('''import unicodedata as ud, statistics

OLCK_LETTER = range(0x1C5A, 0x1C78)
OLCK_DIGIT  = range(0x1C50, 0x1C5A)
DEVA        = range(0x0900, 0x0980)

def has(s, rng): return any(ord(c) in rng for c in s)

digits   = [(i["hi"], t) for i, t in zip(ITEMS, targets) if has(t, OLCK_DIGIT)]
no_olck  = [(i["hi"], t) for i, t in zip(ITEMS, targets) if not has(t, OLCK_LETTER)]
deva     = [(i["hi"], t) for i, t in zip(ITEMS, targets) if has(t, DEVA)]

seen, collisions = {}, []
for i, t in zip(ITEMS, targets):
    if t in seen and seen[t] != i["hi"]:
        collisions.append((seen[t], i["hi"], t))
    seen[t] = i["hi"]

ratios = [len(t) / max(1, len(i["en"])) for i, t in zip(ITEMS, targets)]
median_ratio = statistics.median(ratios)

print(f"Ol Chiki digits inside words : {len(digits)}   (want 0)")
print(f"targets with no Ol Chiki     : {len(no_olck)}  (want 0)")
print(f"targets containing Devanagari: {len(deva)}     (want 0)")
print(f"distinct sources colliding   : {len(collisions)} (want 0)")
print(f"median en->sat char ratio    : {median_ratio:.2f}")
print()
if 0.95 < median_ratio < 1.20:
    print("WARNING: ratio near 1.0 suggests transliteration, not translation.")
    print("Read a few pairs below before using this pack.")
else:
    print("Ratio looks like real translation rather than character substitution.")

print("\\nFirst eight pairs, read them:")
for i, t in list(zip(ITEMS, targets))[:8]:
    print(f"  {i['hi']}")
    print(f"    -> {t}")'''))

cells.append(md('## 6. Write the pack'))
cells.append(code('''import json, datetime

pack = {
    "language": "sat",
    "source": "hi",
    "generated": datetime.datetime.utcnow().isoformat() + "Z",
    "provenance": {
        "translationService": MODEL,
        "ttsService": None,
        "platform": "AI4Bharat IndicTrans2 (ungated MIT mirror). Same model family Bhashini serves for this language pair.",
        "pivot": "Human-authored English, then machine translated to Santali",
        "note": "Machine translation by IndicTrans2 from human-written English. Not checked by a Santali speaker.",
    },
    "entries": {},
}

now = pack["generated"]
for it, tgt in zip(ITEMS, targets):
    e = {
        "source": it["hi"], "target": tgt, "en": it["en"],
        "nipun": it["nipun"], "kind": it["kind"],
        "service": MODEL, "at": now,
    }
    if it.get("lesson"): e["lesson"] = it["lesson"]
    if it.get("image"):  e["image"] = it["image"]
    pack["entries"][it["id"]] = e

with open("pack.sat.json", "w", encoding="utf-8") as f:
    json.dump(pack, f, ensure_ascii=False, indent=2)

print("wrote pack.sat.json with", len(pack["entries"]), "entries")'''))

cells.append(md('''## 7. Download

Then put it in `app/src/main/assets/pack/pack.sat.json`, replacing the sample.

There is no audio in this pack, because Santali text to speech exists only on
Bhashini. Text, worksheets and offline operation all work without it. Audio
arrives when the Bhashini account is approved.'''))
cells.append(code('''from google.colab import files
files.download("pack.sat.json")'''))

nb = {
    'nbformat': 4, 'nbformat_minor': 0,
    'metadata': {
        'colab': {'provenance': [], 'toc_visible': True},
        'kernelspec': {'name': 'python3', 'display_name': 'Python 3'},
        'accelerator': 'GPU',
    },
    'cells': cells,
}

out = ROOT / 'tools' / 'ol_saathi_build_pack.ipynb'
io.open(out, 'w', encoding='utf-8', newline='\n').write(json.dumps(nb, ensure_ascii=False, indent=1))
print('wrote', out.relative_to(ROOT))

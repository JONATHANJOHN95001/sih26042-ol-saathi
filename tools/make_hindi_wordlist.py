# -*- coding: utf-8 -*-
"""
Write the Hindi word list the app uses to repair text pulled out of NCERT PDFs.

Why
---
NCERT's Hindi PDFs set type in Kokila, and the font's Unicode map drops the
halant from some conjuncts: extraction gives कयों for क्यों, चपपलें for
चप्पलें, जलदी for जल्दी. Printed on a worksheet those are misspellings in
front of children learning to read, and a translation model turns them into
nonsense. Which spelling is right can only be decided with a word list.

Where the words come from
-------------------------
The target vocabulary of AI4Bharat IndicTrans2 (MIT), the model the packs are
already translated with. Its entries are ordered by frequency in the model's
training text (है is 8th, क्या 198th, the broken कया 11,544th), so the line
number in the output is a frequency rank and the app can prefer the common
spelling. Only whole words in Devanagari are kept.

    .venv-tts/Scripts/python tools/make_hindi_wordlist.py
"""
import json
import pathlib
import re

REPO = pathlib.Path(__file__).resolve().parent.parent
SNAP = pathlib.Path.home() / '.cache' / 'huggingface' / 'hub' / \
    'models--prajdabre--rotary-indictrans2-en-indic-1B' / 'snapshots'
OUT = REPO / 'app' / 'src' / 'main' / 'assets' / 'hindi' / 'words.txt'
KEEP = 60000


def main():
    vocab = next(SNAP.glob('*/dict.TGT.json'))
    d = json.loads(vocab.read_text(encoding='utf-8'))
    words = [k[1:] for k, _ in sorted(d.items(), key=lambda kv: kv[1])
             if k.startswith('▁') and re.fullmatch(r'[ऀ-ॿ]{2,}', k[1:])]
    words = words[:KEEP]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    header = [
        '# Hindi words in frequency order, one per line; the line is the rank.',
        '# From the AI4Bharat IndicTrans2 target vocabulary (MIT licence),',
        '# written by tools/make_hindi_wordlist.py. Used only to repair text',
        '# extracted from NCERT PDFs; never shown on its own.',
    ]
    OUT.write_text('\n'.join(header + words) + '\n', encoding='utf-8', newline='\n')
    print('wrote %d words to %s (%.0f KB)' % (len(words), OUT, OUT.stat().st_size / 1024))


if __name__ == '__main__':
    main()

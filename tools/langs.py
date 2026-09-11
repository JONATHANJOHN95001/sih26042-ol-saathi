# -*- coding: utf-8 -*-
"""
The languages Ol Saathi can ship, and the script rules that keep them honest.

One table, imported by the pack builder, the manifest writer and the
verifiers, so that adding a language is a single edit in a single place
rather than a hunt through four files.

WHY SCRIPT IS THE UNIT THAT MATTERS
-----------------------------------
A language costs about 33 KB of JSON. A *script* costs a font, and fonts are
hundreds of kilobytes each in an APK that currently fits in four megabytes.
So the cheap languages are the ones whose script is already bundled, and the
table below records which font each language needs so nothing ships pointing
at a font that is not there.

WHY EACH SCRIPT NEEDS ITS OWN GUARD
-----------------------------------
The Santali build caught IndicTrans2 emitting Arabic letters mid-sentence and
dropped those entries rather than shipping them. That guard was a hardcoded
Ol Chiki range. Generalising the pipeline without generalising the guard would
silently ship exactly what the guard existed to catch, so every script here
carries its own allowed range, its own letter range, and its own digits.

THE DEVANAGARI CAVEAT, STATED OUT LOUD
--------------------------------------
For Santali the guard is strong: the source is Devanagari, the target is Ol
Chiki, so a model that echoes the Hindi instead of translating it produces
zero Ol Chiki letters and is caught instantly.

For Marathi, Nepali, Bodo and the rest of the Devanagari family that check is
much weaker, because source and target share a script. An echoed Hindi
sentence is perfectly valid Devanagari and sails through. That is what
`echoes_source` exists for: when the target script equals the source script,
an output identical to the Hindi input is treated as untranslated and dropped.
It is a weaker guarantee than Ol Chiki gets, and the packs say so.
"""

# Common to every script: ASCII punctuation the models legitimately emit, plus
# the zero-width joiners that Indic shaping depends on. A ZWJ is invisible and
# looks like contamination to a naive check, so it must be allowed explicitly.
ALLOWED_ASCII = set(' 0123456789.,?!:;-()\'"/')
# U+0964 DANDA and U+0965 DOUBLE DANDA are encoded once, in the Devanagari
# block, but Unicode treats them as shared Indic punctuation: Bengali, Odia,
# Gujarati and Punjabi all end sentences with the same character. Leaving them
# out made the Bengali build drop 40 of 53 entries as "foreign script
# (DEVANAGARI DANDA)" when the only thing wrong was a full stop.
ALLOWED_COMMON = {0x200C, 0x200D, 0x0964, 0x0965}

SCRIPTS = {
    'Olck': dict(
        label='Ol Chiki',
        blocks=[(0x1C50, 0x1C7F)],
        letters=[(0x1C5A, 0x1C77)],
        digits=(0x1C50, 0x1C59),
        font='NotoSansOlChiki-Regular.ttf',
    ),
    'Deva': dict(
        label='Devanagari',
        # 0x0900-0x097F is the main block; 0xA8E0-0xA8FF is Devanagari Extended.
        # Matras and the danda live inside the main block, so they are allowed
        # without being counted as letters.
        blocks=[(0x0900, 0x097F), (0xA8E0, 0xA8FF)],
        letters=[(0x0904, 0x0939), (0x0958, 0x0961), (0x0972, 0x097F)],
        digits=(0x0966, 0x096F),
        font='NotoSansDevanagari-Regular.ttf',
    ),
    'Beng': dict(
        label='Bengali',
        blocks=[(0x0980, 0x09FF)],
        letters=[(0x0985, 0x09B9), (0x09DC, 0x09E1)],
        digits=(0x09E6, 0x09EF),
        font='NotoSansBengali-Regular.ttf',
    ),
    'Orya': dict(
        label='Odia',
        blocks=[(0x0B00, 0x0B7F)],
        letters=[(0x0B05, 0x0B39), (0x0B5C, 0x0B61)],
        digits=(0x0B66, 0x0B6F),
        font='NotoSansOriya-Regular.ttf',
    ),
    'Gujr': dict(
        label='Gujarati',
        blocks=[(0x0A80, 0x0AFF)],
        letters=[(0x0A85, 0x0AB9), (0x0AE0, 0x0AE1)],
        digits=(0x0AE6, 0x0AEF),
        font='NotoSansGujarati-Regular.ttf',
    ),
    'Guru': dict(
        label='Gurmukhi',
        blocks=[(0x0A00, 0x0A7F)],
        letters=[(0x0A05, 0x0A39), (0x0A59, 0x0A5E)],
        digits=(0x0A66, 0x0A6F),
        font='NotoSansGurmukhi-Regular.ttf',
    ),
    'Taml': dict(
        label='Tamil',
        blocks=[(0x0B80, 0x0BFF)],
        letters=[(0x0B85, 0x0BB9)],
        digits=(0x0BE6, 0x0BEF),
        font='NotoSansTamil-Regular.ttf',
    ),
    'Telu': dict(
        label='Telugu',
        blocks=[(0x0C00, 0x0C7F)],
        letters=[(0x0C05, 0x0C39), (0x0C58, 0x0C61)],
        digits=(0x0C66, 0x0C6F),
        font='NotoSansTelugu-Regular.ttf',
    ),
    'Knda': dict(
        label='Kannada',
        blocks=[(0x0C80, 0x0CFF)],
        letters=[(0x0C85, 0x0CB9), (0x0CDE, 0x0CE1)],
        digits=(0x0CE6, 0x0CEF),
        font='NotoSansKannada-Regular.ttf',
    ),
    'Mlym': dict(
        label='Malayalam',
        blocks=[(0x0D00, 0x0D7F)],
        letters=[(0x0D05, 0x0D39), (0x0D5F, 0x0D61)],
        digits=(0x0D66, 0x0D6F),
        font='NotoSansMalayalam-Regular.ttf',
    ),
}

# code -> (IndicTrans2 tag, English name, endonym, script key)
#
# The endonym is what the dropdown shows: a teacher picking a language should
# see it written in that language rather than transliterated into English.
#
# An EMPTY endonym means nobody who reads that script has confirmed the
# spelling, and the dropdown falls back to the English name. Santali is empty
# on purpose. Writing a language's own name wrongly, in front of the people
# whose language it is, is the same failure as shipping an unchecked
# translation, and this project drops those rather than guessing. It is one
# more line for the Santali reviewer to fill in, alongside the 53 they are
# already checking.
LANGUAGES = {
    'sat': ('sat_Olck', 'Santali',   '', 'Olck'),
    'mar': ('mar_Deva', 'Marathi',   'मराठी',                   'Deva'),
    'npi': ('npi_Deva', 'Nepali',    'नेपाली',             'Deva'),
    # Sanskrit is deliberately not shipped. It is a classical language, not
    # a mother tongue any child in a Jharkhand primary school speaks, so it
    # serves nothing in a mother-tongue-education app. It was also the one
    # language the model could not terminate on, burning an hour to reach
    # entry 16 of 53. Left here, commented, so nobody re-adds it without
    # reading this.
    # 'san': ('san_Deva', 'Sanskrit',  'संस्कृतम्', 'Deva'),
    'mai': ('mai_Deva', 'Maithili',  'मैथिली',             'Deva'),
    'doi': ('doi_Deva', 'Dogri',     'डोगरी',                   'Deva'),
    'gom': ('gom_Deva', 'Konkani',   'कोंकणी',             'Deva'),
    'brx': ('brx_Deva', 'Bodo',      'बड़ो',                         'Deva'),
    'ben': ('ben_Beng', 'Bengali',   'বাংলা', 'Beng'),
    'asm': ('asm_Beng', 'Assamese',  'অসমীয়া', 'Beng'),
    'mni': ('mni_Beng', 'Manipuri',  'মৈতৈলোন্', 'Beng'),
    'ory': ('ory_Orya', 'Odia',      'ଓଡ଼ିଆ', 'Orya'),
    'guj': ('guj_Gujr', 'Gujarati',  'ગુજરાતી', 'Gujr'),
    'pan': ('pan_Guru', 'Punjabi',   'ਪੰਜਾਬੀ', 'Guru'),
    'tam': ('tam_Taml', 'Tamil',     'தமிழ்', 'Taml'),
    'tel': ('tel_Telu', 'Telugu',    'తెలుగు', 'Telu'),
    'kan': ('kan_Knda', 'Kannada',   'ಕನ್ನಡ', 'Knda'),
    'mal': ('mal_Mlym', 'Malayalam', 'മലയാളം', 'Mlym'),
}

# indic-nlp-library's code for each script, used to convert IndicTrans2's
# Devanagari output into the target script.
#
# WHY THIS IS NEEDED AT ALL
# -------------------------
# IndicTrans2 normalises Indic scripts to Devanagari internally, and its
# official inference pipeline transliterates back on the way out. Skipping that
# step is invisible while every target is either Devanagari already or Ol Chiki,
# and then Bengali arrives as "হ্যালো বাচ্চারা" spelled in Devanagari: the right
# language in a script its readers cannot read. The build caught all 53 of them
# as foreign-script contamination, which is exactly what that guard is for.
#
# None means the model already emits this script natively, so nothing is
# converted. Santali is the one such case.
TRANSLIT = {
    'Olck': None,
    'Deva': None,
    'Beng': 'bn',
    'Orya': 'or',
    'Gujr': 'gu',
    'Guru': 'pa',
    'Taml': 'ta',
    'Telu': 'te',
    'Knda': 'kn',
    'Mlym': 'ml',
}

# The source the teacher reads. Hindi, in Devanagari, always.
SOURCE_SCRIPT = 'Deva'


def spec(code):
    """Everything about one language, as a dict. Raises on an unknown code."""
    if code not in LANGUAGES:
        raise KeyError('unknown language %r. Known: %s'
                       % (code, ', '.join(sorted(LANGUAGES))))
    tag, english, endonym, script_key = LANGUAGES[code]
    sc = SCRIPTS[script_key]
    return dict(code=code, tag=tag, english=english, endonym=endonym,
                script=script_key, script_label=sc['label'], font=sc['font'],
                blocks=sc['blocks'], letters=sc['letters'], digits=sc['digits'],
                translit=TRANSLIT.get(script_key),
                shares_script_with_source=(script_key == SOURCE_SCRIPT))


def _in_ranges(cp, ranges):
    return any(lo <= cp <= hi for lo, hi in ranges)


def foreign(text, sp):
    """Characters with no business in this language's output."""
    out = []
    for ch in text:
        cp = ord(ch)
        if _in_ranges(cp, sp['blocks']) or ch in ALLOWED_ASCII or cp in ALLOWED_COMMON:
            continue
        out.append(ch)
    return out


def has_script_letter(text, sp):
    """True when the output contains at least one real letter of the script."""
    return any(_in_ranges(ord(c), sp['letters']) for c in text)


def digits_inside_word(text, sp):
    """
    Native-script digits glued into a word, which is always a decoding defect.

    Checked with whitespace stripped, exactly as the Santali build did, so a
    standalone numeral is fine and a numeral wedged mid-word is not.
    """
    lo, hi = sp['digits']
    stripped = ''.join(text.split())
    return any(lo <= ord(c) <= hi for c in stripped)


def echoes_source(target, source):
    """
    True when the model handed back the Hindi it was given.

    Only meaningful when target and source share a script; for Ol Chiki the
    letter check already catches it. Compared on stripped text so a difference
    of trailing punctuation or spacing does not mask an echo.
    """
    return ''.join(target.split()) == ''.join(source.split())


def to_target_script(text, sp):
    """
    Convert IndicTrans2's Devanagari output into the target script.

    Deliberately conditional rather than unconditional. A language whose script
    the model already emits natively must be left alone, and running a
    Devanagari-to-X transliteration over text that is already in X would
    corrupt it. The check is "does this already contain letters of the target
    script", which is the same test the contamination guard uses.
    """
    if sp['translit'] is None:
        return text
    if has_script_letter(text, sp):
        return text
    from indicnlp.transliterate.unicode_transliterate import UnicodeIndicTransliterator
    return UnicodeIndicTransliterator.transliterate(text, 'hi', sp['translit'])

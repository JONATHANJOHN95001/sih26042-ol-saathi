# -*- coding: utf-8 -*-
"""
Encode build-time speech to OGG and point a pack at it.

Two sources feed this. bhashini/pack_audio.mjs leaves float WAVs in
bhashini/out/audio/<lang>/, and tools/build_pack_audio_parler.py leaves 16-bit
WAVs in build/audio-parler/ for Santali, which Bhashini has no voice for. Both
folders are gitignored. This validates each clip, encodes it to OGG Vorbis in
the APK's assets under pack/audio/<lang>/, writes the entry's audio fields, and
brings packs.json's audio count into line. Run it with the TTS venv, which has
soundfile:

    .venv-tts/Scripts/python.exe tools/apply_audio_ogg.py --lang tam                     report only
    .venv-tts/Scripts/python.exe tools/apply_audio_ogg.py --lang tam --write             apply
    .venv-tts/Scripts/python.exe tools/apply_audio_ogg.py --lang sat --provenance parler \\
        --src build/audio-parler --write

WHY OGG, AND WHY A FOLDER PER LANGUAGE
--------------------------------------
Bhashini returns 32-bit float WAV. For the four languages that is about 30 MB
as delivered and 15 MB as 16-bit WAV, against 2.7 MB as Vorbis, measured on
real clips on 10 Sep 2026. Vorbis plays through MediaPlayer on every Android
version the app supports. The per-language folder exists because entry ids are
shared across packs: Tamil's p01 and Santali's p01 would otherwise overwrite
each other.

Both sources are normalised to a peak near 1.0 and a lossy codec overshoots a
little on decode, so the signal is scaled to 0.95 before encoding.

WHAT THE LABEL SAYS
-------------------
audioProvenance is set to the source, which the app shows as "Synthesised
speech · Bhashini" or "Synthesised speech · Indic Parler-TTS, unchecked". Both
are a machine voice reading a machine translation. No review or verified field
is touched.
"""

import argparse
import io
import json
import os

import soundfile as sf

from pack_manifest import refresh_audio_counts

PACK_DIR = os.path.join('app', 'src', 'main', 'assets', 'pack')
LANGS = ('tam', 'mal', 'tel', 'ben', 'brx', 'sat')
SOURCES = {
    'bhashini': (os.path.join('bhashini', 'out', 'audio', '{lang}'),
                 'Synthesised by Bhashini (AI4Bharat Indic-TTS)'),
    'parler': (os.path.join('build', 'audio-parler'),
               'Synthesised by AI4Bharat Indic Parler-TTS, run locally; not checked by a speaker'),
}
GAIN = 0.95
MIN_SECONDS = 0.25


def noise_not_speech(data, rate):
    """
    A reason to reject a clip whose shape is not a voice, or None.

    The second line of defence behind build_pack_audio_parler.py's own check,
    with the same thresholds: loudest 25 ms frame above -35 dBFS and at least
    60 per cent of the energy between 100 Hz and 4 kHz. Real speech from both
    synthesisers measured -12 to -19 dBFS and 85 to 95 per cent; a failed
    Parler take measured -52.5 dBFS and 30 per cent.
    """
    import numpy as np

    frame = int(rate * 0.025)
    n = len(data) // frame
    if n < 4:
        return 'too short to judge'
    frames = data[:n * frame].reshape(n, frame)
    loudest = float(20 * np.log10(np.sqrt((frames ** 2).mean(axis=1)).max() + 1e-12))
    if loudest < -35:
        return 'too quiet to be speech, loudest frame %.1f dBFS' % loudest
    spectrum = np.abs(np.fft.rfft(data * np.hanning(len(data))))
    freqs = np.fft.rfftfreq(len(data), 1.0 / rate)
    band = spectrum[(freqs > 100) & (freqs < 4000)].sum() / (spectrum.sum() + 1e-12)
    if band < 0.6:
        return 'only %.0f%% of energy in the speech band, noise rather than a voice' % (band * 100)
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--lang', required=True, choices=LANGS)
    ap.add_argument('--provenance', default='bhashini', choices=sorted(SOURCES),
                    help='which synthesiser made the clips (default: bhashini)')
    ap.add_argument('--src', default='',
                    help='folder of WAVs named <entry id>.wav (default depends on --provenance)')
    ap.add_argument('--write', action='store_true',
                    help='actually encode the clips and update the pack')
    ap.add_argument('--accept-by-ear', nargs='+', default=[], metavar='ID',
                    help='entry ids a person listened to and judged to be speech; skips only '
                         'the noise check for them, never the length check, and records it '
                         'in the entry as audioGate')
    args = ap.parse_args()

    default_src, label = SOURCES[args.provenance]
    # Santali's Bhashini voice is IIT Madras's, not AI4Bharat's, and it reads a
    # Devanagari transliteration (tools/build_pack_audio_iitm.py). The label
    # must name the voice that actually spoke.
    if args.provenance == 'bhashini' and args.lang == 'sat':
        label = ('Synthesised by Bhashini (IIT Madras TTS) from a Devanagari '
                 'transliteration; not checked by a speaker')
    src = args.src or default_src.format(lang=args.lang)
    pack_path = os.path.join(PACK_DIR, 'pack.%s.json' % args.lang)
    dst = os.path.join(PACK_DIR, 'audio', args.lang)
    pack = json.load(io.open(pack_path, encoding='utf-8'))
    entries = pack['entries']
    if not os.path.isdir(src):
        raise SystemExit('no clips at %s' % src)

    good, bad = {}, []
    for name in sorted(os.listdir(src)):
        if not name.endswith('.wav'):
            continue
        eid = name[:-4]
        if eid not in entries:
            bad.append((eid, 'not in the pack'))
            continue
        try:
            data, rate = sf.read(os.path.join(src, name), dtype='float32')
        except Exception as exc:
            bad.append((eid, 'unreadable: %s' % exc))
            continue
        if data.ndim > 1:
            data = data.mean(axis=1)
        secs = len(data) / float(rate)
        if secs < MIN_SECONDS:
            bad.append((eid, 'only %.2fs, not a spoken phrase' % secs))
            continue
        why = noise_not_speech(data, rate)
        if why and eid in args.accept_by_ear:
            # A person heard it. Say so on every run rather than silently.
            print('  ACCEPTED BY EAR %-11s %s' % (eid, why))
        elif why:
            bad.append((eid, why))
            continue
        good[eid] = (data, rate, secs)

    print('Language : %s, %d pack entries, source %s (%s)' % (args.lang, len(entries), src, args.provenance))
    print('Usable   : %d clips, %.0f s of speech' % (len(good), sum(g[2] for g in good.values())))
    for eid, why in bad:
        print('  REJECTED %-18s %s' % (eid, why))
    missing = sorted(e for e in entries if e not in good)
    print('Coverage : %d of %d' % (len(good), len(entries)))
    if missing:
        print('Silent   : %s%s' % (', '.join(missing[:6]), ' ...' if len(missing) > 6 else ''))
    if not args.write:
        print('Nothing written. Re-run with --write to apply.')
        return

    os.makedirs(dst, exist_ok=True)
    total = 0
    for eid, (data, rate, _) in sorted(good.items()):
        out = os.path.join(dst, eid + '.ogg')
        sf.write(out, data * GAIN, rate, format='OGG', subtype='VORBIS')
        total += os.path.getsize(out)
        entries[eid]['audio'] = 'pack/audio/%s/%s.ogg' % (args.lang, eid)
        entries[eid]['audioProvenance'] = args.provenance
        if eid in args.accept_by_ear:
            entries[eid]['audioGate'] = 'noise check overridden: judged to be speech by ear'
        else:
            entries[eid].pop('audioGate', None)
    pack.setdefault('provenance', {})['ttsService'] = label
    with io.open(pack_path, 'w', encoding='utf-8', newline='\n') as fh:
        json.dump(pack, fh, ensure_ascii=False, indent=2, sort_keys=True)
        fh.write('\n')
    print('Wrote %d OGG clips to %s (%.2f MB) and updated %s.'
          % (len(good), dst, total / 1048576.0, pack_path))
    for code, old, new in refresh_audio_counts(PACK_DIR):
        print('packs.json: %s audioEntries %d -> %d' % (code, old, new))


if __name__ == '__main__':
    main()

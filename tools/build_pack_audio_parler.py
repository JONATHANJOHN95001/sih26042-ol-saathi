# -*- coding: utf-8 -*-
"""
Synthesise Santali audio for every pack entry with Indic Parler-TTS.

For most of this project's life the answer to "where does the Santali audio
come from" was "nowhere". Meta's MMS covers 1,143 languages and Santali is not
one of them, eSpeak has no Santali voice, and Android ships none. That left
Bhashini, which is gated behind an approval queue that has not moved.

The earlier search was wrong, and it is worth naming how. It looked for
"Santali TTS" and stopped when nothing came back. What it should have done is
read the language lists of the big Indic multi-speaker models, because
ai4bharat/indic-parler-tts carries Santali in its official twenty-one and is
Apache 2.0. So the audio can be generated after all.

    python tools/build_pack_audio_parler.py --limit 3      # try three first
    python tools/build_pack_audio_parler.py                # all of them

Output lands in build/audio-parler/<entry id>.wav, ready for apply_audio.py:

    python tools/apply_audio.py build/audio-parler --provenance parler --write

WHAT THIS AUDIO IS AND IS NOT
-----------------------------
It is machine synthesis of machine translation, so nobody who speaks the
language has heard it. Santali is one of twenty-one languages in a 0.9B model
and is certainly not the best served of them. Treat every clip as a draft until
a Santali speaker listens to it, and keep the on-screen label saying exactly
that. The project's rule is that the app never invents output, and a confident
voice reading an unchecked line is the most convincing way to break it.

This runs on CPU by design. Generation happens at build time and ships inside
the APK, so slowness costs a coffee, not a user.
"""
from __future__ import annotations

import argparse
import io
import json
import os
import sys
import time

PACK = 'app/src/main/assets/pack/pack.sat.json'
OUT_DIR = os.path.join('build', 'audio-parler')
MODEL = 'ai4bharat/indic-parler-tts'

# The description conditions the voice. Parler models take a plain-English
# sentence rather than a speaker id, and the model card asks for a named
# speaker plus recording conditions. This one aims at a primary classroom:
# unhurried, clearly articulated, no room echo, because the tablet speaker is
# small and the room has thirty children in it.
DESCRIPTION = (
    'A female speaker delivers the sentence slowly and very clearly, with a '
    'warm and encouraging tone, as if teaching a young child. The recording is '
    'clean and close, with no background noise and no reverberation.'
)


ATTEMPTS = 3


def speech_problem(audio, rate):
    """
    Why a generated clip is not usable speech, or None when it is.

    Measured on this model's own output, 10 Sep 2026: four clips that are
    speech had their loudest 25 ms frame at -12 to -19 dBFS and 85 to 95 per
    cent of their spectral energy between 100 Hz and 4 kHz. The failed take of
    p02 was -52.5 dBFS and 30 per cent, steady hiss. The thresholds sit well
    between the two. They judge the shape of a signal, not the language, so
    passing says "this is a voice", never "this is correct Santali".
    """
    import numpy as np

    seconds = len(audio) / float(rate)
    # tools/verify_assets.py rejects anything at or below 0.3s, so the floor
    # here sits above it.
    if seconds < 0.35:
        return 'only %.2fs of audio' % seconds
    frame = int(rate * 0.025)
    n = len(audio) // frame
    frames = audio[:n * frame].reshape(n, frame)
    loudest = float(20 * np.log10(np.sqrt((frames ** 2).mean(axis=1)).max() + 1e-12))
    if loudest < -35:
        return 'too quiet to be speech, loudest frame %.1f dBFS' % loudest
    spectrum = np.abs(np.fft.rfft(audio * np.hanning(len(audio))))
    freqs = np.fft.rfftfreq(len(audio), 1.0 / rate)
    band = spectrum[(freqs > 100) & (freqs < 4000)].sum() / (spectrum.sum() + 1e-12)
    if band < 0.6:
        return 'only %.0f%% of energy in the speech band, noise rather than a voice' % (band * 100)
    return None


def load_entries(pack_path):
    with io.open(pack_path, encoding='utf-8') as fh:
        return json.load(fh)['entries']


def main():
    ap = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--out', default=OUT_DIR, help='where the WAVs go')
    ap.add_argument('--limit', type=int, default=0,
                    help='only synthesise the first N entries, for a quick listen')
    ap.add_argument('--only', default='',
                    help='comma separated entry ids, overrides --limit')
    ap.add_argument('--description', default=DESCRIPTION,
                    help='voice and recording description passed to the model')
    ap.add_argument('--dtype', default='float32', choices=['float32', 'bfloat16'],
                    help='bfloat16 roughly halves memory, at some quality risk')
    ap.add_argument('--redo', action='store_true',
                    help='regenerate clips that already exist instead of skipping')
    # ai4bharat/indic-parler-tts became a gated repo and now answers 401 without
    # a Hugging Face token and an accepted licence click. The weights are still
    # Apache 2.0, and ungated mirrors of the same checkpoint exist, so this flag
    # lets a build proceed without an account. The canonical repo stays the
    # default: a mirror is somebody else's upload and should be a deliberate
    # choice, recorded in the pack's provenance when it is used.
    ap.add_argument('--model', default=MODEL,
                    help='model id to synthesise with (default: %s)' % MODEL)
    args = ap.parse_args()

    # Imported here rather than at module scope so --help works without the
    # 2 GB of dependencies installed.
    import torch
    import soundfile as sf
    from parler_tts import ParlerTTSForConditionalGeneration
    from transformers import AutoTokenizer

    entries = load_entries(PACK)

    if args.only:
        wanted = [e.strip() for e in args.only.split(',') if e.strip()]
        missing = [e for e in wanted if e not in entries]
        if missing:
            raise SystemExit('not in the pack: %s' % ', '.join(missing))
        ids = wanted
    else:
        ids = sorted(entries)
        if args.limit:
            ids = ids[:args.limit]

    if not os.path.isdir(args.out):
        os.makedirs(args.out)

    dtype = torch.float32 if args.dtype == 'float32' else torch.bfloat16
    # A CUDA build of torch on the RTX 3050 turns roughly fifteen minutes a
    # clip on CPU into seconds. bf16 needs about 2 GB of the card's 4 GB. The
    # CPU path stays for machines without a GPU build of torch.
    device = 'cuda' if torch.cuda.is_available() else 'cpu'
    print('Model     : %s' % args.model)
    print('Precision : %s on %s' % (args.dtype, torch.cuda.get_device_name(0) if device == 'cuda' else 'CPU'))
    print('Entries   : %d' % len(ids))
    print('Output    : %s' % args.out)
    print()
    print('Loading the model. First run downloads roughly 2 GB.')

    t0 = time.time()
    model = ParlerTTSForConditionalGeneration.from_pretrained(
        args.model, torch_dtype=dtype).to(device)
    model.eval()
    tokenizer = AutoTokenizer.from_pretrained(args.model)
    desc_tokenizer = AutoTokenizer.from_pretrained(
        model.config.text_encoder._name_or_path)
    rate = model.config.sampling_rate
    print('Loaded in %.0fs, sample rate %d Hz.' % (time.time() - t0, rate))
    print()

    desc_ids = desc_tokenizer(args.description, return_tensors='pt').to(device)

    done, skipped, failed = 0, 0, []
    started = time.time()

    for n, eid in enumerate(ids, 1):
        path = os.path.join(args.out, eid + '.wav')
        if os.path.exists(path) and not args.redo:
            skipped += 1
            continue

        text = entries[eid].get('target', '').strip()
        if not text:
            failed.append((eid, 'no target text'))
            continue

        t = time.time()
        try:
            prompt_ids = tokenizer(text, return_tensors='pt').to(device)
            # Parler samples, so a failed take can succeed on the next one.
            # On 10 Sep 2026 p02 came back as 3.7 s of steady hiss at -52 dBFS
            # with most of its energy above 4 kHz, which the old peak floor
            # let through. speech_problem() catches that shape, and each entry
            # gets up to ATTEMPTS takes before it is reported as failed.
            audio, why = None, 'not generated'
            for take in range(1, ATTEMPTS + 1):
                with torch.no_grad():
                    generation = model.generate(
                        input_ids=desc_ids.input_ids,
                        attention_mask=desc_ids.attention_mask,
                        prompt_input_ids=prompt_ids.input_ids,
                        prompt_attention_mask=prompt_ids.attention_mask,
                    )
                audio = generation.to(torch.float32).cpu().numpy().squeeze()
                why = speech_problem(audio, rate)
                if why is None:
                    break
                print('  [%2d/%2d] %-18s take %d rejected: %s' % (n, len(ids), eid, take, why))
            if why is not None:
                failed.append((eid, why))
                continue

            seconds = len(audio) / float(rate)
            peak = float(abs(audio).max())
            sf.write(path, audio, rate, subtype='PCM_16')
            done += 1
            print('  [%2d/%2d] %-18s %5.1fs audio  %5.1fs to make  peak %.2f'
                  % (n, len(ids), eid, seconds, time.time() - t, peak))
        except (RuntimeError, ValueError, OSError) as exc:
            failed.append((eid, str(exc)[:120]))
            print('  [%2d/%2d] %-18s FAILED: %s'
                  % (n, len(ids), eid, str(exc)[:80]))

    elapsed = time.time() - started
    print()
    print('Wrote %d clips in %.0fs (%.1fs each).'
          % (done, elapsed, elapsed / done if done else 0))
    if skipped:
        print('Skipped %d that already existed. Use --redo to replace them.'
              % skipped)
    if failed:
        print()
        print('FAILED (%d):' % len(failed))
        for eid, why in failed:
            print('   %-18s %s' % (eid, why))

    print()
    print('Listen to a few before going further. Then:')
    print('  python tools/apply_audio.py %s --provenance parler --write' % args.out)
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())

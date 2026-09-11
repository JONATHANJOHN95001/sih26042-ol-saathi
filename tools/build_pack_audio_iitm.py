# -*- coding: utf-8 -*-
"""
Synthesise Santali pack audio with Bhashini's IIT Madras voice.

    .venv-tts/Scripts/python.exe tools/build_pack_audio_iitm.py            all entries
    .venv-tts/Scripts/python.exe tools/build_pack_audio_iitm.py p01 p02    just these
    .venv-tts/Scripts/python.exe tools/apply_audio_ogg.py --lang sat --write

WHY THIS VOICE, AND WHY DEVANAGARI
---------------------------------
Bhashini/IITM/TTS is the only Santali speech on Bhashini (pipeline
660fa5bec7fb5b0328229016). Given Ol Chiki it answers 200 OK with 0.02 s of
silence; given the same words in Devanagari it speaks. So each entry's
`bridge` field, written by tools/olchiki_bridge.py, is what gets read aloud.
The pack still displays Ol Chiki; the Devanagari is only the voice's input.

The request is the one the app's BhashiniClient sends and the one the official
bhashini-client-sdk sends: compute endpoint directly, inference key in
Authorization, gender female, wav at 16000. Output is written as 16-bit WAV to
bhashini/out/audio/sat/<entry id>.wav, which apply_audio_ogg.py reads by default
and which is gitignored.

A clip that comes back short or silent is retried, then skipped rather than
kept: apply_audio_ogg.py would reject it anyway, and an entry with no audio
says so on screen, where a silent one would look broken.
"""
import base64
import io
import json
import os
import sys
import time
import urllib.error
import urllib.request

import numpy as np
import soundfile as sf

from olchiki_bridge import transliterate

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'pack', 'pack.sat.json')
OUT = os.path.join(ROOT, 'bhashini', 'out', 'audio', 'sat')
ENDPOINT = 'https://dhruva-api.bhashini.gov.in/services/inference/pipeline'
SERVICE = 'Bhashini/IITM/TTS'
MIN_SECONDS, MIN_RMS = 0.3, 0.006   # rms on a -1..1 scale; the silent reply is 0.0
TRIES = 3


def inference_key():
    with io.open(os.path.join(ROOT, 'local.properties'), encoding='utf-8') as fh:
        for line in fh:
            k, _, v = line.partition('=')
            if k.strip() == 'BHASHINI_INFERENCE_KEY':
                return v.strip()
    raise SystemExit('BHASHINI_INFERENCE_KEY is not in local.properties')


def synthesise(key, text):
    body = json.dumps({
        'pipelineTasks': [{'taskType': 'tts', 'config': {
            'language': {'sourceLanguage': 'sat'}, 'serviceId': SERVICE,
            'gender': 'female', 'audioFormat': 'wav', 'samplingRate': 16000}}],
        'inputData': {'input': [{'source': text}]},
    }).encode('utf-8')
    req = urllib.request.Request(ENDPOINT, data=body, headers={
        'Content-Type': 'application/json', 'Authorization': key})
    with urllib.request.urlopen(req, timeout=30) as resp:
        reply = json.loads(resp.read().decode('utf-8'))
    b64 = reply['pipelineResponse'][0]['audio'][0]['audioContent']
    data, rate = sf.read(io.BytesIO(base64.b64decode(b64)), dtype='float32')
    return (data.mean(axis=1) if data.ndim > 1 else data), rate


def main():
    key = inference_key()
    entries = json.load(io.open(PACK, encoding='utf-8'))['entries']
    wanted = sys.argv[1:] or sorted(entries)
    os.makedirs(OUT, exist_ok=True)
    made, skipped = 0, []
    for eid in wanted:
        entry = entries.get(eid)
        if entry is None:
            skipped.append((eid, 'not in the pack'))
            continue
        text = entry.get('bridge') or transliterate(entry.get('target', ''))
        if not text.strip():
            skipped.append((eid, 'no text'))
            continue
        for attempt in range(1, TRIES + 1):
            t0 = time.time()
            try:
                data, rate = synthesise(key, text)
            except (urllib.error.URLError, KeyError, IndexError, ValueError, RuntimeError) as exc:
                why = '%s: %s' % (type(exc).__name__, exc)
                data = None
            if data is not None:
                secs = len(data) / float(rate)
                rms = float(np.sqrt((data ** 2).mean())) if len(data) else 0.0
                if secs >= MIN_SECONDS and rms >= MIN_RMS:
                    sf.write(os.path.join(OUT, eid + '.wav'), data, rate, subtype='PCM_16')
                    print('%-6s %5.2f s  rms %.3f  %4.0f ms  %s' % (eid, secs, rms, (time.time() - t0) * 1000, text))
                    made += 1
                    break
                why = 'silent or short: %.2f s, rms %.4f' % (secs, rms)
            if attempt == TRIES:
                skipped.append((eid, why))
            else:
                time.sleep(1.5 * attempt)
    print('\n%d of %d clips written to %s' % (made, len(wanted), OUT))
    for eid, why in skipped:
        print('  SKIPPED %-6s %s' % (eid, why))


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()

"""Hindi -> every pack language: translate, then speak the translation.

Audio is judged by duration and RMS, since a silent WAV still returns success.
Santali goes through olchiki_bridge (Ol Chiki -> Devanagari) before TTS.
"""
import audioop
import os
import sys
import time
import wave
from pathlib import Path

HERE = Path(__file__).parent
exec((HERE / "smoke_test.py").read_text(encoding="utf-8").split("from bhashini_client")[0])
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from olchiki_bridge import transliterate  # noqa: E402
from bhashini_client import BhashiniClient  # noqa: E402

LANGS = ["as", "bn", "brx", "doi", "gom", "gu", "kn", "mai", "ml", "mr",
         "mni", "ne", "or", "pa", "sat", "ta", "te"]
SOURCE = "बच्चे स्कूल जाते हैं।"

client = BhashiniClient(os.environ["BHASHINI_API_KEY"])
out = HERE / "out" / "langs"
out.mkdir(parents=True, exist_ok=True)

print(f"{'lang':5} {'nmt':>6} {'tts':>6} {'secs':>5} {'rms':>6}  translation")
for lang in LANGS:
    t0 = time.perf_counter()
    try:
        text = client.nmt(SOURCE, "hi", lang)
    except Exception as e:
        print(f"{lang:5} NMT FAIL {type(e).__name__}: {str(e)[:120]}")
        continue
    t1 = time.perf_counter()
    speak = transliterate(text) if lang == "sat" else text
    wav = out / f"{lang}.wav"
    try:
        client.tts(speak, lang, output_file=str(wav))
        t2 = time.perf_counter()
        w = wave.open(str(wav))
        frames = w.readframes(w.getnframes())
        secs = w.getnframes() / w.getframerate()
        rms = audioop.rms(frames, w.getsampwidth())
        tts = f"{(t2 - t1) * 1000:6.0f}"
    except Exception as e:
        tts, secs, rms = "  FAIL", 0.0, 0
        text += f"  [TTS {type(e).__name__}: {str(e)[:80]}]"
    print(f"{lang:5} {(t1 - t0) * 1000:6.0f} {tts} {secs:5.2f} {rms:6}  {text}")

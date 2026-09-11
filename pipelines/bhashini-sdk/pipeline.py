"""Hindi voice to target-language voice, built on bhashini-client-sdk.

    ASR (hi speech -> hi text) -> NMT (hi -> target) -> TTS (target speech)

Usage:
    python pipeline.py --wav speech.wav --to sat
    python pipeline.py --text "बच्चे स्कूल जाते हैं।" --to ta

Two things the SDK does not do for you:
  * It returns errors as strings ("Input Error: ...") instead of raising, so
    every step is checked with is_error_response; otherwise an error message
    would be translated and spoken as if it were content.
  * Santali's only voice (Bhashini/IITM/TTS) is silent for Ol Chiki, so the
    translation is bridged to Devanagari before TTS. The Ol Chiki is what you
    display; the Devanagari is only what the voice reads.
"""
import argparse
import os
import sys
import time
from pathlib import Path

HERE = Path(__file__).parent
exec((HERE / "smoke_test.py").read_text(encoding="utf-8").split("from bhashini_client")[0])
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from olchiki_bridge import transliterate  # noqa: E402
from bhashini_client import BhashiniClient, get_available_models  # noqa: E402
from bhashini_client.services.service_utils import is_error_response  # noqa: E402
from tts_compare_measure import measure  # noqa: E402  (float-aware WAV reader)

# Measured faster than the SDK's first choice (tts_compare.py, 10 Sep 2026).
DRAV = "ai4bharat/indic-tts-coqui-dravidian-gpu--t4"
ARYAN = "ai4bharat/indic-tts-coqui-indo_aryan-gpu--t4"
FAST_TTS = {"ta": DRAV, "ml": DRAV, "te": DRAV, "or": ARYAN, "gu": ARYAN, "mr": ARYAN}
# The SDK's first NMT match is IIIT Hyderabad's, which rendered "नमस्ते" as
# ᱦᱚᱞᱳ ("holo", i.e. "hello"). The app's packs and live path use IndicTrans2,
# so the pipeline does too, and its output matches what the app shows.
NMT_PREFERRED = "ai4bharat/indictrans-v2-all-gpu--t4"
# A TTS call can report success and write silence (Santali given Ol Chiki:
# 0.02 s, rms 0). Anything this short or this quiet is treated as a failure.
MIN_SECONDS, MIN_RMS = 0.3, 200


class PipelineError(RuntimeError):
    pass


def pick(service, language, preferred=None):
    """Preferred id if the SDK lists it for this language, else the SDK's first."""
    models = get_available_models(service, language=language)
    if is_error_response(models) or not models:
        raise PipelineError(f"no {service} model for {language}: {models}")
    ids = [m["serviceId"] for m in models]
    return preferred if preferred in ids else ids[0]


def check(step, value):
    if value is None or is_error_response(value):
        raise PipelineError(f"{step} failed: {value}")
    return value


def run(client, target, wav=None, text=None, out_dir=HERE / "out" / "pipeline"):
    out_dir.mkdir(parents=True, exist_ok=True)
    timings, result = {}, {"target": target}

    if wav:
        t = time.perf_counter()
        asr_id = pick("asr", "hi")
        text = check("ASR", client.asr(str(wav), "hi", service_id=asr_id))
        timings["asr"] = time.perf_counter() - t
        result["asr_model"] = asr_id
    result["hindi"] = text

    t = time.perf_counter()
    nmt_id = pick("nmt", ("hi", target), NMT_PREFERRED)
    translated = check("NMT", client.nmt(text, "hi", target, service_id=nmt_id))
    timings["nmt"] = time.perf_counter() - t
    result.update(nmt_model=nmt_id, translation=translated)

    spoken = transliterate(translated) if target == "sat" else translated
    result["spoken_text"] = spoken

    t = time.perf_counter()
    tts_id = pick("tts", target, FAST_TTS.get(target))
    # Unique per run: two runs to the same language must not overwrite each other.
    path = out_dir / f"{target}-{time.strftime('%H%M%S')}-{time.perf_counter_ns() % 10000:04d}.wav"
    check("TTS", client.tts(spoken, target, output_file=str(path), service_id=tts_id))
    timings["tts"] = time.perf_counter() - t
    secs, rms = measure(path)
    if secs < MIN_SECONDS or rms < MIN_RMS:
        raise PipelineError(f"TTS returned silence ({secs:.2f} s, rms {rms}) for: {spoken}")
    result.update(tts_model=tts_id, audio=str(path), audio_check=f"{secs:.2f} s, rms {rms}")

    timings["total"] = sum(timings.values())
    result["timings_ms"] = {k: round(v * 1000) for k, v in timings.items()}
    return result


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--wav", help="Hindi speech, WAV")
    src.add_argument("--text", help="Hindi text (skips ASR)")
    ap.add_argument("--to", required=True, help="target language code, e.g. sat, ta, bn")
    a = ap.parse_args()

    client = BhashiniClient(os.environ["BHASHINI_API_KEY"])
    try:
        r = run(client, a.to, wav=a.wav, text=a.text)
    except PipelineError as e:
        sys.exit(f"PIPELINE FAILED: {e}")
    for k, v in r.items():
        print(f"{k:12} {v}")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()

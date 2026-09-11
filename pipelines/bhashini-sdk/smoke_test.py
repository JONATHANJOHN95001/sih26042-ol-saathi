"""Smoke test for bhashini-client-sdk: translation and speech against live Bhashini.

The key comes from the BHASHINI_API_KEY environment variable, or else from
BHASHINI_INFERENCE_KEY in the repository's local.properties (gitignored), so it
never has to be typed or printed.
"""
import os
import time
from pathlib import Path

PROPS = Path(__file__).resolve().parents[2] / "local.properties"


def load_key():
    if os.environ.get("BHASHINI_API_KEY"):
        return os.environ["BHASHINI_API_KEY"]
    if PROPS.exists():
        for line in PROPS.read_text(encoding="utf-8").splitlines():
            k, _, v = line.partition("=")
            if k.strip() == "BHASHINI_INFERENCE_KEY":
                return v.strip()
    raise SystemExit("Set BHASHINI_API_KEY, or BHASHINI_INFERENCE_KEY in local.properties")


os.environ.setdefault("BHASHINI_API_KEY", load_key())

from bhashini_client import BhashiniClient  # noqa: E402  (reads the env var on import)

client = BhashiniClient(os.environ["BHASHINI_API_KEY"])
out = Path(__file__).parent / "out"
out.mkdir(exist_ok=True)


def run(label, fn):
    t = time.perf_counter()
    try:
        result = fn()
        print(f"PASS {label} ({(time.perf_counter() - t) * 1000:.0f} ms): {str(result)[:160]}")
    except Exception as e:
        print(f"FAIL {label}: {type(e).__name__}: {str(e)[:300]}")


run("nmt en->hi", lambda: client.nmt("Hello, how are you?", "en", "hi"))
run("nmt hi->sat", lambda: client.nmt("आप कैसे हैं?", "hi", "sat"))
run("tts hi", lambda: client.tts("नमस्ते, आप कैसे हैं?", "hi", output_file=str(out / "hi.wav")))
run("tts ta", lambda: client.tts("வணக்கம்", "ta", output_file=str(out / "ta.wav")))

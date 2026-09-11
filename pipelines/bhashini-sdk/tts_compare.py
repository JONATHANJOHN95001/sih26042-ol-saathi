"""Compare TTS latency: SDK default (IITM) vs AI4Bharat service ids, 3 runs each.

Text is translated once per language, then each service speaks the same string.
"""
import audioop
import math
import os
import statistics
import struct
import time
from pathlib import Path

HERE = Path(__file__).parent
exec((HERE / "smoke_test.py").read_text(encoding="utf-8").split("from bhashini_client")[0])
from bhashini_client import BhashiniClient  # noqa: E402

IITM = "Bhashini/IITM/TTS"
DRAV = "ai4bharat/indic-tts-coqui-dravidian-gpu--t4"
ARYAN = "ai4bharat/indic-tts-coqui-indo_aryan-gpu--t4"
FAST = {"ta": DRAV, "ml": DRAV, "kn": DRAV, "te": DRAV,
        "bn": ARYAN, "as": ARYAN, "gu": ARYAN, "mr": ARYAN, "or": ARYAN, "pa": ARYAN}
RUNS = 3
SOURCE = "बच्चे स्कूल जाते हैं।"

client = BhashiniClient(os.environ["BHASHINI_API_KEY"])
out = HERE / "out" / "compare"
out.mkdir(parents=True, exist_ok=True)


def time_tts(text, lang, service_id, wav):
    times = []
    for _ in range(RUNS):
        t = time.perf_counter()
        client.tts(text, lang, service_id=service_id, output_file=str(wav))
        times.append((time.perf_counter() - t) * 1000)
    secs, rms = measure(wav)
    return statistics.median(times), secs, rms


def measure(wav):
    """Duration and 16-bit-scale RMS; handles AI4Bharat's IEEE float WAV (format 3)."""
    data = Path(wav).read_bytes()
    pos, fmt, rate, bits, pcm = 12, None, None, None, b""
    while pos + 8 <= len(data):
        cid, size = data[pos:pos + 4], struct.unpack("<I", data[pos + 4:pos + 8])[0]
        body = data[pos + 8:pos + 8 + size]
        if cid == b"fmt ":
            fmt, _, rate = struct.unpack("<HHI", body[:8])
            bits = struct.unpack("<H", body[14:16])[0]
        elif cid == b"data":
            pcm = body
        pos += 8 + size + (size & 1)
    if fmt == 3:
        samples = struct.unpack(f"<{len(pcm) // 4}f", pcm)
        rms = int(math.sqrt(sum(s * s for s in samples) / max(len(samples), 1)) * 32767)
        return len(samples) / rate, rms
    width = bits // 8
    return len(pcm) / width / rate, audioop.rms(pcm, width)


print(f"{'lang':4} {'IITM ms':>8} {'AI4B ms':>8}  {'AI4B secs':>9} {'rms':>6}")
for lang, fast_id in FAST.items():
    text = client.nmt(SOURCE, "hi", lang).replace('"', "")
    try:
        slow_ms, _, _ = time_tts(text, lang, IITM, out / f"{lang}.iitm.wav")
    except Exception as e:
        slow_ms = float("nan")
        print(f"{lang:4} IITM FAIL {type(e).__name__}: {str(e)[:100]}")
    try:
        fast_ms, secs, rms = time_tts(text, lang, fast_id, out / f"{lang}.ai4b.wav")
        print(f"{lang:4} {slow_ms:8.0f} {fast_ms:8.0f}  {secs:9.2f} {rms:6}")
    except Exception as e:
        print(f"{lang:4} {slow_ms:8.0f} AI4B FAIL {type(e).__name__}: {str(e)[:100]}")

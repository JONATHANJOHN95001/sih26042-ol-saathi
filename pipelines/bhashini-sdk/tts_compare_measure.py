"""Duration and RMS of a WAV, including AI4Bharat's IEEE float WAV (format 3).

Python's wave module refuses format 3 ("unknown format: 3"), and Bhashini's
AI4Bharat voices return exactly that. RMS is on a 16-bit scale either way.
"""
import audioop
import math
import struct
from pathlib import Path


def measure(wav):
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
    if not rate or not pcm:
        return 0.0, 0
    if fmt == 3:
        samples = struct.unpack(f"<{len(pcm) // 4}f", pcm[: len(pcm) // 4 * 4])
        rms = int(math.sqrt(sum(s * s for s in samples) / max(len(samples), 1)) * 32767)
        return len(samples) / rate, rms
    width = bits // 8
    return len(pcm) / width / rate, audioop.rms(pcm, width)

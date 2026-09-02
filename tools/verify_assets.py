#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Check that bundled assets are real files rather than placeholders.

Three ONNX models and one font once shipped as zero-filled stubs with plausible
magic bytes. The app loaded them, the load failed, and a catch-all fallback
swallowed the error, so everything appeared to work while no neural model was
ever running. Nothing in the build or the test suite noticed.

Fonts are checked by actual glyph coverage rather than file size, because size
is a bad proxy: the real Noto Sans Ol Chiki is only 15 KB, since the script has
just 48 code points.

Audio files (when present) are checked for:
  - Genuine RIFF/WAVE header
  - Duration longer than 0.3 s
  - Not pure silence
  - Every entry.audio path in the pack resolves to a real file
  - Every wav file on disk has a pack entry pointing at it

    python tools/verify_assets.py
    python tools/verify_assets.py --selftest   # prove the WAV checks work

The audio checks have nothing to run against until a pack ships wav files, so
--selftest builds them in memory instead. Use it whenever you change them.
"""
from __future__ import annotations

import json
import pathlib
import struct
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'app' / 'src' / 'main' / 'assets'

WAVE_FORMAT_PCM = 1
WAVE_FORMAT_EXTENSIBLE = 0xFFFE

# name -> (first code point, last code point, human label)
FONTS = {
    'fonts/NotoSansOlChiki-Regular.ttf':    (0x1C5A, 0x1C77,  'Ol Chiki'),
    'fonts/NotoSansWarangCiti-Regular.ttf': (0x118A0, 0x118DF, 'Warang Citi'),
    'fonts/NotoSansDevanagari-Regular.ttf': (0x0905, 0x0939,  'Devanagari'),
    'fonts/NotoSansNagMundari-Regular.ttf': (0x1E4D0, 0x1E4EB, 'Nag Mundari'),
}


def font_coverage(data: bytes, lo: int, hi: int) -> tuple[int, int]:
    """Return (covered, wanted) code points, read from the font's cmap."""
    num_tables = struct.unpack('>H', data[4:6])[0]
    cmap_off = None
    for i in range(num_tables):
        rec = 12 + i * 16
        if data[rec:rec + 4] == b'cmap':
            cmap_off = struct.unpack('>I', data[rec + 8:rec + 12])[0]
    if cmap_off is None:
        return 0, hi - lo + 1

    covered: set[int] = set()
    n_sub = struct.unpack('>H', data[cmap_off + 2:cmap_off + 4])[0]
    for i in range(n_sub):
        rec = cmap_off + 4 + i * 8
        sub = cmap_off + struct.unpack('>I', data[rec + 4:rec + 8])[0]
        fmt = struct.unpack('>H', data[sub:sub + 2])[0]
        if fmt == 4:
            seg2 = struct.unpack('>H', data[sub + 6:sub + 8])[0]
            seg = seg2 // 2
            ends = [struct.unpack('>H', data[sub + 14 + j * 2:sub + 16 + j * 2])[0] for j in range(seg)]
            sp = sub + 16 + seg2
            starts = [struct.unpack('>H', data[sp + j * 2:sp + 2 + j * 2])[0] for j in range(seg)]
            for s, e in zip(starts, ends):
                if e != 0xFFFF:
                    covered.update(range(s, e + 1))
        elif fmt == 12:
            n_groups = struct.unpack('>I', data[sub + 12:sub + 16])[0]
            for j in range(n_groups):
                g = sub + 16 + j * 12
                s, e = struct.unpack('>II', data[g:g + 8])
                covered.update(range(s, min(e, s + 4000) + 1))

    wanted = set(range(lo, hi + 1))
    return len(wanted & covered), len(wanted)


# ── WAV helpers ──────────────────────────────────────────────────────────

def _parse_wav_header(data: bytes) -> tuple[int, int, int] | None:
    """
    Parse a RIFF/WAVE header.  Returns (sample_rate, bits_per_sample,
    num_channels) on success, or None if the file is not a valid WAV.
    """
    if len(data) < 44:
        return None
    if data[0:4] != b'RIFF' or data[8:12] != b'WAVE':
        return None

    # Walk chunks looking for 'fmt '
    offset = 12
    while offset + 8 <= len(data):
        chunk_id = data[offset:offset + 4]
        chunk_size = struct.unpack('<I', data[offset + 4:offset + 8])[0]
        if chunk_id == b'fmt ':
            if chunk_size < 16:
                return None
            audio_fmt = struct.unpack('<H', data[offset + 8:offset + 10])[0]
            num_channels = struct.unpack('<H', data[offset + 10:offset + 12])[0]
            sample_rate = struct.unpack('<I', data[offset + 12:offset + 16])[0]
            bits_per_sample = struct.unpack('<H', data[offset + 22:offset + 24])[0]

            # WAVE_FORMAT_EXTENSIBLE. Plain PCM wearing a longer header, which
            # is what a lot of encoders emit at 48 kHz, and Bhashini returns
            # 48 kHz. Rejecting it outright failed perfectly good audio for a
            # cosmetic reason. The real format tag is the first two bytes of
            # the SubFormat GUID, 24 bytes into the chunk body.
            if audio_fmt == WAVE_FORMAT_EXTENSIBLE:
                if chunk_size < 40:
                    return None
                audio_fmt = struct.unpack('<H', data[offset + 32:offset + 34])[0]

            if audio_fmt != WAVE_FORMAT_PCM:
                return None
            return sample_rate, bits_per_sample, num_channels
        # advance to next chunk (word-aligned)
        offset += 8 + chunk_size + (chunk_size & 1)
    return None


def _wav_duration_and_energy(data: bytes) -> tuple[float, float] | None:
    """
    Return (duration_seconds, peak_amplitude) for a PCM WAV, or None on
    parse failure.  Peak amplitude is the max absolute sample value
    normalised to [0, 1].
    """
    info = _parse_wav_header(data)
    if info is None:
        return None
    sample_rate, bits_per_sample, num_channels = info
    if sample_rate == 0:
        return None

    # Find the 'data' chunk
    offset = 12
    while offset + 8 <= len(data):
        chunk_id = data[offset:offset + 4]
        chunk_size = struct.unpack('<I', data[offset + 4:offset + 8])[0]
        if chunk_id == b'data':
            bytes_per_sample = bits_per_sample // 8
            # Anything outside this range is not something this function can
            # measure. Say so, rather than falling through to a peak of zero:
            # a 24-bit file used to be read as sample == 0 for every frame and
            # then failed as "pure silence", which is a confident wrong answer
            # where "cannot tell" was the truth.
            if bytes_per_sample not in (1, 2, 3, 4):
                return None
            total_samples = chunk_size // bytes_per_sample
            duration = total_samples / (sample_rate * num_channels)

            peak = 0
            max_val = (1 << (bits_per_sample - 1)) - 1
            if max_val <= 0:
                return None
            for i in range(0, min(chunk_size, len(data) - offset - 8), bytes_per_sample):
                pos = offset + 8 + i
                if pos + bytes_per_sample > len(data):
                    break
                if bytes_per_sample == 1:
                    # 8-bit PCM is unsigned, centred on 128
                    sample = data[pos] - 128
                else:
                    sample = int.from_bytes(
                        data[pos:pos + bytes_per_sample], 'little', signed=True
                    )
                abs_sample = abs(sample)
                if abs_sample > peak:
                    peak = abs_sample
            return duration, peak / max_val
        offset += 8 + chunk_size + (chunk_size & 1)
    return None


def main() -> int:
    failures: list[tuple[str, str]] = []
    checks_ran = 0

    # ── font checks ──────────────────────────────────────────────────────
    for rel, (lo, hi, label) in FONTS.items():
        path = ASSETS / rel
        if not path.exists():
            failures.append((rel, 'missing'))
            continue
        data = path.read_bytes()
        if data[:4] not in (b'\x00\x01\x00\x00', b'OTTO', b'true', b'ttcf'):
            failures.append((rel, 'not a TrueType/OpenType signature'))
            continue
        got, want = font_coverage(data, lo, hi)
        checks_ran += 1
        if got < want:
            failures.append((rel, '%s coverage %d/%d, so some glyphs would render as boxes' % (label, got, want)))
        else:
            print('  ok    font   %-38s %10d bytes  %s %d/%d' % (rel, len(data), label, got, want))

    # ── audio checks ──────────────────────────────────────────────────────
    pack_path = ASSETS / 'pack' / 'pack.sat.json'
    audio_dir = ASSETS / 'pack' / 'audio'

    pack_entries: dict[str, dict] = {}
    pack_audio_paths: set[str] = set()
    if pack_path.exists():
        with open(pack_path, 'r', encoding='utf-8') as f:
            pack_data = json.load(f)
        pack_entries = pack_data.get('entries', {})
        for eid, entry in pack_entries.items():
            audio_rel = entry.get('audio')
            if audio_rel:
                pack_audio_paths.add(audio_rel)

    if audio_dir.exists():
        wav_files = sorted(audio_dir.glob('*.wav'))
        files_on_disk: set[str] = set()

        for wav in wav_files:
            rel_path = 'pack/audio/' + wav.name
            files_on_disk.add(rel_path)
            data = wav.read_bytes()
            checks_ran += 1

            # Check 1: RIFF/WAVE header
            header_info = _parse_wav_header(data)
            if header_info is None:
                failures.append((rel_path, 'not a valid RIFF/WAVE file'))
                continue

            sr, bps, ch = header_info
            duration_peak = _wav_duration_and_energy(data)
            if duration_peak is None:
                failures.append((rel_path, 'could not parse WAV data chunk'))
                continue

            duration, peak = duration_peak

            # Check 2: duration > 0.3 s
            if duration <= 0.3:
                failures.append((rel_path, 'duration %.2f s is too short (must be > 0.3 s)' % duration))
                continue

            # Check 3: not pure silence
            if peak < 1e-6:
                failures.append((rel_path, 'pure silence (peak amplitude %.6f)' % peak))
                continue

            # Check 4: has a pack entry pointing at it
            if rel_path not in pack_audio_paths:
                failures.append((rel_path, 'file exists on disk but no pack entry references it'))
                continue

            print('  ok    audio  %-38s %10d bytes  %.2fs  %d Hz %d-bit %dch' % (
                rel_path, len(data), duration, sr, bps, ch))

        # Check 5: every pack audio path resolves to a file on disk
        for rel_path in sorted(pack_audio_paths):
            if rel_path not in files_on_disk:
                failures.append((rel_path, 'pack entry references this file but it does not exist'))
    else:
        # No audio directory — check that the pack has no audio references either
        if pack_audio_paths:
            failures.append(('pack/audio/', 'audio directory missing but pack references audio files'))

    for rel, why in failures:
        print('  FAIL         %-38s %s' % (rel, why))

    total_fonts = len(FONTS)
    audio_checked = len(list(audio_dir.glob('*.wav'))) if audio_dir.exists() else 0
    total = total_fonts + audio_checked
    print('\n%d of %d assets are real.' % (total - len(failures), total))
    if failures:
        print('\nA stub font means Ol Chiki characters render as empty boxes.')
        print('A bad WAV means the play button works but the child hears silence or static.')
        return 1
    return 0


def selftest() -> int:
    """
    Run the WAV checks against files built here, in memory.

    The audio checks shipped without ever having executed, because there is no
    pack/audio/ directory yet, so `verify_assets.py` printed "4 of 4 assets are
    real" and exercised none of them. Two bugs were sitting in that unrun code:
    a WAVE_FORMAT_EXTENSIBLE header was rejected as "not a valid RIFF/WAVE
    file", and 24-bit audio measured as pure silence. Both are the sort that
    only appear the day the real files arrive, on the one day nobody has time.

        python tools/verify_assets.py --selftest
    """
    import math

    def wav(bits=16, seconds=1.0, rate=48000, amp=0.8, channels=1, extensible=False):
        frames = int(rate * seconds)
        width = bits // 8
        max_val = (1 << (bits - 1)) - 1
        body = bytearray()
        for i in range(frames):
            value = int(amp * max_val * math.sin(2 * math.pi * 440 * i / rate))
            if bits == 8:
                sample = bytes([(value + 128) & 0xFF])
            else:
                sample = value.to_bytes(width, 'little', signed=True)
            body += sample * channels
        block_align = width * channels
        if extensible:
            fmt = struct.pack('<HHIIHH', WAVE_FORMAT_EXTENSIBLE, channels, rate,
                              rate * block_align, block_align, bits)
            fmt += struct.pack('<H', 22) + struct.pack('<HI', bits, 3)
            fmt += struct.pack('<H', WAVE_FORMAT_PCM) + bytes.fromhex(
                '000000001000800000aa00389b71')
        else:
            fmt = struct.pack('<HHIIHH', WAVE_FORMAT_PCM, channels, rate,
                              rate * block_align, block_align, bits)
        chunks = b'WAVE' + b'fmt ' + struct.pack('<I', len(fmt)) + fmt
        chunks += b'data' + struct.pack('<I', len(body)) + bytes(body)
        return b'RIFF' + struct.pack('<I', len(chunks)) + chunks

    # (name, bytes, expect_parses, expect_long_enough, expect_audible)
    cases = [
        ('16-bit 48k mono tone',        wav(),                          True,  True,  True),
        ('16-bit stereo tone',          wav(channels=2),                True,  True,  True),
        ('8-bit tone',                  wav(bits=8),                    True,  True,  True),
        ('24-bit tone',                 wav(bits=24),                   True,  True,  True),
        ('32-bit tone',                 wav(bits=32),                   True,  True,  True),
        ('WAVE_FORMAT_EXTENSIBLE',      wav(extensible=True),           True,  True,  True),
        ('pure silence',                wav(amp=0.0),                   True,  True,  False),
        ('too short, 0.1s',             wav(seconds=0.1),               True,  False, True),
        ('not a wav at all',            b'ID3' + bytes(400),            False, False, False),
        ('truncated header',            b'RIFF' + bytes(8),             False, False, False),
    ]

    failures = 0
    for name, data, want_parse, want_long, want_audible in cases:
        header = _parse_wav_header(data)
        measured = _wav_duration_and_energy(data)
        parsed = header is not None and measured is not None
        long_enough = bool(measured and measured[0] > 0.3)
        audible = bool(measured and measured[1] >= 1e-6)

        wrong = (parsed != want_parse
                 or (want_parse and long_enough != want_long)
                 or (want_parse and audible != want_audible))
        if wrong:
            failures += 1
            print('  FAIL   %-26s parses=%s long=%s audible=%s  (wanted %s/%s/%s)'
                  % (name, parsed, long_enough, audible, want_parse, want_long, want_audible))
        else:
            detail = '%.2fs peak %.3f' % measured if measured else 'rejected'
            print('  ok     %-26s %s' % (name, detail))

    print('')
    print('%d of %d WAV cases behaved as intended.' % (len(cases) - failures, len(cases)))
    return 1 if failures else 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    sys.exit(main())

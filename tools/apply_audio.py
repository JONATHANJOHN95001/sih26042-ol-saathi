# -*- coding: utf-8 -*-
"""
Drop recorded or Bhashini audio into the app.

Takes the zip that tools/make_recording_studio.py exports, or a folder of WAV
files named by entry id, validates every clip, copies them into the APK's
assets and writes the audio fields into the pack. After this runs the play
buttons light up with no code change.

    python tools/apply_audio.py santali-recordings.zip --provenance native
    python tools/apply_audio.py bhashini/out/audio --provenance bhashini

Nothing is written without --write, so a run always shows what it would do
first. Files that fail validation are reported and skipped rather than shipped,
because a corrupt clip in the pack means a play button that does nothing in
front of a judge.
"""
import argparse
import io
import json
import os
import shutil
import struct
import sys
import zipfile

PACK = 'app/src/main/assets/pack/pack.sat.json'
AUDIO_DIR = 'app/src/main/assets/pack/audio'

PROVENANCE = {
    'native': 'Recorded by a Santali speaker',
    'bhashini': 'Synthesised by Bhashini Santali TTS',
}


def read_wav_header(data):
    """
    Validate a WAV and return (channels, rate, bits, seconds).

    Raises ValueError with a specific reason. A silent 44-byte header is a
    real failure mode of browser recording, so a clip with no samples is
    rejected rather than shipped as a working file.
    """
    if len(data) < 44:
        raise ValueError('shorter than a WAV header (%d bytes)' % len(data))
    if data[0:4] != b'RIFF' or data[8:12] != b'WAVE':
        raise ValueError('not a RIFF/WAVE file')

    pos, fmt, samples = 12, None, 0
    while pos + 8 <= len(data):
        cid = data[pos:pos + 4]
        size = struct.unpack('<I', data[pos + 4:pos + 8])[0]
        body = data[pos + 8:pos + 8 + size]
        if cid == b'fmt ' and len(body) >= 16:
            _, channels, rate, _, _, bits = struct.unpack('<HHIIHH', body[:16])
            fmt = (channels, rate, bits)
        elif cid == b'data':
            samples = len(body)
        pos += 8 + size + (size & 1)

    if fmt is None:
        raise ValueError('no fmt chunk')
    if samples == 0:
        raise ValueError('no audio data, the recording is empty')

    channels, rate, bits = fmt
    seconds = samples / float(rate * channels * max(bits // 8, 1))
    if seconds < 0.25:
        raise ValueError('only %.2fs long, that is not a spoken phrase' % seconds)
    return channels, rate, bits, seconds


def collect(source):
    """Return {entry_id: bytes} from a zip or a directory."""
    clips = {}
    if os.path.isdir(source):
        for name in sorted(os.listdir(source)):
            if name.lower().endswith('.wav'):
                with open(os.path.join(source, name), 'rb') as fh:
                    clips[name[:-4]] = fh.read()
    elif zipfile.is_zipfile(source):
        with zipfile.ZipFile(source) as z:
            for name in sorted(z.namelist()):
                base = os.path.basename(name)
                if base.lower().endswith('.wav'):
                    clips[base[:-4]] = z.read(name)
    else:
        raise SystemExit('not a zip or a directory: %s' % source)
    return clips


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('source', help='recordings zip, or a folder of WAV files')
    ap.add_argument('--provenance', required=True, choices=sorted(PROVENANCE),
                    help='who or what produced this audio')
    ap.add_argument('--reviewer', default='',
                    help='name of the speaker, recorded in the pack for native audio')
    ap.add_argument('--write', action='store_true',
                    help='actually copy files and update the pack')
    args = ap.parse_args()

    if args.provenance == 'native' and not args.reviewer and args.write:
        raise SystemExit(
            'a native recording needs --reviewer "Name".\n'
            'The app shows who recorded a line, and an anonymous claim is not '
            'one a teacher can check.')

    pack = json.load(io.open(PACK, encoding='utf-8'))
    entries = pack['entries']
    clips = collect(args.source)

    if not clips:
        raise SystemExit('no WAV files found in %s' % args.source)

    good, bad, unknown = {}, [], []
    for eid, data in sorted(clips.items()):
        if eid not in entries:
            unknown.append(eid)
            continue
        try:
            ch, rate, bits, secs = read_wav_header(data)
            good[eid] = (data, secs, '%d Hz %d-bit %s' % (rate, bits, 'mono' if ch == 1 else 'stereo'))
        except ValueError as exc:
            bad.append((eid, str(exc)))

    print('Source      : %s' % args.source)
    print('Provenance  : %s (%s)' % (args.provenance, PROVENANCE[args.provenance]))
    print('Pack entries: %d' % len(entries))
    print()
    print('Usable clips: %d' % len(good))
    for eid, (_, secs, spec) in sorted(good.items())[:5]:
        print('   %-16s %5.1fs  %s' % (eid, secs, spec))
    if len(good) > 5:
        print('   ... and %d more' % (len(good) - 5))

    if bad:
        print()
        print('REJECTED (%d), these will not ship:' % len(bad))
        for eid, why in bad:
            print('   %-16s %s' % (eid, why))
    if unknown:
        print()
        print('Not in the pack (%d), ignored: %s' % (len(unknown), ', '.join(unknown[:8])))

    missing = [e for e in entries if e not in good]
    print()
    print('Coverage    : %d of %d entries (%.0f%%)'
          % (len(good), len(entries), 100.0 * len(good) / len(entries)))
    if missing:
        print('Still silent: %s%s'
              % (', '.join(sorted(missing)[:8]), ' ...' if len(missing) > 8 else ''))

    if not args.write:
        print()
        print('Nothing written. Re-run with --write to apply.')
        return

    if not os.path.isdir(AUDIO_DIR):
        os.makedirs(AUDIO_DIR)

    for eid, (data, _, _) in good.items():
        with open(os.path.join(AUDIO_DIR, eid + '.wav'), 'wb') as fh:
            fh.write(data)
        entries[eid]['audio'] = 'pack/audio/%s.wav' % eid
        entries[eid]['audioProvenance'] = args.provenance
        if args.reviewer:
            entries[eid]['audioRecordedBy'] = args.reviewer

    pack.setdefault('provenance', {})['ttsService'] = PROVENANCE[args.provenance]
    with io.open(PACK, 'w', encoding='utf-8', newline='\n') as fh:
        json.dump(pack, fh, ensure_ascii=False, indent=2, sort_keys=True)
        fh.write('\n')

    total = sum(len(d) for d, _, _ in good.values())
    print()
    print('Wrote %d clips to %s (%.1f MB) and updated the pack.'
          % (len(good), AUDIO_DIR, total / 1024.0 / 1024.0))
    print('Rebuild the APK and the play buttons will be live.')


if __name__ == '__main__':
    main()

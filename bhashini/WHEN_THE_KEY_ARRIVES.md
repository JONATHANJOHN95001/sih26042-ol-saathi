# When the Bhashini key arrives

The Udyat key for `sih_vernacular_pedagogy` was requested on 1 September 2026
and is pending. Everything that could be built without it has been built, so
what is left here genuinely needs credentials.

Read `CONTRIBUTING.md` first, in particular the rule everything else follows:
**the app never invents output**, and every Santali string carries a provenance
label saying where it came from.

Only stage 1 and stage 3 are open work. Stages 2, 4 and 5 are already done and
are written down here as invariants, because the run described below can break
every one of them.

## Context you must not get wrong

- **Bhashini runs at build time only.** No runtime network calls, ever. The
  offline-on-a-2GB-tablet deliverable depends on this, and `NetworkGuard`
  counts calls on the Proof screen. Do not add a runtime client.
- The shipped pack is `app/src/main/assets/pack/pack.sat.json`, 53 entries,
  currently produced by IndicTrans2, `provenance.translationService =
  "prajdabre/rotary-indictrans2-en-indic-1B"`.
- The reason to use Bhashini at all is **audio**. There is no open Santali TTS.
  Bhashini's IITM model (`6a7dab589f0dd063fe3cfd27`, published 2026-08-13) is
  the only synthesis route that exists.
- Bhashini output is still machine translation. The honest label is
  "Machine translation · Bhashini". Do not write the word *verified* anywhere
  unless a named Santali speaker has actually read the strings.

## Stage 1: make the credentials work

This is the open work. `bhashini/test-connection.mjs` and
`bhashini/build_pack.mjs` assume the older ULCA credential trio
(`BHASHINI_USER_ID`, `BHASHINI_ULCA_KEY`, `BHASHINI_INFERENCE_KEY`) posted to
`meity-auth.ulcacontrib.org/.../getModelsPipeline`.

The key we were issued comes from the newer **Bhashini Udyat** dashboard, and I
do not know whether it uses the same three fields, the same endpoint or the same
header names. **Find out from the credentials themselves, not from memory and
not from what these scripts already assume.** Report what the auth shape
actually is before you change anything, then adapt `bhashini/test-connection.mjs`
to it and run it. All four of its checks must print before you go further.

If a check fails, stop and report the raw response. Do not work around it.

`bhashini/selftest.mjs` stands the whole generator up against a local mock and
must keep passing after you touch the auth layer. Run it before and after.
The duplicate generator is already dealt with: `tools/build_pack.mjs` is a stub
that refuses to run, and `bhashini/build_pack.mjs` is the only live one.

## Stage 2: provenance, already fixed, do not undo

Three defects would have made the app lie the moment audio shipped. Fixed and
tested before the key arrived.

**2a.** Both generators write `audioProvenance: 'bhashini'` on the same branch
that writes the wav. The app gates playback on that field, not on the file, so a
wav without it loads as `AudioProvenance.NONE`, ships inside the APK, and reads
on screen as "no audio yet". `bhashini/selftest.mjs` fails if a wav ever arrives
unlabelled.

**2b.** The service name comes from the pack, not from a compiled-in constant.
`VerifiedContentPack.serviceName` reads `provenance.platform`, trimmed to its
opening clause, falling back to `provenance.translationService`, and
`Translation.provenanceLabel` appends it. The printed flashcard footer reads the
same value. **So the provenance block you write matters: whatever you put in
`platform` is what a teacher reads on a 12sp chip.** Keep it short and true.

**2c.** Audio provenance is on screen beside the play button on both
teacher-facing screens, and the button is enabled only when the wav exists
**and** the pack says where the voice came from. `ShowClassActivity` still shows
no provenance at all, because a six-year-old cannot judge a translation; instead
its callers hand it audio only when that audio is labelled.

## Stage 3: the run itself

This is the other piece of open work.

```
node bhashini/build_pack.mjs --dry-run
node bhashini/build_pack.mjs --limit 3
node bhashini/build_pack.mjs --compare
```

Read the `--compare` output before running the full build. The decision it
feeds is genuine: IndicTrans2's Santali is already decent, so if Bhashini's is
worse we keep the current text and take **only the audio** from Bhashini. In
that case the pack ends up with IndicTrans2 text and `audioProvenance:
"bhashini"`, and the provenance block must say exactly that rather than
attributing everything to one service.

Do not run `--install` until you have read that output and decided.

## Stage 4: the audio has to be checked, and it has to fit

**The checker exists.** `tools/verify_assets.py` verifies every wav is genuine
RIFF/WAVE (including `WAVE_FORMAT_EXTENSIBLE`, which Bhashini's 48 kHz output
may well use), longer than 0.3 s, not silence, and that pack entries and files
on disk agree in both directions. It has a self-test, because until audio exists
it has nothing to run against:

```
python tools/verify_assets.py --selftest
python tools/verify_assets.py
```

Ten synthetic cases, all passing. If you change those checks, add a case. Do not
let that code go back to never having executed.

**Sizing is still open, and needs the real files.** Bhashini returns 48 kHz WAV.
Fifty-three lines of roughly three seconds is on the order of 15 MB against a
release APK that is currently 4.23 MB and documented as such. Report the real
measured total, then propose one option and wait: keep 48 kHz WAV, downsample to
22.05 kHz mono, or transcode to OGG Vorbis (`MediaPlayer` handles it, and the
pack stores the path, so the extension is free to change). Keep any 48 kHz
masters out of the APK.

Every number in `README.md`, `HANDOVER.md`,
`PROBLEM_STATEMENT_TRACEABILITY_MATRIX.md` and `CONTRIBUTING.md` that mentions APK
size, audio count or "no Santali audio" is stale the moment the pack lands.
Re-measure, do not re-estimate.

## Stage 5: the latency measurement, already fixed, do not merge them again

Deliverable 2 is voice-to-voice under three seconds, and the app now measures
two separate things and reports them separately on the Proof screen.

- `latencyHistory` is the pack lookup. It is a hash lookup, it lands near zero,
  and it is labelled "not the deliverable".
- `voiceLatencyHistory` runs from the moment speech recognition returns Hindi to
  `MediaPlayer.onPrepared`, the first instant sound can leave the tablet. The
  3-second ceiling applies to this one and only this one.

**Do not put them back in one list.** They were one list, and the median came
out of dozens of near-zero lookups blended with a handful of real voice spans,
which cleared the ceiling while measuring nothing anyone had asked about.

The voice number is empty until the pack ships audio, and the Proof screen says
so in words rather than borrowing the lookup figure. After your run it will
start filling in. Expect it to be much worse than the lookup number and report
it honestly. A measured 1.4 s is worth more than a claimed 5 ms that measures
the wrong thing.

## Done means

```
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:testDebugUnitTest
python tools/verify_assets.py --selftest
python tools/verify_assets.py
python tools/verify_traceability.py
node bhashini/selftest.mjs
```

all pass, 36 tests or more, plus the app installed on the
`TribalFLN_LowSpec_API28` emulator with a screenshot of a play button that
works, an audio provenance chip beside it, and a real voice-to-voice figure on
the Proof screen. "It compiles" is not done. Report failures with their output.

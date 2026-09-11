# Key day: the order to do things in

Read `WHEN_THE_KEY_ARRIVES.md` first for the invariants. This file is only the
sequence, written down because it has one trap in it that costs a day.

## 0. Before anything, test TTS

The reason to use Bhashini at all is audio. Translation you already have from
IndicTrans2 at build time.

The ULCA registry lists a Santali TTS model, `6a7dab589f0dd063fe3cfd27`
(Bhashini-IITM, FastPitch + HiFi-GAN, CC-BY-4.0), and its
`inferenceEndPoint.callbackUrl` was an **empty string** when last checked on
5 September 2026. A modelId with no endpoint behind it is worth nothing.

**So establish whether TTS actually returns audio before you build anything on
it.** If it does not, stop and go to the recording studio route in step 6. You
want to know this at 9am, not at 9pm.

## 1. Verify the credential shape

```
node bhashini/test-connection.mjs
```

All four checks must print. The scripts assume the older ULCA trio
(`BHASHINI_USER_ID`, `BHASHINI_ULCA_KEY`, `BHASHINI_INFERENCE_KEY`) posted to
`meity-auth.ulcacontrib.org`. The Udyat dashboard key may differ in fields,
endpoint or header names.

**Find the shape out from the credential itself. Do not assume, and do not work
around a failure.** Report the raw response instead.

```
node bhashini/selftest.mjs
```

Must pass before and after you touch the auth layer.

## 2. Regenerate the pack

```
node bhashini/build_pack.mjs
```

Requests `taskType: 'translation'` and, if available, `taskType: 'tts'`,
writing base64 `audioContent` out to `bhashini/out/audio`.

## 3. THE TRAP: re-apply the NIPUN codes

```
python tools/add_nipun_codes.py
```

**Step 2 regenerates `pack.sat.json` from scratch and destroys two things:**
every published NIPUN outcome code, and the hand-correction to `p01`.

Skip this and your worksheets silently lose the NIPUN Bharat alignment, which
is a graded deliverable. The script is idempotent and understands every label
vintage the packs have ever used, so running it twice is harmless.

## 4. Drop the audio in

```
python tools/apply_audio.py bhashini/out/audio --provenance bhashini
```

The honest label for this is "Machine translation, Bhashini". It is synthesis,
not a person. **Do not write the word "verified" anywhere near it.**

## 5. Rebuild and re-verify

```
JAVA_HOME="/c/Program Files/Java/jdk-17" ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleRelease
```

Then on a device, check:

- Check & Proof still reads 0 network calls when no key is compiled into the
  shipped APK. Credentials live in `local.properties`, which is gitignored.
- Pack audio no longer reads "0 of 53 entries have WAV".
- **Voice to voice now measures.** It has never been measured with a human
  voice; the screen says so, and it must show a real number rather than a
  claim. Use the GMS emulator or a real phone: `TribalFLN_LowSpec_API28` has
  no speech recogniser, so the voice path cannot run on it at all.
- Re-check `p01`. Machine translation transliterated English "hello" rather
  than translating it in 10 of 17 languages. The script guard cannot catch this
  because a transliteration is valid script.

## 6. If TTS is unavailable

`verification/santali-recording-studio.html` is a self-contained offline page
with the fonts embedded. About twenty minutes with a Santali speaker.

```
python tools/apply_audio.py <recordings.zip> --provenance native --reviewer "Name" --write
```

This is the **stronger** outcome, not the fallback. "Recorded by a native
speaker" beats "synthesised" in front of a judge, and the same session gets a
person to read the 53 lines, which closes gap 2 and catches the `ᱦᱚᱞᱳ` defect.

# When the Bhashini keys arrive

You do not need a second app. The app looks Santali up in a JSON file and does
not care who produced it, so switching to Bhashini is a build-time swap. Nothing
in `app/src/main/java/` changes.

What Bhashini adds that IndicTrans2 cannot: **Santali text to speech**. There is
no open Santali TTS and Android has no Santali voice, so `pack/audio/*.wav` can
only come from Bhashini's IITM model. That is the real reason to bother.

---

## The three commands

Set the keys once per shell. Never paste them into a file; the repo is public.

```bash
$env:BHASHINI_USER_ID="..."; $env:BHASHINI_ULCA_KEY="..."; $env:BHASHINI_INFERENCE_KEY="..."
```

**1. Prove the credentials work and Santali is reachable.**

```bash
node bhashini-test.mjs
```

Four checks: credentials, Hindi to Santali translation, Santali text to speech,
Hindi speech to text. If translation fails, stop and read the error rather than
continuing.

**2. See what would happen, without spending any calls.**

```bash
node tools/build_pack.mjs --dry-run
```

Should report 53 strings.

**3. Compare Bhashini against what is already shipped, before replacing it.**

```bash
node tools/build_pack.mjs --compare
```

This translates all 53 through Bhashini and diffs them against the shipped
IndicTrans2 pack without writing anything. Read the output. If Bhashini's
Santali is better, continue. If it is worse, keep what you have and use Bhashini
only for the audio.

**4. Build for real.**

```bash
node tools/build_pack.mjs
```

Writes `app/src/main/assets/pack/pack.sat.json` and the wav files. Resumable and
saves every ten entries, so a dropped connection costs almost nothing.

---

## Then

- `python tools/verify_assets.py` must still pass.
- `./gradlew :app:testDebugUnitTest` must still pass. The pack tests run against
  whatever is shipped, so they will catch contamination or collisions in the new
  content the same way they did for IndicTrans2.
- The provenance chip changes by itself, because it reads
  `provenance.translationService` from the pack. Nothing to edit.
- The Proof screen's audio section will start reporting a real count instead of
  "0 of 53 entries have audio".

---

## One thing to decide, not assume

The label currently reads **"Machine translation · IndicTrans2"** rather than
"Verified", because nothing has been checked by a Santali speaker.

Bhashini output is still machine translation. It is a government platform, which
is a much stronger provenance claim, but it is not human verification. So the
honest label becomes **"Machine translation · Bhashini"**, not "Verified".

Only use the word verified if an actual Santali speaker has read the strings. If
that ever happens, record who and when in the provenance block, because that is
the claim worth being able to back up.

---

## What we are NOT doing

**No live Bhashini calls at runtime.** The problem statement requires full
offline operation, the classroom has no signal, and an online call on stage is a
failure waiting to happen. Bhashini runs once, at build time, on a laptop with
wifi. That is the whole design and it is what makes the offline claim true.

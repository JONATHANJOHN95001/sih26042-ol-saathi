# The Bhashini build

Self-contained. Everything needed to produce the Santali content pack from
Bhashini instead of IndicTrans2 lives in this folder, and **nothing here can
touch the working app until you copy it across yourself.**

## What this is, and what it is not

This is **not** a second copy of the Android app, and it should not become one.

The app reads Santali out of a JSON file and has no idea who produced it. So
"the Bhashini version" is a different *content pipeline*, not a different app.
Swapping source means replacing one file and a folder of wav files. Zero Kotlin
changes.

A forked app would diverge within a day, need every fix applied twice, and give
you two things to demo. This folder gives you the same insurance without that.

## Why bother with Bhashini at all

One reason, and it is a good one: **audio**.

There is no open Santali text-to-speech anywhere, and Android ships no Santali
voice. Bhashini's IITM model is the only Santali TTS that exists. The problem
statement explicitly requires *"synthesised audio in target tribal languages"*,
and right now the pack has none.

The translation quality question is genuinely open. IndicTrans2 1B already
produces good Santali, using `ᱜᱚᱲᱚᱢ ᱟᱭᱳ` for grandmother and `ᱠᱩᱠᱞᱤ` for
question rather than Hindi loanwords. Bhashini may or may not beat it. That is
what `--compare` is for.

## Files

| File | What |
|---|---|
| `test-connection.mjs` | Four checks: credentials, Hindi→Santali, Santali TTS, Hindi ASR |
| `build_pack.mjs` | The generator. Resumable, records provenance, writes to `out/` |
| `phrases.hi.json` | The 40 classroom phrases |
| `RUNBOOK.md` | The commands in order, with the decision points |
| `out/` | Where a run lands. Never written to the app directly. |

Lesson content is read from `../content/lessons.json`, which stays the single
source of truth so the two pipelines cannot drift apart.

## Prove it works before you have keys

```bash
node bhashini/selftest.mjs
```

Stands up a local server that answers like Bhashini, runs the real generator
against it, and checks what comes out: entries written, provenance recorded,
ids distinct, no foreign-script contamination, and audio that decodes to a
genuine RIFF/WAVE file.

Everything is exercised except the remote service. When real keys arrive the
only untested link is whether the live API returns the shape the mock imitates.

This exists because handing someone a "just paste your keys" button that has
never executed is precisely the kind of confident, untested thing this project
has had to remove more than once.

## Running it

Set the keys once per shell. Never put them in a file; this repository is public.

```bash
$env:BHASHINI_USER_ID="..."; $env:BHASHINI_ULCA_KEY="..."; $env:BHASHINI_INFERENCE_KEY="..."
```

```bash
node bhashini/test-connection.mjs
node bhashini/build_pack.mjs --dry-run
node bhashini/build_pack.mjs --compare
node bhashini/build_pack.mjs
```

Then, only if you are satisfied with what `--compare` showed you, one command
does the copy and keeps a timestamped backup of what it replaced:

```bash
node bhashini/build_pack.mjs --install
```

Then `./gradlew :app:testDebugUnitTest`. The pack tests run against whatever is
shipped, so they will catch contamination or collisions in the new content the
same way they caught them in the old.

## The one thing to get right

The provenance chip reads from `provenance.translationService`, so it updates
itself. But the wording matters.

Bhashini output is still **machine translation**. It comes from a government
platform, which is a far stronger provenance claim than ours, but it is not
human verification. The label should read "Machine translation · Bhashini".

Use the word *verified* only if a Santali speaker has actually read the strings.
If that happens, record their name and the date in the provenance block, because
that is the claim worth being able to back up. `verification/santali-review-sheet.html`
exists to make that review easy.

## What we deliberately do not do

**No Bhashini calls at runtime.** The statement requires full offline operation,
the target classrooms have no signal, and a network call on stage is a failure
waiting to happen. Bhashini runs once, here, on a laptop with wifi. That is what
makes the offline claim true rather than aspirational.

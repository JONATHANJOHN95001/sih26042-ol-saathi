# Ol Saathi — task 6

## Your last block

Verified independently. Four markdown files at root, three coherent commits, no
secrets, no keystore, no APK tracked. The README reads well. Good work.

Two things I cleaned up after it, so you know: `pack/audio/p01.wav` was half a
second of pure silence that nothing referenced, and `pack.sat.SAMPLE.bak` was
504 lines of invented Santali from the sample era. Both were committed. Neither
is now, and `.gitignore` blocks placeholder wavs.

**The content pack is now real.** 53 of 53 entries, genuine machine translation
from AI4Bharat IndicTrans2 1B, zero foreign-script contamination. The provenance
chip reads "Machine translation · IndicTrans2" rather than "Verified", because
nothing in it has been checked by a Santali speaker and we do not claim
otherwise.

---

## Priority 1 — the demo will fail on a fresh tablet

This is the important one and nobody has looked at it.

`HindiSpeechInput` sets `EXTRA_PREFER_OFFLINE = true`. On Android, offline
speech recognition only works if the user has **downloaded the Hindi language
pack** through Settings. On a tablet straight out of the box it is not there.

So the sequence on stage is: aeroplane mode on, press to talk, `PREFER_OFFLINE`
finds no local model, the recogniser reaches for the network, there is no
network, and you get `ERROR_NETWORK`. Your handler then prints **"Network error
(try offline mode)"** while the app is already in offline mode, which is the
least helpful message it could give.

The whole pitch is that this works with no signal. Failing at exactly that point
would be the worst possible moment.

### What to build

**A pre-flight self-test screen**, reachable from the lesson list next to Live
Proof, that a person runs once before the demo. One button, then a green or red
line for each check:

| Check | How |
|---|---|
| Content pack loads | entry count, and that it is greater than zero |
| Ol Chiki font | `Typeface.createFromAsset` succeeds, plus a live-rendered `ᱚᱞ ᱪᱤᱠᱤ` |
| Devanagari font | same |
| **Hindi offline recognition** | see below. **This is the one that matters.** |
| Microphone permission | granted or not |
| Storage writable | can create a file via FileProvider |
| Worksheet generates | actually produce a PDF into cache and report its byte size |

For the offline recognition check, ask the system rather than guessing. Send
`RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS` and read
`EXTRA_SUPPORTED_LANGUAGES`, and on API 33 and above also use
`SpeechRecognizer.checkRecognitionSupport()` with `EXTRA_PREFER_OFFLINE` set, so
you learn whether `hi-IN` is available **on device** rather than only online.

When it is missing, do not just say "unavailable". Say what to do about it:

> Hindi offline speech is not installed. Settings → System → Languages & input →
> On-device speech recognition → add Hindi. Needed before demonstrating in
> aeroplane mode.

**Also fix the misleading error string.** `ERROR_NETWORK` while offline should
read something like "No offline Hindi model and no network. Install the Hindi
speech pack.", not "try offline mode".

**Add the setup step to `DEMO.md`** as a numbered pre-flight item, before the
install instructions. Somebody following that document on the morning of the
demo needs to hit this step.

---

## Priority 2 — the assessment prompts are shipped but unused

The pack contains three `kind == "check"` entries, the comprehension questions
for the lesson. Nothing in the app ever reads them. I checked: every filter is
on `"phrase"` or `"lesson"`.

The problem statement names **"lesson scripts, activity instructions, and
assessment prompts"** as the three content types the system must handle. We
translated all three and surface two.

Add them to the classroom flow: after the last line of a lesson, show the
comprehension questions in Hindi and Santali, the same way lines are shown. No
scoring, no marks, no database. Just the question in both languages so a teacher
can ask it aloud. Keep it small.

Also include them in the worksheet PDF, under a heading, since a worksheet
without questions is half a worksheet.

---

## Priority 3 — only if the first two are done

Put the pre-flight result on the Proof screen too, as a single line: "Pre-flight:
7 of 7 passing" or "6 of 7, Hindi offline speech missing". A judge should be able
to see at a glance that the device was checked.

---

## Do not touch

```
app/src/main/assets/pack/                     the content pack
app/src/main/java/app/olsaathi/content/       Translation.kt, VerifiedContentPack.kt
app/src/test/java/app/olsaathi/content/       the pack tests
tools/
PROBLEM_STATEMENT_TRACEABILITY_MATRIX.md
```

## Gates

```
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:assembleDebug
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:testDebugUnitTest
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:assembleRelease
```

All pass, the pre-flight screen runs all seven checks and reports each honestly,
and `DEMO.md` tells someone how to install the Hindi speech pack before they
stand up in front of anyone.

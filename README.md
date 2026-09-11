# Ol Saathi (ओल साथी)

![CI](https://github.com/JONATHANJOHN95001/sih26042-ol-saathi/actions/workflows/ci.yml/badge.svg)

**SIH26042 · Government of Jharkhand · Team INNOV8, JAIN Deemed-to-be University**

Ol Saathi is an offline-first Android app that lets a Hindi-speaking primary teacher deliver a lesson in Santali (Ol Chiki script) — or 16 other languages across 10 scripts — without knowing the language. The teacher speaks Hindi; the app translates, displays, and speaks the target language. Fully offline, on a low-cost tablet. No internet required.

---

## The problem

Over 5,000 tribal-area primary schools in Jharkhand need foundational literacy and numeracy (FLN) teaching in Santali, Ho, and Mundari. Teachers are Hindi-medium trained and cannot speak these languages. PALASH (Programme for Adventure Learning in Academic Schools of Habitat) works well in settings where bilingual resources exist, but it cannot scale to thousands of classrooms where no bilingual teacher or bilingual material is available. There is no digital Ol Chiki keyboard, no Santali TTS engine on Android, and no internet in most of these schools.

Ol Saathi bridges this gap by shipping pre-translated content offline.

---

## The four graded requirements

| # | Requirement | Status |
|---|-------------|--------|
| 1 | Hindi to tribal-language translation, minimum one language | **Done** — Santali (Ol Chiki, primary target) plus 16 more languages across 9 additional scripts. Every entry produced by AI4Bharat IndicTrans2 (prajdabre/rotary-indictrans2-en-indic-1B, 1.1B parameters), run locally at build time. Bhashini access arrived on 10 Sep 2026; it translates sentences outside the pack and speaks Santali live (IIT Madras voice). |
| 2 | Real-time voice-to-voice translation, under 3 seconds | **Code done, instrumented** — latency measured at lookup time; not yet measured on hardware with a human voice |
| 3 | Auto-generated bilingual worksheet | **Done** — A4 PDF with Hindi + target script, NIPUN outcome codes, share/print. Also supports importing a teacher's own lesson PDF and building a worksheet from whatever lines the offline pack covers. |
| 4 | Full offline operation on Android 9+ tablet, 2 GB RAM | **Done and measured** — minSdk 28, zero network calls. Run on a 2,046 MB Android 9 device: 43 to 45 MB peak PSS, 656 ms cold start, 3,000 monkey events with no crash. |

### What is not done

- **Santali audio is machine speech that no Santali speaker has heard.** Android has no Santali TTS engine. Meta's MMS covers 1,143 languages and Santali is not one of them; eSpeak has no Santali voice. A generator does exist: AI4Bharat Indic Parler-TTS (ai4bharat/indic-parler-tts, Apache 2.0, auto-approve gate) lists Santali among its official 21 languages, and the build pipeline at `tools/build_pack_audio_parler.py` is wired end-to-end through `tools/apply_audio.py` and [Translation.kt](app/src/main/java/app/olsaathi/content/Translation.kt#L21-L41). Parler is a 0.9B multilingual research model with no published Santali evaluation and no validation by any Santali speaker. Bhashini's IITM model is the only Santali synthesis with institutional backing. Access arrived on 10 Sep 2026 and the app now uses it **live**: with a network, every Santali line plays, labelled as Bhashini machine speech (the text is converted to Devanagari first, because that voice is silent for Ol Chiki). The same voice also produced the **offline** pack audio: all 53 Santali lines ship as OGG clips (`tools/build_pack_audio_iitm.py`), labelled "Synthesised speech · Bhashini" and not checked by a Santali speaker. Two of them (p24, p40) failed the build's automatic noise check narrowly and were accepted after a team member listened; each carries an `audioGate` note in the pack saying so.
- **Latency not measured on hardware with a human voice.** The code instruments every lookup and round trip. The sub-3-second target is expected (lookup is microseconds), but the speech-recogniser leg needs the Hindi offline pack installed on a real device, which the emulator does not have.
- **No line has been checked by a Santali speaker.** Every translation entry carries the model's service ID. If a judge disputes a translation, the answer is "that is AI4Bharat IndicTrans2 output, unchecked, run locally at build time." Lines translated live by Bhashini are labelled as such on screen.

---

## Architecture

**No machine learning runs at runtime.** Zero. All target-language content is translated once, ahead of time, by AI4Bharat IndicTrans2 (prajdabre/rotary-indictrans2-en-indic-1B, 1.1B parameters) run locally at build time, and stored as JSON plus optional pre-rendered WAV files inside the APK. At runtime the app does a hash lookup.

This is not a shortcut. It is the correct design for this problem, and it is what the problem statement's own phrase *"after initial content synchronisation"* permits. Four things follow:

1. **Offline is trivial.** There is nothing left to call.
2. **Sub-3-second latency becomes a measurement, not a hope.** A hash lookup is microseconds.
3. **The demo cannot fail on stage.** No network, no model loading, no variance.
4. **Every translation carries a provenance label on screen.** The service ID that produced each line is stored in the pack, and the UI shows it: "Machine translation · AI4Bharat IndicTrans2" or "Checked by a native speaker" or "Not in the offline pack". Nothing claims a source it does not have. Live Bhashini output is labelled separately: "Machine translation, live · Bhashini" and "Machine voice, live · Bhashini".

---

## The per-script contamination guard

The build pipeline runs a per-script defect check before any entry ships. Anything outside the target script's Unicode block is treated as a defect: defective outputs are retried with different decoding, and if they stay dirty the entry is dropped rather than shipped. An entry that is absent shows the teacher "not in the offline pack"; an entry full of the wrong script looks correct and is not.

This guard caught three real defect classes during the multi-language build:

- **Arabic letters appearing mid-sentence** inside Santali output. The 200M distilled model contaminated 4 of 53 entries this way; the 1.1B model does not, and the guard remains because the 1.1B could regress on a different phrase set.
- **Bengali returned in Devanagari script** rather than Bengali, all 53 entries. IndicTrans2 normalises Indic targets to Devanagari internally and relies on its official pipeline to transliterate back; skipping that step produced correct Bengali words in a script no Bengali reader can parse. The guard flagged every one as foreign-script contamination, and the fix was to add the transliteration step the official pipeline uses.
- **A stray Latin token appended** in 13 of 17 languages on one phrase. Retried with different beam settings and recovered.

Across 17 languages, **17 entries were withheld in total** rather than shipped contaminated. Each pack records which guarantee its guard gives: "strong" for scripts distinct from Devanagari (Ol Chiki, Bengali, Gujarati, Kannada, Malayalam, Odia, Gurmukhi, Tamil, Telugu), where an untranslated echo contains zero target letters and is caught instantly; "weak-shared-script" for languages sharing Devanagari with the Hindi source (Marathi, Nepali, Maithili, Dogri, Konkani, Bodo), where only a verbatim echo is detectable and each pack says so on screen.

---

## PDF lesson import

A teacher can import their own lesson PDF via the **Import a Lesson screen ([LessonPdfImporter.kt](app/src/main/java/app/olsaathi/pdf/LessonPdfImporter.kt), [ImportLessonActivity.kt](app/src/main/java/app/olsaathi/ui/ImportLessonActivity.kt)). It uses PdfBox-Android to extract text, segments Hindi sentences, matches each one against the shipped language pack, and reports a coverage figure — e.g. "12 of 28 lines found in Santali (42%). A bilingual worksheet is generated from whatever lines that matched.

**What it deliberately does not do.** It does not translate arbitrary new Hindi on the device. IndicTrans2 is 1.1B parameters and roughly 4.4 GB in float32; even distilled and int8-quantised it is about 200 MB, on a device with 2 GB of RAM total where this app peaks at 43 MB. There is no version of that which runs on the hardware the problem statement names. So the honest design is match what the pack already knows and tell the teacher plainly which lines it does not. A worksheet built from matched lines is real. A worksheet with machine-guessed filler in it would look identical to a teacher who cannot read the target language, and would be worse than useless in front of a class. The uncovered lines are not thrown away: they are exactly what the problem statement's "after initial content synchronisation" clause exists for.

---

## The provenance rule

Every piece of target-language text the app displays carries a `Provenance` label shown on screen:

| Label | Meaning |
|-------|---------|
| **VERIFIED** | Machine translation from AI4Bharat IndicTrans2 (prajdabre/rotary-indictrans2-en-indic-1B), not checked by a native speaker |
| **SAMPLE** | Placeholder data, not model output — stamped in red across worksheets |
| **TRANSLITERATED** | Hindi respelled in target script — not a translation |
| **UNAVAILABLE** | No match in the pack. Empty string. No fallback. |

The app never invents output. A lookup miss returns an empty string. There is no fallback phrase, no "close enough" match, no generic Santali sentence for unrecognised input. This rule exists because a previous version shipped exactly that, and a Santali speaker would have noticed immediately.

---

## Build and run

### Prerequisites

- **JDK 17** (required by Gradle 8.2). JDK 21+ will fail.
- Android SDK with API 35 platform

### Commands

```bash
# Debug build
JAVA_HOME="/path/to/jdk-17" ./gradlew :app:assembleDebug

# Unit tests (17 tests over the shipped content pack)
JAVA_HOME="/path/to/jdk-17" ./gradlew :app:testDebugUnitTest

# Release build (signed with app/release-key.jks)
JAVA_HOME="/path/to/jdk-17" ./gradlew :app:assembleRelease
```

### Output

- Debug APK: `app/build/outputs/apk/debug/` (~30 MB)
- Release APK: `app/build/outputs/apk/release/` (11.2 MB, up from 4.23 MB. Growth: 1.4 MB fonts for 10 scripts, 436 KB language packs for 17 languages, PdfBox-Android dependency for the PDF lesson import feature.)

### Install on tablet

See [DEMO.md](DEMO.md) for full install steps (USB + sideload).

---

## Project structure

```
app/src/main/java/app/olsaathi/
├── OlSaathiApplication.kt        App startup, pack loading, latency tracking
├── content/
│   ├── Translation.kt            Provenance enum + Translation data class
│   └── VerifiedContentPack.kt    JSON pack loader + lookup engine
├── speech/HindiSpeechInput.kt    Android SpeechRecognizer wrapper (hi-IN, offline)
├── audio/PackAudioPlayer.kt      WAV playback from assets
├── pdf/LessonPdfImporter.kt      PDF text extraction + pack matching for lesson import
├── worksheet/WorksheetPdf.kt     A4 bilingual PDF via PdfDocument
├── worksheet/FlashcardPdf.kt     6-per-page flashcard PDF (NIPUN aligned)
├── worksheet/ScriptFonts.kt      Per-script font loading for 10 scripts
├── util/NetworkGuard.kt          Offline enforcement + call counter
└── ui/
    ├── LessonListActivity.kt     Entry point, lesson browser
    ├── ClassroomActivity.kt      The demo screen
    ├── ImportLessonActivity.kt   Teacher PDF import, coverage report, worksheet gen
    ├── WorksheetActivity.kt      PDF generation + share/print
    ├── ShowClassActivity.kt      Child-facing display (picture + large text)
    └── ProofActivity.kt          Live-proof screen for judges
```

**~17 Kotlin files.** Grew from 11 when 16 more languages, 9 more scripts, PDF import, language picker and flashcards landed.

---

## What is not built

We deliberately did not build:

- ONNX Runtime, semantic search, OCR, Wi-Fi Direct mesh, thermal printing, a custom Ol Chiki keyboard, telemetry overlays, analytics dashboards, flashcards (wait — flashcards *are* built, 7 pages, every one of 53 entries carries a picture). Ho/Mundari translation (the problem statement names Santali, Ho and Mundari, but IndicTrans2 scheduled languages include Santali and exclude Mundari and Ho — so those two cannot be produced by the same route and are not claimed). User accounts, or a backend.

Every one of these was in a previous attempt. None is graded. Together they are why the six that are graded were never finished.

---

## Team and credits

**Team INNOV8** — JAIN Deemed-to-be University, Bengaluru

Team members:

- Jonathan John ([@JONATHANJOHN95001](https://github.com/JONATHANJOHN95001))
- Shinjini Pal ([@Shinjini06](https://github.com/Shinjini06))
- shaiksuhana-630 ([@shaiksuhana-630](https://github.com/shaiksuhana-630))
- SUBANGIVIGNESH30 ([@SUBANGIVIGNESH30](https://github.com/SUBANGIVIGNESH30))
- varshiniT221 ([@varshiniT221](https://github.com/varshiniT221))
- sujinanair ([@sujinanair](https://github.com/sujinanair))

Credits:

- AI4Bharat for IndicTrans2 1.1B (prajdabre/rotary-indictrans2-en-indic-1B) — the actual translation model used, run locally at build time
- AI4Bharat for Indic Parler-TTS — the available-but-not-yet-used Santali audio path
- Bhashini (MeitY, Government of India) for live translation and speech; its IIT Madras voice (`Bhashini/IITM/TTS`) is what speaks Santali in the app
- Google Noto fonts for Ol Chiki, Devanagari, Bengali, Odia, Gujarati, Gurmukhi, Tamil, Telugu, Kannada, and Malayalam typefaces

# SIH26042 — Traceability Matrix

**Project:** TribalFLN, AI-assisted mother-tongue instruction suite
**Problem statement:** SIH26042, Government of Jharkhand, Department of Higher & Technical Education
**Category / theme:** Software, Smart Education
**Last verified:** 25 August 2026

Every file path below is checked by `tools/verify_traceability.py`, which fails
if a citation does not resolve. Bundled assets are checked by
`tools/verify_assets.py`, which fails if a model or font is a placeholder.
Run both before any submission.

> **Status: none of the six graded items is fully met.** Two are substantially
> built and two are partial. The gaps are named below rather than papered over.
> An earlier version of this document certified 100% compliance while citing
> fifteen classes that no longer existed. None of those citations could be
> checked and two of the claims were not true. This version states what the
> code does.

---

## The Expected Solution, item by item

The official text names four deliverables plus two submission artefacts. Those
six are what the project is graded on.

| # | Required | Status | Evidence |
|---|----------|--------|----------|
| 1 | Hindi to tribal language, minimum one | **Partial** | Text is produced for all three languages by `app/src/main/java/in/gov/tribalfln/engine/TribalPhonemeMatcher.kt`, but that class performs **script conversion, not translation**. See Known gaps. Corpus of 810 records in `app/src/main/assets/database/nipun_curriculum_prepopulated.json`, every one flagged `machine_translated: true`. |
| 2 | Voice to voice, under 3 seconds | **Not met** | The state machine and latency budget are real: `app/src/main/java/in/gov/tribalfln/engine/RealTimeClassroomDialogueEngine.kt` sets `MAX_LATENCY_MS = 1200`. Audio capture and noise handling exist in `app/src/main/java/in/gov/tribalfln/ClassroomAudioRecorder.kt` and `app/src/main/java/in/gov/tribalfln/ClassroomNoiseFilter.kt`. But **there is no speech recognition**: `app/src/main/java/in/gov/tribalfln/VoiceEngineManager.kt` is entirely log statements, and no `SpeechRecognizer` exists anywhere. See Known gaps. |
| 3 | Bilingual worksheets, NIPUN aligned | **Not met** | `app/src/main/java/in/gov/tribalfln/engine/materials/BilingualMaterialSynthesizer.kt` exists but emits a PDF with no `/Contents`, no `/Resources` and no font, so the page renders blank. It encodes `US_ASCII`, which cannot carry Devanagari or Ol Chiki. No NIPUN outcome code is written by any code path. |
| 4 | Fully offline, Android 9, 2 GB RAM | **Partial** | The offline *plumbing* is real: `app/build.gradle.kts` sets `minSdk = 28`, network use is blocked by `app/src/main/java/in/gov/tribalfln/util/NetworkGuard.kt`, and the memory ceiling is enforced by `app/src/main/java/in/gov/tribalfln/util/LowSpecMemoryGovernor.kt`. But **the three bundled ONNX models are 72-byte placeholders**, so no neural model actually runs. See Known gaps. |
| 5 | Demo video | **Pending** | Script prepared in `SIH_DEMO_VIDEO_SCRIPT.md`. Not yet recorded. |
| 6 | Public GitHub repository | **Pending** | Repository exists. **Confirm it is public before submitting**, because a private repository counts as none. |

---

## Supporting capability

Described in the problem statement, not separately graded.

| Requirement fragment | Evidence |
|---|---|
| "lesson scripts, activity instructions, assessment prompts" | `app/src/main/java/in/gov/tribalfln/data/NipunCurriculumDatabase.kt`. The corpus splits evenly at 270 records of each content type. |
| "aligned to the NIPUN Bharat learning outcomes framework" | 18 distinct outcome codes such as `L1-FL-OL-01` and `L1-FN-NS-01`, carried on every record in `app/src/main/assets/database/nipun_curriculum_prepopulated.json`. **Present in the data and not yet read by any code.** |
| "synthesised audio in target tribal languages" | `app/src/main/java/in/gov/tribalfln/engine/OfflineTribalSpeechSynthesizer.kt` |
| Ho, Mundari and Santhali | `app/src/main/java/in/gov/tribalfln/engine/materials/TribalLanguageProvider.kt`. Runtime switching in `app/src/main/java/in/gov/tribalfln/NipunEducatorDashboardActivity.kt` via the codes `san`, `hoc` and `mfq`. |
| Ol Chiki rendering | Font bundled at `app/src/main/assets/fonts/NotoSansOlChiki-Regular.ttf`. Note that `app/src/main/java/in/gov/tribalfln/OlChikiGlyphRenderer.kt` has an empty `renderGlyph` body and draws nothing. |
| "after initial content synchronisation" | Wi-Fi Direct mesh in `app/src/main/java/in/gov/tribalfln/mesh/ClassroomMeshSync.kt`, QR fallback in `app/src/main/java/in/gov/tribalfln/QrSyncFallback.kt` |
| Offline OCR | `app/src/main/java/in/gov/tribalfln/OfflineOcrScanner.kt` |
| Semantic curriculum search | `app/src/main/java/in/gov/tribalfln/engine/SemanticCurriculumSearchEngine.kt` and `app/src/main/java/in/gov/tribalfln/data/LocalVectorDatabase.kt` |

---

## Known gaps

### 1. The bundled AI models and the Ol Chiki font are placeholders

`app/src/main/assets/silero_vad.onnx`, `app/src/main/assets/all-MiniLM-L6-v2_int8.onnx`
and `app/src/main/assets/ocr_mobilenet_int8.onnx` are each **72 bytes**: the
ASCII string `ONNXV001` followed by zeros. That is not the ONNX format, which is
protobuf and begins with a `0x08` varint. The real files would be roughly 1.8 MB,
23 MB and 4 MB.

`app/src/main/assets/fonts/NotoSansOlChiki-Regular.ttf` is **100 bytes**. It
carries a correct TrueType signature and then a zero table directory, so it
contains no glyphs. Android does not ship an Ol Chiki font, so Ol Chiki text
currently renders as empty boxes.

This went unnoticed because
`app/src/main/java/in/gov/tribalfln/engine/RealTimeClassroomDialogueEngine.kt`
catches every load failure and switches to a heuristic fallback, with the comment
"we silently flip to fallback mode so the demo never crashes". The app therefore
runs, and appears to work, with no neural model behind it. Voice activity
detection falls back to a sum-of-squares energy threshold.

One asset is genuine: `app/src/main/assets/database/nipun_vector_embeddings.bin`
is 1,244,176 bytes, which is an `NFLN` header plus 810 x 384 float32 vectors, and
vector 0 has an L2 norm of exactly 1.0. The document embeddings are real. Only
the model that would embed a *query* at runtime is missing.

**Fix:** download the real Silero VAD, MiniLM and MobileNet models and the real
Noto Sans Ol Chiki, then run `tools/verify_assets.py` until it passes. Until
then, do not claim on-device inference.

### 2. There is no speech recognition, and unknown input returns a canned phrase

`app/src/main/java/in/gov/tribalfln/VoiceEngineManager.kt` declares
`initializeSpeechRecognizer()`, `startListening()`, `stopListening()` and
`release()`, and every one of them only writes a log line. No `SpeechRecognizer`
or `RecognizerIntent` appears anywhere in the codebase, so nothing converts
speech to text.

What the dialogue engine does instead is look the input up in a small hardcoded
phrase map, then fall back to a **generic canned response** for any input it does
not recognise. So an unrecognised Hindi sentence produces confident-looking Ol
Chiki that has nothing to do with what was said. That is the same failure mode as
gap 3 and it is the more dangerous one, because it is indistinguishable from
success on stage.

**Fix:** wire Android's `SpeechRecognizer` for Hindi, which is on-device from
Android 12 and available via Google services below that, and replace the generic
fallback with an explicit "not recognised" state rather than invented output.

### 3. The tribal-language output is script conversion, not translation

Hindi is Indo-Aryan. Santhali, Ho and Mundari are Austroasiatic. They share
neither vocabulary nor grammar, so the character and phoneme mapping in
`app/src/main/java/in/gov/tribalfln/engine/TribalPhonemeMatcher.kt` cannot
produce meaning.

Verified by romanising the shipped corpus. The Hindi `पाठ योजना` becomes Ol
Chiki that reads `paṭho yojona`, which is the Hindi word respelled rather than
the Santhali word. The Hindi-to-tribal character ratio is 1.11 across all three
languages, which indicates near one-to-one substitution of the same source.

The word dictionaries inside that class are separately incorrect. Every
`HINDI_TO_SANTHALI` value begins with `U+1C50`, which is OL CHIKI DIGIT ZERO
rather than a letter, so every word it emits starts with a numeral. Four
distinct Hindi words, `किताब`, `शिक्षक`, `पढ़ना` and `देखना`, map to one identical
string, and since the reverse map is built by inverting it, three of them can
never be recovered. In `HINDI_TO_HO`, `एक` and `माँ` are byte-identical.

**Mitigation in place.** `TribalPhonemeMatcher.Provenance` labels every result,
and `app/src/main/java/in/gov/tribalfln/ui/ClassroomDialogueFragment.kt` shows
"Script conversion, not a translation" beside the output. The application no
longer presents this as translation to the user.

**Planned fix.** Replace runtime generation with a verified content pack:
translate a bounded set of classroom and lesson sentences correctly once through
Bhashini, which has real Santhali models, pre-render the audio, and ship both
inside the APK. The problem statement's own phrase "after initial content
synchronisation" permits exactly this.

### 4. The worksheet generator produces a blank page

See graded item 3 above.

### 5. `android/` is dead scaffold

`settings.gradle` includes only `:app`. The `android/` directory is an unrelated
earlier scaffold under `com.example.*` and should be deleted so that nobody
reviews the wrong tree.

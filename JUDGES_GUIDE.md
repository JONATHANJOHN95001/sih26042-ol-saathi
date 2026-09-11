# Ol Saathi · Judges' guide

**SIH26042 · Government of Jharkhand · Team INNOV8**

Ol Saathi is a native Android app (Kotlin) that lets a Hindi-speaking primary
teacher teach in a child's mother tongue, primarily **Santali in Ol Chiki
script**, plus 16 more Indian languages. The teacher speaks Hindi; the app shows
and speaks the target language, and prints NIPUN-aligned worksheets and
flashcards. Lessons work **fully offline**; with a network, Bhashini extends it
to any sentence.

Start here, then open the files linked below. Every path is relative to this
folder and clickable in VS Code.

---

## 1. The four graded requirements, and where each one lives

| # | Requirement | Where to look |
|---|---|---|
| 1 | Hindi → tribal language, **text and audio** | Pack: [app/src/main/assets/pack/pack.sat.json](app/src/main/assets/pack/pack.sat.json) (53 Santali entries). Audio: [app/src/main/assets/pack/audio/sat/](app/src/main/assets/pack/audio/sat/) (53 clips). Loader: [VerifiedContentPack.kt](app/src/main/java/app/olsaathi/content/VerifiedContentPack.kt) |
| 2 | Voice to voice, **under 3 s** | Screen: [ClassroomActivity.kt](app/src/main/java/app/olsaathi/ui/ClassroomActivity.kt). Speech in: [BhashiniSpeechInput.kt](app/src/main/java/app/olsaathi/speech/BhashiniSpeechInput.kt), [HindiSpeechInput.kt](app/src/main/java/app/olsaathi/speech/HindiSpeechInput.kt). Routing: [TranslationRouter.kt](app/src/main/java/app/olsaathi/content/TranslationRouter.kt). Timing: [LatencyLog.kt](app/src/main/java/app/olsaathi/util/LatencyLog.kt) |
| 3 | Bilingual worksheets, **NIPUN Bharat aligned** | [WorksheetPdf.kt](app/src/main/java/app/olsaathi/worksheet/WorksheetPdf.kt), [FlashcardPdf.kt](app/src/main/java/app/olsaathi/worksheet/FlashcardPdf.kt), [WorksheetType.kt](app/src/main/java/app/olsaathi/worksheet/WorksheetType.kt) (outcome codes), [WorksheetActivity.kt](app/src/main/java/app/olsaathi/ui/WorksheetActivity.kt). Any teacher PDF: [ImportLessonActivity.kt](app/src/main/java/app/olsaathi/ui/ImportLessonActivity.kt), [LessonPdfImporter.kt](app/src/main/java/app/olsaathi/pdf/LessonPdfImporter.kt) |
| 4 | **Fully offline**, Android 9+, 2 GB RAM | `minSdk = 28` in [app/build.gradle.kts](app/build.gradle.kts). Network guard: [NetworkGuard.kt](app/src/main/java/app/olsaathi/util/NetworkGuard.kt). On-device proof screen: [CheckAndProofActivity.kt](app/src/main/java/app/olsaathi/ui/CheckAndProofActivity.kt) |

Full requirement-by-requirement evidence, including what is **not** done:
[PROBLEM_STATEMENT_TRACEABILITY_MATRIX.md](PROBLEM_STATEMENT_TRACEABILITY_MATRIX.md).

---

## 2. The pipelines

### 2a. Build time: how the offline content is made

Runs on a laptop, once. The output ships inside the APK, so the classroom needs
no network.

```
Hindi lesson text
   → IndicTrans2 translation            tools/build_pack_indictrans.py
   → per-script contamination guard     tools/langs.py
   → NIPUN Bharat outcome codes         tools/add_nipun_codes.py
   → Ol Chiki → Devanagari bridge       tools/olchiki_bridge.py
   → Santali speech (Bhashini IIT Madras)   tools/build_pack_audio_iitm.py
   → validated, encoded to OGG          tools/apply_audio_ogg.py
   → packs.json audio counts            tools/pack_manifest.py
   → asset + traceability checks        tools/verify_assets.py, tools/verify_traceability.py
```

- [tools/build_pack_indictrans.py](tools/build_pack_indictrans.py): translates the lesson text into every pack language.
- [tools/langs.py](tools/langs.py): Unicode script rules. A line containing letters from the wrong script is retried or dropped, never shipped.
- [tools/add_nipun_codes.py](tools/add_nipun_codes.py): gives every entry a published NIPUN Bharat outcome.
- [tools/olchiki_bridge.py](tools/olchiki_bridge.py): Ol Chiki → Devanagari, derived from the Unicode letter names. Bhashini's only Santali voice needs Devanagari input.
- [tools/build_pack_audio_iitm.py](tools/build_pack_audio_iitm.py) → [tools/apply_audio_ogg.py](tools/apply_audio_ogg.py): speech for each line, rejected if silent or noise-like, then packed as OGG.
- [tools/backtranslate_qa.py](tools/backtranslate_qa.py): ranks translations by how likely they are to be wrong; report in [verification/](verification/).

### 2b. Run time: the live voice-to-voice pipeline

Used only when a line is not in the offline pack and a network is available.

```
Teacher holds the button and speaks Hindi
   → Hindi speech recognition   Bhashini ai4bharat/conformer-hi      BhashiniSpeechInput.kt
   → Hindi → target translation Bhashini ai4bharat/indictrans-v2     BhashiniClient.translate
   → (Santali) Ol Chiki → Devanagari                                 OlChikiBridge.kt
   → speech                     Bhashini IIT Madras / AI4Bharat TTS  BhashiniClient.synthesise
   → plays, labelled "Machine voice, live · Bhashini"               PackAudioPlayer.kt
```

- Client: [BhashiniClient.kt](app/src/main/java/app/olsaathi/net/BhashiniClient.kt)
- Santali bridge in the app: [OlChikiBridge.kt](app/src/main/java/app/olsaathi/text/OlChikiBridge.kt), pinned to the Python bridge by [OlChikiBridgeTest.kt](app/src/test/java/app/olsaathi/text/OlChikiBridgeTest.kt)

### 2c. The same pipeline as standalone scripts

[pipelines/bhashini-sdk/](pipelines/bhashini-sdk/) runs speech → translation →
speech on the official `bhashini-client-sdk`, outside the app:

- [pipeline.py](pipelines/bhashini-sdk/pipeline.py): `--wav speech.wav --to sat` or `--text "..." --to ta`
- [all_langs.py](pipelines/bhashini-sdk/all_langs.py): Hindi → all 17 languages, text and speech
- [tts_compare.py](pipelines/bhashini-sdk/tts_compare.py): voice-model latency comparison

[pipelines/postman/](pipelines/postman/) holds Bhashini's official Postman
collection. [bhashini/](bhashini/) holds the Node probes used to establish the
API shape, including which script the Santali voice needs.

---

## 3. A tour of the app code

```
app/src/main/java/app/olsaathi/
  ui/         screens: Teach, Lessons, Materials, Show the Class, Import, Check & Proof
  content/    offline pack, translation routing, provenance labels
  net/        Bhashini client (translate, speak, recognise)
  speech/     Hindi speech input, device voice
  audio/      playback, WAV conversion
  worksheet/  NIPUN worksheets and flashcards (PDF)
  pdf/        teacher PDF import
  text/       Ol Chiki → Devanagari bridge
  util/       network guard, latency log
```

Tests: [app/src/test/](app/src/test/) (90 unit tests, run against the real shipped packs).

**Provenance is a rule, not a label.** Every line on screen and on paper says
where it came from: offline pack, live Bhashini, or not available. See
[Translation.kt](app/src/main/java/app/olsaathi/content/Translation.kt).

---

## 4. Build and run

Needs JDK 17 and the Android SDK.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

The APK is written to `app/build/outputs/apk/debug/`. For live Bhashini
features, copy [local.properties.example](local.properties.example) to
`local.properties` and add the key; without it the app runs offline-only.

Pipeline scripts:

```bash
cd pipelines/bhashini-sdk && python -m pip install -r requirements.txt && python smoke_test.py
```

---

## 5. More reading

- [README.md](README.md): project overview and architecture
- [PROBLEM_STATEMENT_TRACEABILITY_MATRIX.md](PROBLEM_STATEMENT_TRACEABILITY_MATRIX.md): evidence per requirement, known gaps
- [DEMO.md](DEMO.md): demo walkthrough
- [docs/](docs/): pitch, live demo script, judge Q&A
- [verification/](verification/): back-translation report, Santali review sheet and recording studio

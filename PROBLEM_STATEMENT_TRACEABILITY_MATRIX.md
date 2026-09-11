# SIH26042 — Traceability Matrix

**Project:** Ol Saathi, AI-assisted mother-tongue instruction for Jharkhand primary schools
**Problem statement:** SIH26042, Government of Jharkhand, Department of Higher & Technical Education
**Category / theme:** Software, Smart Education
**Last verified:** 6 September 2026, on an Android 9 device with 2,046 MB RAM

Every file path below is checked by `tools/verify_traceability.py`, which fails
if a citation does not resolve. Bundled assets are checked by
`tools/verify_assets.py`. Run both before any submission.

> **This document was rewritten on 26 August 2026.** The previous version
> described the `in.gov.tribalfln` codebase, which no longer exists. Every class
> it cited had been deleted, so a reviewer checking any citation would have
> found nothing. Before that, an even earlier version certified "100%
> COMPLIANT" while citing fifteen classes that had already been renamed. The
> pattern is worth naming: a compliance document is the easiest thing in a
> project to leave behind, and the most expensive thing to be caught on.

---

## The Expected Solution, item by item

| # | Required | Status | Evidence |
|---|----------|--------|----------|
| 1 | Hindi to tribal language, minimum one | **Met, grown to 17 languages across 10 scripts** | Santali (Ol Chiki, primary target, 53 entries) plus Assamese (52, Bengali script), Bengali (52, Bengali script), Bodo (53, Devanagari), Dogri (50, Devanagari), Konkani (52, Devanagari), Gujarati (52, Gujarati script), Kannada (51, Kannada script), Maithili (52, Devanagari), Malayalam (51, Malayalam script), Marathi (52, Devanagari), Manipuri (53, Bengali script), Nepali (53, Devanagari), Odia (52, Odia script), Punjabi (52, Gurmukhi script), Tamil (52, Tamil script), Telugu (52, Telugu script). Full list in `app/src/main/assets/pack/packs.json`. Every entry produced by AI4Bharat IndicTrans2 1.1B (`prajdabre/rotary-indictrans2-en-indic-1B`), run locally at build time by `tools/build_pack_indictrans.py`. Bhashini access arrived on 10 Sep 2026 and is used for two things: live translation of sentences outside the pack, and build-time speech for Tamil, Malayalam, Telugu, Bengali and Bodo (260 OGG clips). **Santali now speaks live; offline Santali audio is the open gap.** Bhashini does have a Santali voice, `Bhashini/IITM/TTS` on the IIT Madras pipeline (`660fa5bec7fb5b0328229016`); the MeitY pipeline's "No supported tasks" answer was only about that pipeline. It returns silence for Ol Chiki and speech for Devanagari, so `app/src/main/java/app/olsaathi/text/OlChikiBridge.kt` converts first (pinned to all 53 pack `bridge` values by `OlChikiBridgeTest`). With a network, every Santali line plays, labelled "Machine voice, live · Bhashini"; verified on an API 35 emulator on 10 Sep 2026. The same voice produced the pack's offline Santali audio: **all 53 entries** ship as OGG (`tools/build_pack_audio_iitm.py`, then `tools/apply_audio_ogg.py --lang sat`), verified playing in airplane mode with zero network calls on 10 Sep 2026. p24 and p40 failed the automatic noise check narrowly (57.8% and 59.6% of energy in the speech band against a 60% line) and were accepted after a team member listened (`--accept-by-ear`); each entry's `audioGate` field records that. Bodo is from Assam, so its pack audio shows the pipeline works but does not answer this requirement. Loaded by `app/src/main/java/app/olsaathi/content/VerifiedContentPack.kt`. **No line has been checked by a native speaker of any target language**, and every line says so on screen. |
| 2 | Voice to voice, under three seconds | **Met offline, measured on an emulator; live path not yet timed** | Measured 10 Sep 2026 on an Android 9 emulator with wifi and data off: seven Tamil phrases spoken through the debug microphone played their shipped audio at 93 to 138 ms, **median 98 ms**, zero playback errors, zero network calls. The clock starts when speech recognition returns, so recognition time is not included. The live path reached Bhashini once but failed on DNS, because the emulator had no internet, so no live figure exists yet. The paragraph below predates the pack audio and is kept for how routing works. Hindi speech in works: `app/src/main/java/app/olsaathi/speech/HindiSpeechInput.kt` uses `SpeechRecognizer` with `EXTRA_PREFER_OFFLINE`. Three audio sources are routed in order of how much each has been vouched for, in `app/src/main/java/app/olsaathi/ui/ClassroomActivity.kt`: a pack clip (Santali: all 53 since 10 Sep 2026); a clip synthesised over the network by `app/src/main/java/app/olsaathi/net/BhashiniClient.kt` (key in hand since 10 Sep 2026; this is the only path that speaks Santali); and the device's own voice via `app/src/main/java/app/olsaathi/speech/TargetVoice.kt`, which needs neither but counts only a voice that is installed on the device and needs no network. That makes this demonstrable on a device whose voice data for the language has been downloaded. On a stock Google TTS install the Tamil voice is a network voice: it spoke once on the GMS emulator while it had internet and timed out without it, so the app declines to offer it offline rather than show a play button that silently fails. **No voice-to-voice figure has been measured yet**, and the Check and Proof screen says so in words rather than borrowing the 1 ms lookup number. The two paths are reported as separate medians so a network round trip is never averaged with local playback. |
| 3 | Bilingual worksheets and flashcards, NIPUN aligned | **Met, plus teacher PDF lesson import** | Worksheets: `app/src/main/java/app/olsaathi/worksheet/WorksheetPdf.kt`. Flashcards: `app/src/main/java/app/olsaathi/worksheet/FlashcardPdf.kt`, 6 to an A4 page with cut guides. Every flashcard carries a picture: 53 of 53 entries have a drawable in `app/src/main/res/drawable/ic_card_*.xml`, fetched at build time from Iconify's public API by `tools/fetch_flashcard_icons.py`. Fluent Emoji Flat, MIT licensed. Both verified on device: 7-page flashcard PDF, 104 Ol Chiki and 60 Devanagari glyphs on page 1, both fonts embedded. PDF lesson import: `app/src/main/java/app/olsaathi/pdf/LessonPdfImporter.kt` + `app/src/main/java/app/olsaathi/ui/ImportLessonActivity.kt` — extracts Hindi text from a teacher's PDF via PdfBox-Android, matches sentences against the shipped pack, reports coverage percentage and which lines matched, generates a worksheet from the matched subset. It does not translate new text on device; see that section below. |
| 4 | Fully offline, Android 9, 2 GB RAM | **Met** | `minSdk = 28` in `app/build.gradle.kts`. Network blocked by `app/src/main/java/app/olsaathi/util/NetworkGuard.kt`. Measured on a 2,046 MB Android 9 device: **43 to 45 MB peak PSS, 656 ms cold start, zero network calls, 3,000 monkey events with no crash.** Release APK **11.2 MB** (up from 4.23 MB: 1.4 MB fonts for 10 scripts, 436 KB language packs for 17 languages, PdfBox-Android dependency for PDF lesson import). |
| 5 | Demo video | **Not started** | Script in `SIH_DEMO_VIDEO_SCRIPT.md`. Nothing recorded. |
| 6 | Public GitHub repository | **Met** | Pushed to `https://github.com/JONATHANJOHN95001/sih26042-ol-saathi.git`. No key, keystore or `local.properties` is in the tree or in any commit; all three are gitignored. |

---

## Supporting capability

| Requirement fragment | Evidence |
|---|---|
| "NLP engine capable of translating Hindi FLN curriculum content" | AI4Bharat IndicTrans2 1.1B, 1,100 M parameters, `eng_Latn` to per-language tag (e.g. `sat_Olck`), run at build time by `tools/build_pack_indictrans.py`. Same model family Bhashini serves for these pairs (`ai4bharat/indictrans-v2-all-gpu--t4`), which the app calls live for sentences outside the pack. The output is guarded per-script (see below): anything outside the target Unicode block is retried or dropped. **17 entries were withheld across 17 languages** rather than shipped contaminated. |
| "aligned to the NIPUN Bharat learning outcomes framework" | Every entry carries a goal and domain written by `tools/add_nipun_codes.py`: 3 Developmental Goals (HW, EC, IL) across **7 domains**. Read at runtime and shown on the Check & Proof screen and on every flashcard. |
| "lesson scripts, activity instructions, assessment prompts" | 40 teaching phrases, 10 lesson sentences and 3 comprehension checks per language, in 17 pack files under `app/src/main/assets/pack/pack.*.json`. |
| "synthesised audio in target tribal languages" | **Met, offline for all 53 Santali lines.** Pack clips from Bhashini's IIT Madras voice, played with no network. Machine speech, not checked by a Santali speaker; two clips passed by ear rather than by the automatic noise check. Playback covered by `app/src/test/java/app/olsaathi/content/PackAudioTest.kt`. See gap 1. |
| Ol Chiki rendering plus 9 more scripts | 10 fonts bundled in `app/src/main/assets/fonts/`: NotoSansOlChiki-Regular.ttf (Ol Chiki), NotoSansDevanagari-Regular.ttf (Devanagari ×6 languages), NotoSansBengali-Regular.ttf (Bengali ×3), NotoSansOriya-Regular.ttf (Odia), NotoSansGujarati-Regular.ttf (Gujarati), NotoSansGurmukhi-Regular.ttf (Gurmukhi/Punjabi), NotoSansTamil-Regular.ttf (Tamil), NotoSansTelugu-Regular.ttf (Telugu), NotoSansKannada-Regular.ttf (Kannada), NotoSansMalayalam-Regular.ttf (Malayalam). All verified rendering on device. |
| Child's experience | `app/src/main/java/app/olsaathi/ui/ShowClassActivity.kt` presents the picture, the target language at up to 60sp, and the Hindi underneath. No controls, no provenance, no counters. The teacher turns the tablet to face the class. Reachable from both the lesson player and the Teach screen. |
| "after initial content synchronisation" | Satisfied by shipping the pack inside the APK, so there is no first-run download at all. Teacher lesson PDF import (`app/src/main/java/app/olsaathi/pdf/LessonPdfImporter.kt`) reports uncovered lines honestly rather than translating; those uncovered lines are the sync input for the next build. |
| Translation quality evidence | `tools/backtranslate_qa.py` round-trips every entry through IndicTrans2 indic-en 1B. Median similarity **0.484**, report in `verification/back-translation-report.json`. No line has been checked by a native speaker of any target language. |
| Per-script contamination guard | Implemented in `tools/langs.py` (Unicode block ranges, per-script allowed chars, echo detection) and enforced in `tools/build_pack_indictrans.py:defects()`. Caught three real defect classes: Arabic letters mid-sentence in Santali (200M distilled model), Bengali returned entirely in Devanagari (all 53 entries — fixed by adding the official IndicTrans2 transliteration step), and a stray Latin token appended in 13 of 17 languages on one phrase. 17 entries withheld total across 17 languages. Each pack records `guard: strong` (script distinct from Devanagari, untranslated Hindi detected instantly) or `guard: weak-shared-script` (Hindi and target share Devanagari, only verbatim echo detected) and the app shows this. |
| PDF lesson import, honest failure mode | `app/src/main/java/app/olsaathi/pdf/LessonPdfImporter.kt` uses PdfBox-Android to extract text via `PDFTextStripper`, segments Hindi sentences (danda, full stop, question mark as boundaries, layout line-breaks collapsed because PDF line breaks are not sentence breaks), matches each line against the pack via `VerifiedContentPack.lookup()`, returns per-line coverage. Unmatched lines return `Not in offline pack` in grey italic, not a guessed translation. The model will not fit in a 2 GB RAM budget (IndicTrans2 1.1B ≈ 4.4 GB float32, ≈ 200 MB even int8 quantised on a 43 MB peak process), so on-device translation is refused rather than faked. |

---

## Known gaps

### 1. Santali audio is machine speech that no Santali speaker has heard

**Update, 10 Sep 2026.** Bhashini access arrived, and the institutionally backed
voice this section was waiting for exists: `Bhashini/IITM/TTS`, given Devanagari
input. The app uses it live, and it produced the pack audio: all 53 lines
now speak offline. The IIT Madras voice is deterministic (retakes are
byte-identical), so p24 and p40, which the noise check rejects, could not be
fixed by retrying; a team member listened and judged both to be clear speech,
and they were accepted with that recorded. What still holds from below: no
Santali speaker has heard any of it. The reasoning
below is kept as written; its premise that the IIT Madras voice was out of
reach no longer holds.

The statement asks for synthesised audio in the tribal language. This is the one
requirement that no amount of code closes, and the reason is worth stating
precisely rather than as "not done yet". The earlier version of this document
said "it cannot be generated"; that claim is false and is corrected here.

What is true:

- **Meta's MMS covers 1,143 languages and Santali is not one of them.** Checked
  directly against the model index on 26 August 2026. Ho, Mundari and Kurukh are
  present, Santali is not.
- **eSpeak NG has no Santali voice.**
- **Android ships no Santali TTS voice**, confirmed at runtime by
  `isLanguageAvailable(Locale("sat"))` on the Check & Proof screen.

What the earlier version missed:

- **ai4bharat/indic-parler-tts lists Santali among its official 21 languages**,
  is Apache 2.0, and its Hugging Face gate is auto-approve rather than a review
  queue. The generator already exists at `tools/build_pack_audio_parler.py` and
  the provenance label is already wired through `tools/apply_audio.py
  --provenance parler` and the `PARLER_TTS` entry in [Translation.kt](app/src/main/java/app/olsaathi/content/Translation.kt#L21-L41).

**Why nothing has been run.** Parler is a 0.9B multilingual research model. It
has no published evaluation for Santali, and no Santali speaker has validated
any output. Bhashini's IITM model is the only Santali synthesis with
institutional backing behind it. Access was requested on 1 September and the
deliverable is being held open rather than filled with a voice nobody can vouch
for. A fluent-sounding unchecked voice reading an unverified line is the most
convincing way to break the provenance rule this app is built on.

Using a related Munda language would be the tempting shortcut, and it is
refused. Ho and Santali are distinct languages, and a Ho voice reading Santali
text produces something no Santali child would recognise. That is the same
failure as the transliteration this project already removed once.

**What is built instead.** Everything downstream of the audio file:

- `tools/make_recording_studio.py` generates `verification/santali-recording-studio.html`,
  a single offline page that shows each line in Hindi and Ol Chiki, records from
  the microphone, and exports every clip as WAV in a zip. Fonts are embedded so
  Ol Chiki renders on a device that has never seen the script.
- `tools/apply_audio.py` validates every clip, rejects empty or truncated ones,
  and writes them into the APK and the pack. Accepts `native`, `bhashini`, or
  `parler` provenance; each label goes straight to the screen.
- Playback and its provenance labelling are covered by 9 unit tests.

So the remaining work is **one hour of one Santali speaker**, and it is the same
hour the translation review already needs. Either source, a recording or
Bhashini, drops in with no code change. If a synthesis run is needed before a
speaker is found, `tools/build_pack_audio_parler.py` will do it and every clip
will ship labelled "unchecked".

### 2. No line has been checked by a native speaker

All 884 entries across 17 languages read "Machine translation". Back-translation ranks
suspicion, not correctness: 27 of 53 Santali entries score below 0.50. The review sheet at
`verification/santali-review-sheet.html` is sorted worst first, and
`tools/apply_review.py` writes verdicts back into the pack. It refuses
anonymous reviews, because a teacher cannot check a claim with no name on it.

### 3. Santali is the named target; Mundari and Ho are not shipped

The statement names Santhali, Mundari and Ho, and asks for a minimum of one.
Santali is done properly and the pipeline has been generalised to 16 additional
languages across 9 scripts that IndicTrans2 covers well. IndicTrans2's 22
scheduled languages include Santali and exclude Mundari and Ho, so those two
cannot be produced by the same route and are not claimed.

### 4. The voice round trip has never been measured with a human voice

Lookup is 1 ms. The recogniser leg needs a device with the Hindi offline speech
pack installed, which the emulator does not have. Until that is measured on
hardware, the three-second claim is a budget rather than a result.

---

## How to check any of this yourself

```bash
python tools/verify_traceability.py     # every citation above resolves
python tools/verify_assets.py           # no placeholder fonts or models
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:testDebugUnitTest
```

74 unit tests, 0 failures, re-run 10 September 2026. On a device, open the app,
overflow menu, **Check & Proof**, then **Run Checks**. That screen reports the
pack, both scripts, audio coverage, offline state, latency and cold start from
the running build rather than from this document.

# SIH26042 — Traceability Matrix

**Project:** Ol Saathi, AI-assisted mother-tongue instruction for Jharkhand primary schools
**Problem statement:** SIH26042, Government of Jharkhand, Department of Higher & Technical Education
**Category / theme:** Software, Smart Education
**Last verified:** 26 August 2026, on an Android 9 device with 2,046 MB RAM

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
| 1 | Hindi to tribal language, minimum one | **Met** | 53 entries, Hindi source to Santali in Ol Chiki, in `app/src/main/assets/pack/pack.sat.json`. Produced by AI4Bharat IndicTrans2 1B. Loaded by `app/src/main/java/app/olsaathi/content/VerifiedContentPack.kt`. **Not yet checked by a Santali speaker**, and every line says so on screen. |
| 2 | Voice to voice, under three seconds | **Partial** | Hindi speech in works: `app/src/main/java/app/olsaathi/speech/HindiSpeechInput.kt` uses `SpeechRecognizer` with `EXTRA_PREFER_OFFLINE`. Lookup is **1 ms**, measured. Playback is built and tested: `app/src/main/java/app/olsaathi/audio/PackAudioPlayer.kt`. **The Santali audio itself does not exist yet.** See gap 1. |
| 3 | Bilingual worksheets and flashcards, NIPUN aligned | **Met** | Worksheets: `app/src/main/java/app/olsaathi/worksheet/WorksheetPdf.kt`. Flashcards: `app/src/main/java/app/olsaathi/worksheet/FlashcardPdf.kt`, 6 to an A4 page with cut guides. Every flashcard carries a picture: 53 of 53 entries have a drawable in `app/src/main/res/drawable/ic_card_*.xml`, fetched at build time from Iconify's public API by `tools/fetch_flashcard_icons.py`. Fluent Emoji Flat, MIT licensed. Both verified on device: 7-page flashcard PDF, 104 Ol Chiki and 60 Devanagari glyphs on page 1, both fonts embedded. |
| 4 | Fully offline, Android 9, 2 GB RAM | **Met** | `minSdk = 28` in `app/build.gradle.kts`. Network blocked by `app/src/main/java/app/olsaathi/util/NetworkGuard.kt`. Measured on a 2,046 MB Android 9 device: **43 to 45 MB peak PSS, 656 ms cold start, zero network calls, 3,000 monkey events with no crash.** Release APK **4.23 MB**. |
| 5 | Demo video | **Not started** | Script in `SIH_DEMO_VIDEO_SCRIPT.md`. Nothing recorded. |
| 6 | Public GitHub repository | **Not pushed** | Committed locally, no remote configured. A graded item at zero, and it is two commands. |

---

## Supporting capability

| Requirement fragment | Evidence |
|---|---|
| "NLP engine capable of translating Hindi FLN curriculum content" | AI4Bharat IndicTrans2 1B, 1,116 M parameters, `eng_Latn` to `sat_Olck`, run at build time by `tools/build_pack_indictrans.py`. The same model family Bhashini serves for this pair. |
| "aligned to the NIPUN Bharat learning outcomes framework" | Every entry carries a goal and domain written by `tools/add_nipun_codes.py`: 3 Developmental Goals (HW, EC, IL) across **7 domains**. Read at runtime and shown on the Check & Proof screen and on every flashcard. |
| "lesson scripts, activity instructions, assessment prompts" | 40 teaching phrases, 10 lesson sentences and 3 comprehension checks in `app/src/main/assets/pack/pack.sat.json`. |
| "synthesised audio in target tribal languages" | **Not met.** Path built end to end and covered by `app/src/test/java/app/olsaathi/content/PackAudioTest.kt`. See gap 1. |
| Ol Chiki rendering | `app/src/main/assets/fonts/NotoSansOlChiki-Regular.ttf`, bundled because Android ships no Ol Chiki font. Verified rendering on device. |
| Child's experience | `app/src/main/java/app/olsaathi/ui/ShowClassActivity.kt` presents the picture, the Santali at up to 60sp, and the Hindi underneath. No controls, no provenance, no counters. The teacher turns the tablet to face the class. Reachable from both the lesson player and the Teach screen. |
| "after initial content synchronisation" | Satisfied by shipping the pack inside the APK, so there is no first-run download at all. |
| Translation quality evidence | `tools/backtranslate_qa.py` round-trips every entry through IndicTrans2 indic-en 1B. Median similarity **0.484**, report in `verification/back-translation-report.json`. |

---

## Known gaps

### 1. There is no Santali audio, and it cannot be generated

The statement asks for synthesised audio in the tribal language. This is the one
requirement that no amount of code closes, and the reason is worth stating
precisely rather than as "not done yet".

- **Meta's MMS covers 1,143 languages and Santali is not one of them.** Checked
  directly against the model index on 26 August 2026. Ho, Mundari and Kurukh are
  present, Santali is not.
- **eSpeak NG has no Santali voice.**
- **Android ships no Santali TTS voice**, confirmed at runtime by
  `isLanguageAvailable(Locale("sat"))` on the Check & Proof screen.
- **Bhashini is the only service that offers Santali TTS**, and the account is
  still waiting on a faculty supervisor.

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
  and writes them into the APK and the pack.
- Playback and its provenance labelling are covered by 9 unit tests.

So the remaining work is **one hour of one Santali speaker**, and it is the same
hour the translation review already needs. Either source, a recording or
Bhashini, drops in with no code change.

### 2. No line has been checked by a Santali speaker

All 53 entries read "Machine translation · IndicTrans2". Back-translation ranks
suspicion, not correctness: 27 of 53 score below 0.50. The review sheet at
`verification/santali-review-sheet.html` is sorted worst first, and
`tools/apply_review.py` writes verdicts back into the pack. It refuses
anonymous reviews, because a teacher cannot check a claim with no name on it.

### 3. One language, not three

The statement names Santhali, Mundari and Ho, and asks for a minimum of one.
Santali is done properly. IndicTrans2 covers the 22 scheduled languages, which
includes Santali and excludes Mundari and Ho, so those two cannot be produced by
the same route and are not claimed.

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

33 unit tests, 0 failures as of 26 August 2026. On a device, open the app,
overflow menu, **Check & Proof**, then **Run Checks**. That screen reports the
pack, both scripts, audio coverage, offline state, latency and cold start from
the running build rather than from this document.

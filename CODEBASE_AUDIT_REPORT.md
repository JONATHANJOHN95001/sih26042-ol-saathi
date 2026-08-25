# TribalFLN — Codebase Audit & Problem Statement Gap Analysis

**Auditor:** Lead Systems Architect / SIH Hackathon Judge  
**Date:** August 24, 2026  
**Target:** `C:\Users\ASUS\sih-hackathon`  
**Problem Statement:** Offline Edge-AI Tribal Language FLN Teaching Assistant (NIPUN Bharat)

---

## 1. Overall Completion Score: **22 / 100%**

The project has a well-architected foundation with 4 production-ready Kotlin files and a solid Gradle configuration, but is critically missing **15 Kotlin source files**, **all binary assets (ONNX models, fonts, audio)**, **essential XML resources**, and contains **package declaration mismatches** that will cause compilation failure. The project will **not** compile in its current state.

---

## 2. `[CRITICAL]` Missing Assets or Code Blocks

These items will cause `gradlew assembleDebug` to fail or the app to crash on startup.

### 2.1 — Compilation-Blocking Issues

| # | Issue | File/Location | Impact |
|---|-------|--------------|--------|
| C-1 | **`LocalVectorDatabase` class does not exist** | `TribalFLNApplication.kt` imports `com.example.flnapp.data.LocalVectorDatabase` | `Unresolved reference` — app will not compile |
| C-2 | **`LearningGapRadarView` class does not exist** | `TeacherDashboardActivity.kt` + `activity_teacher_dashboard.xml` reference `com.example.flnapp.ui.LearningGapRadarView` | `Unresolved reference` — layout inflation crash |
| C-3 | **`TribalKeyboardService` class does not exist** | `AndroidManifest.xml` registers `.ime.TribalKeyboardService` | Manifest references nonexistent class |
| C-4 | **`@string/app_name` not defined** | `AndroidManifest.xml` | `Resource not found` — app label missing |
| C-5 | **`@string/ol_chiki_keyboard_label` not defined** | `AndroidManifest.xml` | IME service label missing |
| C-6 | **`@style/Theme.TribalFLN` not defined** | `AndroidManifest.xml` | `No theme found` — app crashes on launch |
| C-7 | **`@xml/file_paths` not present** in `app/src/main/res/xml/` | `AndroidManifest.xml` FileProvider | FileProvider configuration missing |
| C-8 | **`@xml/method` not present** in `app/src/main/res/xml/` | `AndroidManifest.xml` IME service | IME metadata missing |
| C-9 | **Package declaration mismatch** — `SecurityUtils.kt` declares `package com.example.flnapp` but resides in `data/` directory | `app/src/main/java/com/example/flnapp/data/SecurityUtils.kt` | Wrong package — imports will fail |
| C-10 | **Package declaration mismatch** — `StudentProgressDatabase.kt` declares `package com.example.flnapp` but resides in `data/` directory | `app/src/main/java/com/example/flnapp/data/StudentProgressDatabase.kt` | Wrong package — imports will fail |
| C-11 | **No `assets/` directory** in `app/src/main/` | `app/src/main/assets/` is empty | ONNX models, fonts, curriculum data missing |

### 2.2 — Missing Binary Assets (all referenced by code)

| Asset | Referenced By | Required For |
|-------|--------------|--------------|
| `fonts/NotoSansOlChiki-Regular.ttf` | `WorksheetPdfGenerator.kt`, `MainActivity.kt` | Ol Chiki glyph rendering on Canvas/PDF |
| `silero_vad.onnx` | `SileroVadEngine.kt` (not yet created) | Voice Activity Detection |
| `all-MiniLM-L6-v2_int8.onnx` | `OnDeviceVectorSearch.kt` (not yet created) | Semantic curriculum search |
| `ocr_mobilenet_int8.onnx` | `OfflineOcrScanner.kt` (not yet created) | Physical worksheet grading |
| `curriculum_data.json` | `VoiceEngineManager.kt` | 15 NIPUN FLN curriculum entries (exists in `android/` but not `app/`) |
| `phonemes/*.wav` | `OfflineOlChikiTTS.kt` (not yet created) | Phoneme-stitched speech synthesis |

### 2.3 — Missing XML Resources

| Resource | Status | Impact |
|----------|--------|--------|
| `res/values/strings.xml` | **MISSING** | `@string/app_name` and keyboard label undefined |
| `res/values/themes.xml` | **MISSING** | `@style/Theme.TribalFLN` undefined |
| `res/values/colors.xml` | **MISSING** | No color palette defined |
| `res/xml/file_paths.xml` | **MISSING** | FileProvider for PDF sharing broken |
| `res/xml/method.xml` | **MISSING** | IME service metadata missing |
| `res/xml/keyboard_ol_chiki.xml` | **MISSING** | Keyboard layout binding absent |
| `res/drawable/` (icons) | **MISSING** | No app icon or drawable resources |

---

## 3. `[HIGH]` Unfulfilled Problem Statement Requirements

### 3.1 — Missing Kotlin Source Files (15 of 19 required)

| # | File | Status | SIH Requirement |
|---|------|--------|-----------------|
| H-1 | `MainActivity.kt` | ❌ Missing in `app/` (exists in `android/` under wrong package) | Legacy entry point with permissions + PDF binding |
| H-2 | `WorksheetPdfGenerator.kt` | ❌ Missing in `app/` (exists in `android/`) | A4 NIPUN worksheet PDF with Ol Chiki font wrapping |
| H-3 | `VoiceEngineManager.kt` | ❌ Missing in `app/` (exists in `android/`) | Speech recognizer + keyword matcher + audio playback |
| H-4 | `OnDeviceVectorSearch.kt` | ❌ Missing | INT8 ONNX MiniLM vector search (<50ms) |
| H-5 | `ClassroomNoiseFilter.kt` | ❌ Missing | WebRTC spectral subtraction DSP filter |
| H-6 | `ClassroomAudioRecorder.kt` | ❌ Missing | 16kHz PCM recording with adaptive threshold |
| H-7 | `SileroVadEngine.kt` | ❌ Missing | INT8 Silero VAD ONNX wrapper |
| H-8 | `LocalVectorDatabase.kt` | ❌ Missing (referenced by TribalFLNApplication) | SQLite vector DB with sub-10ms queries |
| H-9 | `OfflineOcrScanner.kt` | ❌ Missing | CameraX + MobileNet ONNX grading |
| H-10 | `TribalKeyboardService.kt` | ❌ Missing (referenced by AndroidManifest) | Ol Chiki IME keyboard |
| H-11 | `ClassroomMeshSync.kt` | ❌ Missing | Wi-Fi Direct P2P worksheet sync |
| H-12 | `BluetoothThermalPrinter.kt` | ❌ Missing | ESC/POS thermal printer driver |
| H-13 | `OfflineOlChikiTTS.kt` | ❌ Missing | Phoneme-stitched TTS synthesizer |
| H-14 | `LearningGapRadarView.kt` | ❌ Missing (referenced by layout) | Custom Canvas radar chart |
| H-15 | `OlChikiGlyphRenderer.kt` | ❌ Missing | Interactive letter-tracing View |

### 3.2 — Multi-Dialect & Script Support

| Requirement | Status | Evidence |
|-------------|--------|----------|
| Santhali (Ol Chiki) | ⚠️ Partial | `curriculum_data.json` has Santhali entries; font file missing; TTS engine missing |
| Ho (Warang Chiti) | ❌ Not implemented | No Warang Chiti mappings, font, or G2P tables anywhere in codebase |
| Mundari | ❌ Not implemented | No Mundari support in any file |
| Hindi input | ✅ Implemented | `VoiceEngineManager.kt` has Hindi speech recognition + keyword matching |

### 3.3 — 100% Offline / Airplane Mode Guarantee

| Check | Status | Detail |
|-------|--------|--------|
| No cloud API calls | ✅ Pass | All code uses local assets, Room DB, ONNX Runtime |
| No network permissions required for core flow | ⚠️ Partial | `ACCESS_NETWORK_STATE` declared but not required for core; `INTERNET` absent (good) |
| Speech recognition offline | ✅ Pass | `VoiceEngineManager` uses `EXTRA_PREFER_OFFLINE` + `createOnDeviceSpeechRecognizer` |
| ONNX models local | ❌ Fail | No `.onnx` files present in `assets/` |

### 3.4 — NIPUN Bharat Pedagogical Compliance

| Requirement | Status | Evidence |
|-------------|--------|----------|
| Phonemic awareness | ⚠️ Partial | `curriculum_data.json` has phonetic_latin fields; TTS not implemented |
| Number sense / counting | ✅ Partial | `WorksheetPdfGenerator` has number tracing + counting blocks |
| Script tracing (Ol Chiki) | ⚠️ Partial | PDF has dotted character tracing blocks; font file missing; interactive tracing view missing |
| Diagnostic grading | ❌ Not implemented | `OfflineOcrScanner.kt` does not exist |
| FLN competency tracking | ✅ Implemented | `StudentProgressDatabase.kt` has NIPUN-aligned competency scores |

### 3.5 — Hardware & Memory Constraint (<180 MB RAM)

| Check | Status | Evidence |
|-------|--------|----------|
| `largeHeap="true"` | ✅ Set | AndroidManifest declares `largeHeap="true"` |
| `onTrimMemory` handling | ✅ Implemented | `TribalFLNApplication` has CRITICAL/LOW/MODERATE tiers |
| Crash log rotation | ✅ Implemented | Keeps last 10 crash logs |
| Bitmap/Canvas cleanup | ⚠️ Partial | `WorksheetPdfGenerator` closes PdfDocument in finally block |
| OrtSession lifecycle | ⚠️ Partial | `OrtEnvironment` is recycled on critical memory; individual sessions not managed |
| AudioRecord cleanup | ❌ Missing | `ClassroomAudioRecorder.kt` does not exist |
| MediaPlayer cleanup | ✅ Implemented | `VoiceEngineManager` has `releaseMediaPlayer()` in all exit paths |

### 3.6 — P2P Mesh & Data Sync

| Check | Status | Evidence |
|-------|--------|----------|
| `ClassroomMeshSync.kt` | ❌ Missing | Wi-Fi Direct P2P engine not implemented |
| `BluetoothThermalPrinter.kt` | ❌ Missing | ESC/POS printer driver not implemented |
| `ProgressExporter` JSON export | ✅ Implemented | `StudentProgressDatabase.kt` has `exportTelemetryToJson()` |
| Encryption for P2P payloads | ✅ Implemented | `SecurityUtils.kt` has AES-256-GCM encrypt/decrypt |

---

## 4. `[MEDIUM]` Recommended Performance & Reliability Fixes

### 4.1 — Package & Structural Issues

| # | Issue | Fix |
|---|-------|-----|
| M-1 | `SecurityUtils.kt` in `data/` declares `package com.example.flnapp` | Change to `package com.example.flnapp.data` |
| M-2 | `StudentProgressDatabase.kt` in `data/` declares `package com.example.flnapp` | Change to `package com.example.flnapp.data` |
| M-3 | Two parallel project structures (`app/` and `android/`) with different packages | Consolidate to single `app/` with `com.example.flnapp` |
| M-4 | `TribalFLNApplication.kt` imports `com.example.flnapp.data.StudentProgressDatabase` but the file's package is wrong | Fix package declarations in both data files |

### 4.2 — Missing Test Suite

| # | Issue | Fix |
|---|-------|-----|
| M-5 | `TribalFLNUnitTestSuite.kt` does not exist | Create JUnit4 test suite for cosine similarity, PDF creation, G2P parsing, encryption round-trip |

### 4.3 — Code Quality

| # | Issue | Fix |
|---|-------|-----|
| M-6 | `TeacherDashboardActivity.kt` has `createNipunWorksheetPdf()` duplicating `WorksheetPdfGenerator` logic | Refactor to delegate to `WorksheetPdfGenerator` |
| M-7 | No `@string` resources — hardcoded strings in manifest and layout | Create `res/values/strings.xml` |
| M-8 | No `@style` resources — theme referenced but undefined | Create `res/values/themes.xml` with Material3 theme |
| M-9 | `build.gradle.kts` missing `settings.gradle.kts` at project root | Required for Gradle wrapper to function |
| M-10 | No `gradlew` / `gradle-wrapper.jar` present | Cannot run `./gradlew assembleDebug` without these |

### 4.4 — ProGuard & Optimization

| # | Issue | Fix |
|---|-------|-----|
| M-11 | `proguard-rules.pro` references classes that don't exist yet | Update keep-rules after all files are created |
| M-12 | No `gradle.properties` with `org.gradle.jvmargs` | May cause OOM during large ONNX model dexing |

---

## 5. `[ACTION PLAN]` Next Immediate Steps

### Phase 1 — Fix Compilation Blockers (Priority: CRITICAL)

| Step | Action | Files |
|------|--------|-------|
| 1.1 | Fix package declarations in `data/` directory | `SecurityUtils.kt`, `StudentProgressDatabase.kt` → change to `package com.example.flnapp.data` |
| 1.2 | Create `res/values/strings.xml` with `app_name` and `ol_chiki_keyboard_label` | New file |
| 1.3 | Create `res/values/themes.xml` with `Theme.TribalFLN` Material3 theme | New file |
| 1.4 | Create `res/xml/file_paths.xml` for FileProvider | Copy from `android/` or create |
| 1.5 | Create `res/xml/method.xml` for IME metadata | New file |
| 1.6 | Update `TribalFLNApplication.kt` imports to match corrected package paths | Edit existing |
| 1.7 | Create `res/values/colors.xml` with TribalFLN color palette | New file |

### Phase 2 — Port Existing Code from `android/` to `app/` (Priority: HIGH)

| Step | Action | Files |
|------|--------|-------|
| 2.1 | Copy `VoiceEngineManager.kt` from `android/` to `app/` | Already in `com.example.flnapp` package |
| 2.2 | Copy `WorksheetPdfGenerator.kt` from `android/` to `app/` | Already in `com.example.flnapp` package |
| 2.3 | Copy `MainActivity.kt` from `android/` and refactor package | Change from `com.example.primaryschoolassistant` to `com.example.flnapp` |
| 2.4 | Copy `curriculum_data.json` from `android/` to `app/src/main/assets/` | 15 curriculum entries ready |
| 2.5 | Update `TeacherDashboardActivity.kt` to delegate PDF generation to `WorksheetPdfGenerator` | Remove duplicate code |

### Phase 3 — Create Missing Core Modules (Priority: HIGH)

| Step | Action | Estimated Lines |
|------|--------|----------------|
| 3.1 | Create `LearningGapRadarView.kt` — Custom Canvas radar chart | ~200 |
| 3.2 | Create `TribalKeyboardService.kt` — Ol Chiki IME | ~300 |
| 3.3 | Create `LocalVectorDatabase.kt` — SQLite vector search | ~80 |
| 3.4 | Create `OnDeviceVectorSearch.kt` — ONNX MiniLM wrapper | ~250 |
| 3.5 | Create `ClassroomAudioRecorder.kt` — PCM recording loop | ~300 |
| 3.6 | Create `SileroVadEngine.kt` — VAD ONNX wrapper | ~300 |
| 3.7 | Create `ClassroomNoiseFilter.kt` — Audio DSP filter | ~150 |
| 3.8 | Create `OfflineOcrScanner.kt` — CameraX OCR | ~250 |
| 3.9 | Create `ClassroomMeshSync.kt` — Wi-Fi Direct P2P | ~400 |
| 3.10 | Create `BluetoothThermalPrinter.kt` — ESC/POS driver | ~200 |
| 3.11 | Create `OfflineOlChikiTTS.kt` — Phoneme TTS | ~200 |
| 3.12 | Create `OlChikiGlyphRenderer.kt` — Tracing View | ~150 |

### Phase 4 — Create Placeholder Assets (Priority: HIGH)

| Step | Action | Notes |
|------|--------|-------|
| 4.1 | Download or generate `NotoSansOlChiki-Regular.ttf` | Available from Google Fonts Noto collection |
| 4.2 | Create `res/drawable/ic_launcher_foreground.xml` | Adaptive icon for app |
| 4.3 | Create placeholder `.onnx` model files or document download instructions | VAD, MiniLM, MobileNet models |
| 4.4 | Create `res/mipmap-*` launcher icons | Standard Android adaptive icon set |

### Phase 5 — Build Infrastructure (Priority: HIGH)

| Step | Action | Notes |
|------|--------|-------|
| 5.1 | Create `settings.gradle.kts` at project root | Required for Gradle to identify modules |
| 5.2 | Add `gradle/wrapper/gradle-wrapper.properties` and `gradlew` | Required for `./gradlew assembleDebug` |
| 5.3 | Create `gradle.properties` with JVM args | `org.gradle.jvmargs=-Xmx2048m` |
| 5.4 | Create `app/src/main/res/xml/keyboard_ol_chiki.xml` | IME keyboard layout definition |

### Phase 6 — Testing & Validation (Priority: MEDIUM)

| Step | Action | Notes |
|------|--------|-------|
| 6.1 | Create `TribalFLNUnitTestSuite.kt` | JUnit4 tests for cosine similarity, PDF, G2P, encryption |
| 6.2 | Run `./gradlew assembleDebug` | Verify clean compilation |
| 6.3 | Run `./gradlew test` | Verify unit tests pass |
| 6.4 | Verify APK size < 155 MB | Per README constraint |
| 6.5 | Profile peak RAM on API 28 emulator | Must stay < 180 MB heap |

---

## 6. Summary: What Exists vs. What's Needed

| Category | Files Present | Files Needed | Gap |
|----------|--------------|--------------|-----|
| Kotlin source (`app/`) | 4 | 19 | **-15** |
| Kotlin source (`android/`) | 3 (different package) | — | 3 orphaned |
| XML resources | 1 layout | 7+ (strings, themes, colors, xml/) | **-6** |
| Binary assets | 0 | 6+ (font, ONNX, audio, curriculum) | **-6** |
| Test files | 0 | 1 | **-1** |
| Build config | 2 | 4 (settings, gradlew, properties) | **-2** |
| Python scripts | 0 | 3 | **-3** |
| **Total gap** | **10 files** | **40+ files** | **~30 files missing** |

---

## 7. Positive Assessment — What's Done Well

Despite the gaps, the existing code demonstrates strong engineering:

1. **`TribalFLNApplication.kt`** — Excellent lifecycle management with phased init, memory pressure handling, and crash logging with file rotation
2. **`TeacherDashboardActivity.kt`** — Well-structured StateFlow-based UI with proper permission handling and coroutine lifecycle management
3. **`StudentProgressDatabase.kt`** — Production-grade Room setup with indexed entities, Flow queries, raw SQL aggregation, and encrypted JSON export
4. **`SecurityUtils.kt`** — Clean Android KeyStore AES-256-GCM implementation with proper serialization and secure wipe
5. **`VoiceEngineManager.kt`** (in `android/`) — Robust offline speech recognition with Levenshtein matching and MediaPlayer lifecycle management
6. **`WorksheetPdfGenerator.kt`** (in `android/`) — Complete A4 PDF generation with Ol Chiki font fallback, number tracing, counting blocks, and character tracing
7. **`build.gradle.kts`** — Correct KSP/Room/ONNX/CameraX dependency declarations
8. **`curriculum_data.json`** — Well-structured 15-entry NIPUN FLN curriculum with Hindi, Santhali Ol Chiki, phonetic Latin, and audio file references

---

*End of Audit Report*

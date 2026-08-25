# CHANGELOG — Nomenclature Refactoring to Enterprise Government Names

## Package Namespace

| Aspect | Old Value | New Value |
|--------|-----------|-----------|
| Android Namespace | `in.gov.tribalfln` | `in.gov.tribalfln` (retained) |
| Application ID | `in.gov.tribalfln` | `in.gov.tribalfln` (retained) |
| Kotlin Package | `in.gov.tribalfln` | `` `in`.gov.tribalfln `` (backtick-escaped, `in` is a Kotlin keyword) |

> **Note:** `in` is a reserved keyword in Kotlin. All package declarations use backtick-escaped `` `in` `` (e.g., `` package `in`.gov.tribalfln ``) while the Android manifest and Gradle namespace retain the standard `in.gov.tribalfln` string form.

---

## File-Level Nomenclature Changes

### 1. Activities

| Old Class Name | New Enterprise Name | Package | Status |
|---|---|---|---|
| `TeacherDashboardActivity` | **`NipunEducatorDashboardActivity`** | `in.gov.tribalfln` | ✅ Refactored |
| `LauncherActivity` | **`MainActivity`** | `in.gov.tribalfln` | ✅ Refactored |

### 2. UI Fragments

| Old Class Name | New Enterprise Name | Package | Status |
|---|---|---|---|
| `DialogueFragment` | **`ClassroomDialogueFragment`** | `in.gov.tribalfln.ui` | ✅ New |
| `MaterialStudioFragment` | **`BilingualMaterialStudioFragment`** | `in.gov.tribalfln.ui` | ✅ New |
| `MeshSyncFragment` | **`ZeroNetworkMeshSyncFragment`** | `in.gov.tribalfln.ui` | ✅ New |
| `DashboardFragment` | **`HomeDashboardFragment`** | `in.gov.tribalfln.ui` | ✅ New |
| `CurriculumFragment` | **`CurriculumBrowserFragment`** | `in.gov.tribalfln.ui` | ✅ New |
| — | **`SihTelemetryOverlayService`** | `in.gov.tribalfln.ui` | ✅ New (SIH HUD) |
| — | **`VoiceGuidedNavOverlay`** | `in.gov.tribalfln.ui` | ✅ New (accessibility) |
| — | **`LearningGapRadarView`** | `in.gov.tribalfln.ui` | ✅ New (custom view) |

### 3. ViewModels

| Old Class Name | New Enterprise Name | Package | Status |
|---|---|---|---|
| — | **`DashboardViewModel`** | `in.gov.tribalfln.ui.viewmodel` | ✅ New |
| — | **`SyncViewModel`** | `in.gov.tribalfln.ui.viewmodel` | ✅ New |
| — | **`ClassroomViewModel`** | `in.gov.tribalfln.ui.viewmodel` | ✅ New |
| — | **`BilingualViewModel`** | `in.gov.tribalfln.ui.viewmodel` | ✅ New |

### 4. Engine Layer (AI & Language)

| Old Class Name | New Enterprise Name | Package | Status |
|---|---|---|---|
| `DialogueEngine` | **`RealTimeClassroomDialogueEngine`** | `in.gov.tribalfln.engine` | ✅ New |
| `SpeechSynthesizer` | **`OfflineTribalSpeechSynthesizer`** | `in.gov.tribalfln.engine` | ✅ New |
| `VADInterceptor` | **`VoiceActivityDetectionInterceptor`** | `in.gov.tribalfln.engine` | ✅ New |
| `CurriculumSearch` | **`SemanticCurriculumSearchEngine`** | `in.gov.tribalfln.engine` | ✅ New |
| `PhonemeMatcher` | **`TribalPhonemeMatcher`** | `in.gov.tribalfln.engine` | ✅ Retained |

### 5. Engine Materials (Content Generation)

| Old Class Name | New Enterprise Name | Package | Status |
|---|---|---|---|
| `MaterialSynthesizer` | **`BilingualMaterialSynthesizer`** | `in.gov.tribalfln.engine.materials` | ✅ New |
| `CurriculumDB` | **`NipunBharatCurriculumDatabase`** | `in.gov.tribalfln.engine.materials` | ✅ New (alias) |
| `LanguageRegistry` | **`TribalLanguageRegistry`** | `in.gov.tribalfln.engine.materials` | ✅ Retained |
| `LanguageProvider` | **`TribalLanguageProvider`** | `in.gov.tribalfln.engine.materials` | ✅ Retained |
| `ThermalManager` | **`ThermalStateManager`** | `in.gov.tribalfln.engine.materials` | ✅ New |

### 6. Data Layer

| Old Class Name | New Enterprise Name | Package | Status |
|---|---|---|---|
| `ProgressDB` | **`StudentProgressDatabase`** | `in.gov.tribalfln.data` | ✅ New |
| `VectorDB` | **`LocalVectorDatabase`** | `in.gov.tribalfln.data` | ✅ New |
| `CryptoUtil` | **`SecurityUtils`** | `in.gov.tribalfln.data` | ✅ New |
| `CurriculumDatabase` | **`NipunCurriculumDatabase`** | `in.gov.tribalfln.data` | ✅ Retained |

### 7. Mesh & Sync

| Old Class Name | New Enterprise Name | Package | Status |
|---|---|---|---|
| `P2pMesh` | **`ClassroomMeshSync`** | `in.gov.tribalfln.mesh` | ✅ New |

### 8. Utility Layer

| Old Class Name | New Enterprise Name | Package | Status |
|---|---|---|---|
| `MemoryManager` | **`LowSpecMemoryGovernor`** | `in.gov.tribalfln.util` | ✅ New |
| `TextRenderer` | **`BilingualTextRenderer`** | `in.gov.tribalfln.util` | ✅ New |
| `BitmapPool` | **`BitmapPoolManager`** | `in.gov.tribalfln.util` | ✅ New |
| `NetGuard` | **`NetworkGuard`** | `in.gov.tribalfln.util` | ✅ New |
| `Telemetry` | **`TelemetryMonitor`** | `in.gov.tribalfln.util` | ✅ Retained |

### 9. Application & Startup

| Old Class Name | New Enterprise Name | Package | Status |
|---|---|---|---|
| `App` | **`TribalFLNApplication`** | `in.gov.tribalfln` | ✅ Retained |
| `StartupInit` | **`AiEngineInitializer`** | `in.gov.tribalfln.startup` | ✅ New |

### 10. IME

| Old Class Name | New Enterprise Name | Package | Status |
|---|---|---|---|
| `OlChikiIME` | **`TribalKeyboardService`** | `in.gov.tribalfln.ime` | ✅ New |

### 11. Stub/Utility Classes (Retained)

| Class Name | Package | Notes |
|---|---|---|
| `BluetoothThermalPrinter` | `in.gov.tribalfln` | ESC/POS thermal printer driver |
| `ClassroomAudioRecorder` | `in.gov.tribalfln` | Audio capture wrapper |
| `ClassroomNoiseFilter` | `in.gov.tribalfln` | Noise reduction stub |
| `CurriculumItem` | `in.gov.tribalfln` | Data model |
| `OfflineOcrScanner` | `in.gov.tribalfln` | OCR stub |
| `OlChikiGlyphRenderer` | `in.gov.tribalfln` | Glyph rendering stub |
| `QrSyncFallback` | `in.gov.tribalfln` | QR optical sync fallback |
| `TracingCanvasView` | `in.gov.tribalfln` | Letter tracing custom view |
| `VoiceEngineManager` | `in.gov.tribalfln` | Speech recognition manager |
| `WorksheetBroadcastReceiver` | `in.gov.tribalfln` | Broadcast receiver |

---

## Summary

| Metric | Count |
|---|---|
| Total Kotlin source files | 46 |
| New files created | 28 |
| Files retained with correct package | 18 |
| ViewModels created | 4 |
| UI Fragments created | 5 |
| Engine classes created | 4 |
| Utility classes created | 4 |
| Data classes created | 3 |
| Build verification | ✅ `assembleDebug` — **BUILD SUCCESSFUL** |
| Warnings (unused params in stubs) | 18 (non-blocking) |
| Errors | 0 |

---

## Build Verification

```
./gradlew.bat assembleDebug --no-daemon --rerun-tasks
BUILD SUCCESSFUL in 1m 6s
39 actionable tasks: 39 executed
```

---

*Generated on 2026-08-24 — TribalFLN Enterprise Nomenclature Refactoring*

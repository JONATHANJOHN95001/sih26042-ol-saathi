# TribalFLN — Offline Foundational Literacy & Numeracy for Tribal India

> **Smart India Hackathon 2026** · Problem Statement: NIPUN Bharat Mission  
> **100% Offline · Zero-Training · Edge-AI Powered**

---

## Problem Statement

Indigenous/tribal language communities (Santhali Ol Chiki, Ho, Mundari) in remote rural India lack digital educational tools in their native scripts. Existing FLN (Foundational Literacy & Numeracy) solutions require internet connectivity unavailable in sub-₹5,000 Android Go tablets deployed in these classrooms.

## Solution

TribalFLN is a **fully offline Android application** that provides:

1. **Zero-Training Hindi→Ol Chiki Voice Translation** — Speak Hindi, get instant Santhali Ol Chiki text + spoken audio
2. **NIPUN Bharat Bilingual Worksheets** — Auto-generated A4 PDFs with Hindi instructions + Ol Chiki tracing/counting exercises
3. **Edge-AI OCR Grading** — Camera-based worksheet scanning with <400ms digit recognition
4. **P2P Mesh Sync** — Wi-Fi Direct file distribution between tablets without internet
5. **Hardware-Backed Encryption** — AES-256-GCM via Android KeyStore for student data

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        TeacherDashboardActivity                      │
│  ┌──────────┬──────────┬──────────┬──────────┬──────────┐          │
│  │  Home    │ Classroom│  Lessons │  Tools   │   Sync   │          │
│  │Dashboard │Translation│ Preview │ Browser  │ Manager  │          │
│  └────┬─────┴────┬─────┴────┬─────┴────┬─────┴────┬─────┘          │
│       │          │          │          │          │                  │
│  ┌────▼─────┐ ┌──▼───────┐ │ ┌────────▼───┐ ┌───▼──────────┐     │
│  │Dashboard │ │Translation│ │ │Curriculum  │ │  SyncViewModel│     │
│  │ViewModel │ │ViewModel  │ │ │ViewModel   │ │              │     │
│  └────┬─────┘ └──┬───────┘ │ └────────┬───┘ └───┬──────────┘     │
└───────┼──────────┼──────────┼──────────┼──────────┼────────────────┘
        │          │          │          │          │
┌───────▼──────────▼──────────▼──────────▼──────────▼────────────────┐
│                        Edge-AI Engine Layer                         │
│  ┌─────────────┐ ┌──────────────┐ ┌──────────────┐                │
│  │ Silero VAD  │ │ OnDevice     │ │ OfflineOCR   │                │
│  │ (ONNX INT8) │ │ VectorSearch │ │ Scanner      │                │
│  │ 480 samples │ │ (384-dim)    │ │ (224×224)    │                │
│  └──────┬──────┘ └──────┬───────┘ └──────┬───────┘                │
│         │               │                │                         │
│  ┌──────▼──────┐ ┌──────▼───────┐ ┌──────▼───────┐                │
│  │SpeechRecog  │ │ LocalVector  │ │ OCR MobileNet│                │
│  │(offline Hi) │ │ Database     │ │ (ONNX INT8)  │                │
│  └─────────────┘ └──────────────┘ └──────────────┘                │
│                                                                     │
│  ┌─────────────┐ ┌──────────────┐ ┌──────────────┐                │
│  │Ol Chiki TTS │ │ WorksheetPdf │ │ Bluetooth    │                │
│  │(G2P→PCM)    │ │ Generator    │ │ ThermalPrint │                │
│  └─────────────┘ └──────────────┘ └──────────────┘                │
│                                                                     │
│  ┌─────────────┐ ┌──────────────┐ ┌──────────────┐                │
│  │NetworkGuard │ │ClassroomMesh │ │ SecurityUtils│                │
│  │(zero-net)   │ │Sync (Wi-Fi)  │ │ (AES-256-GCM)│                │
│  └─────────────┘ └──────────────┘ └──────────────┘                │
└─────────────────────────────────────────────────────────────────────┘
```

## Voice Translation Pipeline (Sub-3s Latency)

```
Mic (16kHz PCM)
    ↓
AudioRecord → SileroVadEngine (ONNX, 480 samples/frame)
    ↓ speech detected (prob > 0.5)
SpeechRecognizer (Android on-device, Hindi-IN)
    ↓
VoiceEngineManager.findBestMatch()
    ↓ Levenshtein distance scoring
CurriculumItem lookup
    ↓
OfflineOlChikiTTS.speak() → PCM audio output
    + OnDeviceVectorSearch → LocalVectorDatabase
```

## Project Structure

```
app/src/main/java/com/example/flnapp/
├── TribalFLNApplication.kt          # App init, ONNX/Room/VectorDB lifecycle
├── TeacherDashboardActivity.kt      # 5-tab bottom navigation host
├── MainActivity.kt                  # Legacy standalone activity
├── VoiceEngineManager.kt            # Speech→Match→Play pipeline
├── SileroVadEngine.kt               # ONNX VAD inference
├── OnDeviceVectorSearch.kt          # ONNX embedding + cosine search
├── OfflineOcrScanner.kt             # MobileNet OCR inference
├── OfflineOlChikiTTS.kt             # G2P synthesizer → PCM
├── WorksheetPdfGenerator.kt         # A4 Canvas → PDF
├── BluetoothThermalPrinter.kt       # ESC/POS raster output
├── ClassroomAudioRecorder.kt        # PCM capture with noise gate
├── ClassroomNoiseFilter.kt          # FFT spectral subtraction
├── OlChikiGlyphRenderer.kt          # Touch-based character tracing
├── data/
│   ├── LocalVectorDatabase.kt       # SQLite vector DB (384-dim)
│   ├── SecurityUtils.kt             # AES-256-GCM encryption
│   └── StudentProgressDatabase.kt   # Room DB (3 entities)
├── ime/
│   └── TribalKeyboardService.kt     # System-wide Ol Chiki IME
├── mesh/
│   └── ClassroomMeshSync.kt         # Wi-Fi Direct P2P sync
├── ui/
│   ├── HomeDashboardFragment.kt
│   ├── LiveTranslationFragment.kt
│   ├── WorksheetPreviewFragment.kt
│   ├── CurriculumBrowserFragment.kt
│   ├── SyncManagerFragment.kt
│   ├── LearningGapRadarView.kt      # 6-axis radar chart
│   └── viewmodel/
│       ├── DashboardViewModel.kt
│       ├── TranslationViewModel.kt
│       ├── WorksheetViewModel.kt
│       ├── CurriculumViewModel.kt
│       └── SyncViewModel.kt
└── util/
    ├── NetworkGuard.kt              # Zero-network enforcement
    └── BilingualTextRenderer.kt     # Dual-script font manager
```

## Hardware Requirements

| Component | Minimum |
|---|---|
| Android Version | 9.0 (API 28) |
| RAM | 2GB |
| Storage | 16GB |
| Processor | Quad-core ARM Cortex-A53/A55 |
| Heap Limit | 180MB (enforced) |

## Setup & Installation

### Prerequisites
- Android Studio (Arctic Fox or later)
- JDK 17
- Android SDK 35
- Physical device or emulator (API 28+)

### Build from Source
```bash
# Clone repository
git clone <repository-url>
cd sih-hackathon

# Set Java 17
export JAVA_HOME="/path/to/jdk-17"

# Build debug APK
./gradlew assembleDebug

# Install on device
adb install app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

### Run Tests
```bash
# Run TribalFLNUnitTestSuite (74 tests)
./gradlew testDebugUnitTest --tests "com.example.flnapp.TribalFLNUnitTestSuite"

# Run all tests
./gradlew testDebugUnitTest
```

### First Launch
1. Grant **Microphone** permission (voice translation)
2. Grant **Camera** permission (OCR worksheet scanning)
3. Grant **Location** permission (Wi-Fi Direct peer discovery)
4. Enable **Ol Chiki Keyboard** in Settings → Language & Input

## Test Coverage

| Test Class | Tests | What It Verifies |
|---|---|---|
| NetworkGuardTest | 6 | Zero-network enforcement |
| BilingualTextRendererTest | 10 | Dual-script font fallback |
| MemoryGovernanceTest | 7 | 180MB heap ceiling |
| OlChikiUnicodeTest | 5 | Unicode block validation |
| LocalVectorDatabaseTest | 8 | Cosine similarity & top-K |
| SecurityUtilsTest | 8 | AES-256-GCM round-trip |
| OfflineOlChikiTTSTest | 8 | G2P phoneme mapping |
| WorksheetPdfGeneratorTest | 8 | PDF structure & content |
| ThermalPrinterProtocolTest | 8 | ESC/POS byte commands |
| MeshSyncProtocolTest | 6 | P2P file transfer protocol |
| **Total** | **74** | **All passing** |

## NIPUN Bharat Alignment

| NIPUN Outcome | TribalFLN Feature |
|---|---|
| Foundational Literacy (L1) | Ol Chiki vowel/consonant recognition |
| Foundational Numeracy (L1) | Number tracing 1–10, counting exercises |
| Bilingual Instruction | Hindi + Ol Chiki side-by-side worksheets |
| Assessment | Camera OCR grading of physical worksheets |
| Teacher Enablement | Dashboard with learning gap radar chart |
| Zero-Infrastructure | 100% offline, P2P mesh sync |

## License

Internal / SIH Hackathon 2026

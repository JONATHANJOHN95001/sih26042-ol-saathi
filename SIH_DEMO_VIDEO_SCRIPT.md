# TribalFLN — SIH Demo Video Script (2:00)

> **Duration:** 2 minutes · **Format:** Screen recording + physical tablet footage  
> **Devices:** 2× low-end Android tablets (sub-₹5,000, 2GB RAM)

---

## Scene 1: Airplane Mode & Zero-Network Proof (0:00 – 0:30)

### Visual
- Close-up of tablet Settings → Airplane Mode toggle → **ON**
- Wi-Fi icon disappears, signal bars show airplane icon
- Launch TribalFLN app → Dashboard loads instantly

### Narration
> "TribalFLN operates in **100% offline mode**. Watch — airplane mode is ON. No Wi-Fi, no data, no cloud. The app boots in under 2 seconds on a ₹5,000 tablet."

### On-Screen Action
1. Pull down notification shade → tap Airplane Mode
2. Open TribalFLN → Home Dashboard shows NIPUN metrics
3. Point to **NetworkGuard status** in toolbar: "Offline Mode Active"

---

## Scene 2: Live Hindi ↔ Ol Chiki Voice Translation (0:30 – 1:15)

### Visual
- Tap **Classroom** tab → Live Translation screen appears
- Press and hold **PTT button** → waveform animation activates
- Speak Hindi phrase → release button
- Ol Chiki text appears on screen → audio plays

### Narration
> "The teacher speaks in Hindi: **'गिनना सीखो'** — Learn to count. In under 3 seconds, TribalFLN translates to Santhali Ol Chiki: **ᱜᱤᱫᱽᱨᱟᱹ ᱥᱤᱠᱚ** — and speaks it aloud using our offline TTS engine."

### On-Screen Action
1. Tap **Classroom** tab
2. Press & hold PTT button → say "गिनना सीखो"
3. Release button → waveform bars animate (VAD active)
4. **< 3 seconds later**: Ol Chiki text appears + audio plays
5. Show **latency indicator**: "< 1.2s" on screen
6. Tap **flashcard** chip → hear "Hati" (tree) in Ol Chiki

### Key Latency Breakdown
| Stage | Time |
|---|---|
| VAD detection (Silero ONNX) | ~30ms |
| Speech recognition (offline Hindi) | ~800ms |
| Levenshtein matching | ~5ms |
| Ol Chiki TTS synthesis | ~200ms |
| **Total pipeline** | **< 1.2s** |

---

## Scene 3: Bilingual Worksheet Generation (0:45 – 1:15)

### Visual
- Tap **Lessons** tab → Worksheet Preview appears
- PDF auto-generates → shows bilingual A4 worksheet
- Hindi instructions + Ol Chiki tracing exercises side-by-side

### Narration
> "TribalFLN auto-generates **bilingual A4 worksheets** matching NIPUN Bharat learning outcomes. Hindi instructions on the left, Ol Chiki tracing guides on the right — number tracing, counting exercises, and character practice. All generated offline in under 2 seconds."

### On-Screen Action
1. Tap **Lessons** tab
2. WorksheetPreviewFragment loads → PDF generates automatically
3. Show worksheet content:
   - Header: "निपुण भारत आधारभूत साक्षरता एवं संख्याज्ञान कार्यपत्रक"
   - Ol Chiki subtitle: "ᱱᱤᱯᱩᱱ ᱵᱷᱟᱨᱚᱛ"
   - Activity 1: Number tracing (1–6) with dotted guides
   - Activity 2: Count and circle exercises
   - Activity 3: Ol Chiki character tracing `[ ᱪ ] [ ᱪ ] [ ᱪ ] [ ᱪ ]`
4. Tap **Export** → PDF opens in system viewer
5. (Optional) Show thermal printer output if available

---

## Scene 4: Offline P2P Mesh Sync (1:15 – 2:00)

### Visual
- Two tablets side by side
- Tablet A: Tap **Sync** tab → go online → discover peers
- Tablet B: Also goes online → appears as peer
- Send worksheet from A → B receives it instantly

### Narration
> "Without any internet router, TribalFLN uses **Wi-Fi Direct** to sync data between tablets. The teacher sends worksheets to student devices, and competency scores flow back — all through a local mesh network."

### On-Screen Action
1. **Tablet A** (Teacher): Tap **Sync** tab → tap "Go Online"
2. **Tablet B** (Student): Tap **Sync** tab → tap "Go Online"
3. Tablet A shows: "Peer found: Teacher Tablet B"
4. Tablet A: Go to Lessons → tap **Share via P2P**
5. Tablet B receives worksheet notification
6. Tablet B: Opens received worksheet → content renders correctly
7. Show **Storage** section: "Core App: 45MB, VectorDB: 2MB, Audio: 15MB"

---

## Closing Frame (2:00)

### Visual
- Dashboard with all metrics visible
- Text overlay: "TribalFLN — 100% Offline · Zero-Training · Edge-AI"

### Narration
> "TribalFLN: bringing NIPUN Bharat FLN education to India's most underserved tribal classrooms — no internet required."

---

## Technical Notes for Recording

| Setting | Value |
|---|---|
| Screen Resolution | 1080×2400 (or match tablet) |
| Frame Rate | 30fps minimum |
| Audio | Narration + tablet speaker output |
| Emulator (fallback) | `emulator-5554` with API 28 |
| APK to install | `app-arm64-v8a-debug.apk` |

## Pre-Recording Checklist

- [ ] Airplane mode tested on target device
- [ ] Hindi speech pack installed for offline recognition
- [ ] Ol Chiki keyboard enabled in Settings
- [ ] Two tablets paired via Wi-Fi Direct (for Scene 4)
- [ ] Thermal printer connected (optional, for Scene 3)
- [ ] Curriculum data loaded (`curriculum_data.json`)
- [ ] App version: 1.0.0

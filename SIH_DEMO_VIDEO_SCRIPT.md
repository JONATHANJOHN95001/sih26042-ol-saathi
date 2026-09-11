# Ol Saathi — SIH Demo Video Script (2:00)

> **Duration:** 2 minutes · **Format:** Screen recording + physical tablet footage
> **Devices:** Android 9 tablet, 2 GB RAM (sub-5,000 INR)

---

## Scene 1: Airplane Mode and Zero-Network Proof (0:00 – 0:25)

### Visual
- Close-up of tablet Settings, Airplane Mode toggle ON
- Wi-Fi icon disappears, signal bars show airplane icon
- Launch Ol Saathi, app loads instantly

### Narration
> "Ol Saathi runs fully offline. Airplane mode is ON. No Wi-Fi, no data, no cloud. The app starts in under a second on a five-thousand-rupee tablet."

### On-Screen Action
1. Pull down notification shade, tap Airplane Mode
2. Open Ol Saathi, the lesson list loads
3. Tap the three dots, Check and Proof, Run Checks: pack loads, both fonts render, worksheet generates, zero network calls

---

## Scene 2: Teaching a Lesson Line by Line (0:25 – 1:10)

### Visual
- Tap Lessons tab, pick the first lesson
- Walk through lines using Back and Next
- Each line shows the Hindi sentence, then the Santali in Ol Chiki with a provenance label
- Tap Show, turn tablet to face the camera: picture, large Santali text, Hindi underneath

### Narration
> "The teacher picks a lesson. She reads the Hindi line on screen: 'Neema and her grandmother went to the forest.' She taps Show and turns the tablet around. The class sees the picture, the Santali in large Ol Chiki, and the Hindi underneath. She asks the question aloud, and the children answer."

### On-Screen Action
1. Tap Lessons, pick the first lesson (title in Hindi)
2. Line 1/10 appears: Hindi card, then Santali card in green Ol Chiki with "Machine translation" provenance
3. Tap the Show button, the screen flips to child-facing mode: picture at top, Santali at up to 60sp, Hindi at 24sp, nothing else
4. Close, tap Next, line 2/10 loads
5. Walk through two or three more lines, showing the progress dots advance
6. On line 10, Next becomes "Questions"
7. Tap Questions: comprehension prompts appear in both languages, no scoring, just the question for the teacher to ask aloud

---

## Scene 3: Bilingual Worksheet (1:10 – 1:35)

### Visual
- Tap the Worksheet tab
- PDF generates, a three-page bilingual worksheet appears
- Show Hindi instructions, Ol Chiki tracing guides, and the flashcard cut-outs

### Narration
> "The teacher taps Worksheet. A bilingual A4 PDF generates in seconds, aligned to NIPUN Bharat learning outcomes. Hindi instructions, Ol Chiki tracing exercises, and flashcard cut-outs with pictures. All offline, all printed before class."

### On-Screen Action
1. Tap Worksheet tab
2. PDF loads, show page 1: header with NIPUN branding, Hindi and Ol Chiki
3. Scroll to show tracing exercises and flashcard cut-outs with pictures
4. Show the PDF viewer's share or print option

---

## Scene 4: The Proof Screen (1:35 – 1:55)

### Visual
- Tap the three dots, Check and Proof
- Show the content pack section: 53 entries, "Machine translation, IndicTrans2"
- Show the script rendering: Ol Chiki rendered live in the bundled font
- Show the performance section: cold start, peak memory, zero network calls

### Narration
> "Everything in this app is checkable. The proof screen reads live values from the running build: 53 entries translated by AI4Bharat IndicTrans2, both scripts rendering correctly, 43 megabytes of memory, zero network calls. Nothing is hardcoded. A judge can verify every number."

### On-Screen Action
1. Overflow menu, Check and Proof
2. Show content pack block: entry count, provenance chip showing "Machine translation, IndicTrans2"
3. Show the script rendering: live Ol Chiki text in the bundled Noto Sans Ol Chiki font
4. Show the performance block: memory, cold start, network calls, APK size
5. Scroll to show "No Santali voice on this device" (honest about the gap)

---

## Closing Frame (1:55 – 2:00)

### Visual
- The Show Class screen: picture, large Santali text, Hindi underneath
- Text overlay: "Ol Saathi — offline mother-tongue teaching for Jharkhand"

### Narration
> "Ol Saathi: a Hindi-speaking teacher can now deliver a Santali lesson, fully offline, on a low-cost tablet. Built for five thousand tribal-area schools in Jharkhand."

---

## Technical Notes for Recording

| Setting | Value |
|---|---|
| Screen Resolution | Match the tablet |
| Frame Rate | 30fps minimum |
| Audio | Narration only (no app audio) |
| Emulator (fallback) | `emulator-5554` with API 28 |
| APK to install | `app-release.apk` (4.23 MB) |

## Pre-Recording Checklist

- [ ] Airplane mode tested on target device
- [ ] Hindi speech pack installed for offline recognition
- [ ] Pre-flight checks all passing (Check and Proof screen)
- [ ] First lesson loaded in the lesson player
- [ ] App version: check the proof screen

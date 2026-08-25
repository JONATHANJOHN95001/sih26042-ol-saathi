# Demo click-path

> Keep this current. Any step that breaks is P0.
> Record the walkthrough as soon as it works — do not wait until the end.

## Setup before demoing
- [ ] Ol Saathi app installed on Android 9+ tablet (2 GB RAM minimum)
- [ ] App running, console clean
- [ ] Recording ready

## Install on tablet (sideload)

### Option A — USB (developer machine connected)
1. Enable **Developer Options** on the tablet:
   - Settings → About Tablet → tap **Build Number** 7 times
2. Enable **USB Debugging**:
   - Settings → Developer Options → toggle **USB Debugging** ON
3. Connect the tablet via USB. Authorise the RSA key on the device.
4. Install:
   ```
   adb install app/build/outputs/apk/release/app-release.apk
   ```
   Or debug variant:
   ```
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Option B — No USB (file transfer)
1. Enable **Install from Unknown Sources**:
   - Settings → Apps & Notifications → Special app access → **Install unknown apps** → select the file manager / browser you will use → toggle ON
2. Copy `app-release.apk` to the tablet via a USB drive or cloud transfer
3. Open the file on the tablet and tap **Install**

## APK details
- Package: `app.olsaathi`
- minSdk: 28 (Android 9)
- Signed with `app/release-key.jks`
- APK is NOT committed to the repo

## The path
1. App opens to **Ol Saathi** lesson list — tap any lesson
2. A Hindi phrase is shown; tap **Translate** or type Hindi
3. Ol Chiki translation appears with **VERIFIED CONTENT** label
4. Tap **▶ Play Santali** to hear the asset audio
5. Tap **🎤** (single tap) for a mock Hindi prompt (emulator demo)
6. Long-press the mic button for real voice input (on hardware)
7. Tap **📄 Worksheet** to generate a bilingual A4 PDF
8. Share or print the worksheet
9. Return to lesson list, tap **📋 Live Proof** to see:
   - Content pack details with green VERIFIED chip
   - Ol Chiki font live render test (ᱚᱞ ᱪᱤᱠᱤ)
   - Offline status (network calls: 0)
   - Latency history
   - Build info (versionName, applicationId, minSdk, ABI)

## Known gaps to not click on
- Voice input requires a physical microphone (use mock tap on emulator)
- TTS audio for translations depends on the device TTS engine being installed

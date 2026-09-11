# Demo click-path

> Keep this current. Any step that breaks is P0.
> Record the walkthrough as soon as it works — do not wait at the end.

## Pre-flight (do this on the morning of the demo)

Before anyone touches the app, run the built-in self-test:

1. Open Ol Saathi → tap **🔍 Pre-Flight**
2. Tap **▶ Run All Checks**
3. All 7 checks must be green. If **Hindi offline recognition** is red:
   - Go to **Settings → System → Languages & input → On-device speech recognition**
   - Add **Hindi** to the installed languages
   - This must be done with internet access, before aeroplane mode
4. All green? Tap **📋 Live Proof** to confirm the device is ready

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

## The demo path
1. App opens to **Ol Saathi** lesson list — tap any lesson
2. A Hindi phrase is shown; tap **Translate** or type Hindi
3. Santali translation appears with provenance label
4. Tap **▶ Play Santali** to hear the pre-rendered pack audio
5. Tap **🎤** (single tap) for a mock Hindi prompt (debug build only)
6. Long-press the mic button for real voice input (on hardware)
7. Tap **📄 Worksheet** to generate a bilingual A4 PDF (with assessment questions)
8. Share or print the worksheet
9. Return to lesson list, tap **📋 Live Proof** to see live device state
10. Tap **🔍 Pre-Flight** to re-run the self-test anytime

## APK details
- Package: `app.olsaathi`
- minSdk: 28 (Android 9)
- Signed with `app/release-key.jks`
- APK is NOT committed to the repo

## Known gaps to not click on
- Voice input requires the Hindi speech pack to be installed (pre-flight checks this)
- TTS audio for translations depends on the device TTS engine being installed
- No Santali system voice on Android — audio comes from pre-rendered pack WAVs

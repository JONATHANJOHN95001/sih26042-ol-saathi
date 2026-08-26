# Ol Saathi — start here

SIH26042, Government of Jharkhand. A Hindi-speaking teacher delivers a primary
school lesson in Santali without knowing the language, fully offline on a cheap
tablet.

---

## Read this first: a zip is the wrong way to share code

If six people work from six copies of a zip, there is no way to merge what any
of you do. There is also a graded deliverable called *"a public GitHub
repository"* currently sitting at zero.

**Both problems have the same two-command fix:**

```bash
gh repo create ol-saathi --public --source=. --remote=origin --description "Ol Saathi — offline mother-tongue teaching for Jharkhand primary schools. SIH26042."
```

```bash
git push -u origin master
```

Then everyone else runs `git clone` instead of unzipping, and the graded item is
done. Use this zip only for people who just want to install the app.

---

## I only want to try it

Install `app-release.apk`. It is **4 MB** and needs Android 9 or newer.

1. On the tablet or phone: Settings, Security, allow install from unknown sources
2. Copy the APK across and tap it

Or over USB, with developer options and USB debugging turned on:

```bash
adb install app-release.apk
```

**Then open the app and tap the three dots, then Check & Proof, then Run Checks.**
That screen tells you what works on your device and what does not. Run it before
you demo anything.

One thing it will probably tell you: **Hindi offline speech is not installed.**
Fix it at Settings, System, Languages and input, On-device speech recognition,
and add Hindi. Without it the voice demo cannot work with the network off.

---

## I want to build it

You need **Android Studio**, and **JDK 17 specifically**. Gradle 8.2 will not run
on JDK 21 or later, and that is the single most common way this fails to build.

1. Clone or unzip `source/`
2. Create `local.properties` in the project root, pointing at your own SDK:

   ```
   sdk.dir=C\:\\Users\\YOURNAME\\AppData\\Local\\Android\\Sdk
   ```

   There is a `local.properties.example` to copy. **Never commit this file**, it
   is different on every machine.

3. Build:

   ```bash
   ./gradlew :app:assembleDebug
   ./gradlew :app:testDebugUnitTest
   ```

   If Gradle complains about the Java version, point it at 17:

   ```bash
   JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:assembleDebug
   ```

The APK lands in `app/build/outputs/apk/debug/`.

**You cannot build a signed release.** `app/release-key.jks` is deliberately not
in this zip, because a signing key must not travel through a group chat. Jonathan
has it. Debug builds are fine for everything except final submission.

---

## What is in here

```
app-release.apk              install this to try it
HANDOVER.md                  this file
source/                      the whole project
  app/                       the Android app, Kotlin
  content/lessons.json       the NCERT lesson, hand-corrected
  tools/                     pack generation, verification, the review sheet
  bhashini/                  the Bhashini pipeline, ready for when keys arrive
  design/wireframes.html     the screen designs
  verification/              the review sheet and the recording studio
  deck/                      the pitch deck
```

Four files worth opening in a browser right now, no install needed:

- `verification/santali-review-sheet.html` — hand this to anyone who reads Ol Chiki
- `verification/santali-recording-studio.html` — hand this to anyone who speaks it
- `design/wireframes.html` — what the screens are meant to look like
- `deck/Ol-Saathi-SIH26042.pptx` — the pitch

---

## Where the project actually stands

| The six graded items | State |
|---|---|
| Hindi to Santali, minimum one language | **Built.** 53 entries, machine translated, in the APK |
| Voice to voice under three seconds | **Half built.** Hindi in works, lookup is 1 ms, Santali audio does not exist yet |
| Bilingual worksheets **and flashcards** | **Built and verified.** Worksheet 3 pages, flashcards 7 pages, both scripts |
| Offline on a low-end tablet | **Built and measured.** 43 MB on a 2 GB Android 9 device, 656 ms cold start |
| Demo video | **Not started** |
| Public GitHub repository | **Not pushed** |

Everything is aligned to **NIPUN Bharat**: three developmental goals across seven
foundational domains, shown on the proof screen and printed on every flashcard.

**No audio yet, and it cannot be generated.** The statement asks for synthesised
Santali speech. Meta's MMS model covers 1,143 languages and Santali is not one of
them, eSpeak has no Santali voice, and Android ships none. Bhashini is the only
synthesis route, and that account is still waiting on a faculty supervisor.

So the fix is a person rather than a model, and everything around that person is
built. Open `verification/santali-recording-studio.html` in any browser. It shows
each line in Hindi and Ol Chiki, records from the microphone, and exports the lot
as a zip. Then one command installs it:

```bash
python tools/apply_audio.py santali-recordings.zip --provenance native --reviewer "Their Name" --write
```

Rebuild and the play buttons are live. That is about twenty minutes of recording,
from the same person the translation review already needs.

---

## The rule this project runs on

**The app never invents output.** Every Santali line carries a label saying where
it came from: machine translation, checked by a person, or not available. If a
phrase is not in the pack, the app says so rather than guessing.

This matters more than it sounds. A teacher who does not read Santali cannot tell
a good translation from a bad one. The children can. So if you change anything in
this codebase, do not add a fallback that produces plausible-looking output.

---

## What needs doing, and none of it is code

**1. Push the repo.** Two commands above. Graded, currently zero.

**2. Find a Santali speaker.** This is by far the biggest win available, and it
now closes **two** graded items in the same sitting, not one.

- **They read.** Every line currently says "Machine translation". After an hour
  with `verification/santali-review-sheet.html`, sorted worst first, the lines
  they confirm say "Checked by" with their name and date.
- **They speak.** Twenty more minutes with
  `verification/santali-recording-studio.html` and the app has audio, which is
  the only requirement no amount of code can close.

Ask the department, ask the SIH mentor, ask the student groups. Jharkhand has
7.6 million Santali speakers and JAIN takes students from everywhere. Both files
open in a browser with nothing installed and no network.

**3. Get it on a real tablet**, Android 9 or newer, and install the Hindi offline
speech pack. Then measure the voice round trip ten times in aeroplane mode and
write down the median. That number goes on the slide instead of a claim.

**4. Record the demo video.** The submission requires one and there is none.

---

## If something breaks

Run **Check & Proof** first. It checks the pack, both fonts, speech, the
microphone, storage and worksheet generation, and tells you which one failed.
Most problems on a new machine are the JDK version or a missing
`local.properties`.

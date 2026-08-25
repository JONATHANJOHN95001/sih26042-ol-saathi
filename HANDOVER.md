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
  verification/              the Santali review sheet
  deck/                      the pitch deck
```

Three files worth opening in a browser right now, no install needed:

- `verification/santali-review-sheet.html` — hand this to anyone who reads Ol Chiki
- `design/wireframes.html` — what the screens are meant to look like
- `deck/Ol-Saathi-SIH26042.pptx` — the pitch

---

## Where the project actually stands

| The six graded items | State |
|---|---|
| Hindi to Santali, minimum one language | **Built.** 53 entries, machine translated, in the APK |
| Voice to voice under three seconds | **Wired, needs a real tablet.** Lookup is 1 ms |
| Bilingual worksheet | **Built and verified.** 3 A4 pages, both scripts |
| Offline on a low-end tablet | **Built and measured.** 43 MB on a 2 GB Android 9 device |
| Demo video | **Not started** |
| Public GitHub repository | **Not pushed** |

**No audio yet.** The statement asks for synthesised Santali speech. No open
Santali text-to-speech exists and Android has no Santali voice, so it can only
come from Bhashini, and that account is still waiting on a faculty supervisor.
The app says so on screen rather than hiding it.

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

**2. Find a Santali speaker.** This is the biggest single win available. Every
line currently reads "Machine translation". One hour from one person who reads Ol
Chiki and it reads "Checked by" with their name and date. The review sheet is
already sorted so the most doubtful lines come first. Ask the department, ask the
SIH mentor, ask the student groups. Jharkhand has 7.6 million Santali speakers
and JAIN takes students from everywhere.

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

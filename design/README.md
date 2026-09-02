# Design and UI

Start here if you are picking up the look of the app.

## What the app is

A Hindi-speaking teacher delivers a primary school lesson in Santali without
knowing the language, on a 2 GB Android 9 tablet, with no network. Native
Android, Kotlin, XML layouts, Material 3. Five screens.

## Where things are

| Path | What |
|---|---|
| `app/src/main/res/layout/` | the five real screens, this is what ships |
| `design/wireframes.html` | open in a browser, the intended layouts |
| `design/teach.png` | the Teach screen as built |
| `design/APP_REDESIGN_PROMPT.md` | the brief the current design came from |
| `stitch_screens/*.png` | earlier Stitch mockups, historical, not current |
| `screenshots/` | captures from the Android 9 emulator |

The five screens: Lesson list, Lesson player, Teach (live translation), Show
the class, and Check and proof.

## Four rules the design cannot break

These are not taste. Each one exists because the app makes a claim it has to be
able to defend.

**1. Every piece of Santali carries a provenance label, and it stays visible.**
The chip under the Santali text says where that text came from: machine
translation and which service, or checked by a named Santali speaker. A teacher
who cannot read Ol Chiki cannot tell a good translation from a bad one, so the
app never shows Santali without saying where it came from. Do not move it into a
tooltip, an info icon, or a details sheet. Audio has its own separate label next
to the play button, because a line can be machine translated and then read aloud
by a person, which makes the two claims different.

**2. "Show the class" is the one screen with no provenance on it.** It is what a
six-year-old looks at when the teacher turns the tablet around. A child cannot
judge a translation, so the label would only take space from what they can read.
That screen is picture, Santali as large as it fits, Hindi underneath, two small
controls in the corners. It should read like a page from a picture book. It also
stays awake and works in both orientations, while every other screen is portrait
locked.

**3. A miss shows nothing.** If a phrase is not in the offline pack, the app says
"Not in the offline pack" and shows no Santali at all. There is no nearest match,
no greyed-out guess, no placeholder. Do not design an empty state that implies
content exists.

**4. Ol Chiki needs its own font and room to breathe.** Santali is written in Ol
Chiki, which ships as `NotoSansOlChiki-Regular.ttf` in assets, because Android
has no Santali font. It renders at 26 to 30sp on the teacher screens and much
larger on the class screen. If you set Santali text in the default typeface it
comes out as empty boxes.

## Constraints worth designing around

- **The device is slow and small.** 2 GB RAM, Android 9, a 4.23 MB APK and a
  656 ms cold start that we would like to keep. Heavy image assets and animation
  libraries cost more here than they look like they do.
- **The room is bright and the tablet is held up.** Contrast matters more than
  subtlety. The class screen is seen from several metres away.
- **The teacher is holding it one-handed while talking.** Controls are large and
  few.
- **Everything is offline.** No remote fonts, no CDN, no network image loading.
  Anything the design needs has to be in the APK.

## Getting it running

Needs **JDK 17 specifically**. Gradle 8.2 will not run on 21 or newer, and that
is the most common way this fails to build.

```bash
JAVA_HOME="/path/to/jdk-17" ./gradlew :app:assembleDebug
JAVA_HOME="/path/to/jdk-17" ./gradlew :app:installDebug
```

Test on the low-spec emulator profile, not a modern phone image. A layout that
is comfortable on a 6.7 inch OLED can be unusable on the hardware these schools
actually have.

## Before you open a pull request

Read `AGENTS.md` at the repo root. It is short and it explains why the app is
built the way it is, including the one rule everything else follows: the app
never shows output it cannot back up.

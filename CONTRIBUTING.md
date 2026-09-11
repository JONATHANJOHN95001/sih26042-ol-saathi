# Ol Saathi

SIH26042, Government of Jharkhand. A Hindi-speaking teacher delivers a primary
school lesson in Santali without knowing the language, fully offline on a 2 GB
Android 9 tablet.

Native Android, Kotlin. No backend, no server, no runtime network calls.

---

## The one rule that matters

**The app never invents output.** Every Santali string carries a provenance
label saying where it came from: machine translation, checked by a named person,
or not available. A phrase that is not in the pack shows nothing.

This is not style. A teacher who cannot read Ol Chiki cannot tell a good
translation from a bad one. The children can. So:

- **Never add a fuzzy match, a nearest-neighbour fallback, or a "did you mean".**
- **Never widen a lookup to make a miss succeed.** A miss is correct behaviour.
- Never label something verified that no human verified.

If a change would make the app produce plausible-looking output it cannot back
up, do not make it. Say so instead.

---

## Build and verify

Requires **JDK 17 specifically**. Gradle 8.2 will not run on 21 or newer, and
that is the single most common way this fails to build.

```bash
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:assembleDebug
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:testDebugUnitTest
```

Verification tools, all of which should pass before any commit:

```bash
python tools/verify_traceability.py   # every file cited in the matrix exists
python tools/verify_assets.py         # no placeholder fonts or models
```

**"It compiles" is not done.** Anything that changes what the user sees gets
checked on the Android 9 emulator (`TribalFLN_LowSpec_API28`) before it is
called finished.

---

## Architecture, and why it is this way

**All AI runs at build time. Nothing runs at runtime.**

Translation happens once on a laptop with IndicTrans2 1B (1,116M parameters),
and the finished text ships inside the APK as JSON. The tablet does a hash
lookup in about 1 ms.

This is deliberate and it is the project's main design argument. A 1.1B model
cannot run in 2 GB of RAM. The distilled 200M one that could was tested and
emitted Arabic characters. So the choice was a better model at build time
against a worse model at run time, and build time also lets a human correct the
output before a child ever sees it.

**Do not propose moving inference on-device.** It has been evaluated and
rejected on evidence.

```
app/src/main/assets/pack/pack.sat.json   53 entries, the whole content pack
app/src/main/java/app/olsaathi/          12 Kotlin files, 5 screens
app/src/main/res/drawable/ic_card_*.xml  53 flashcard pictures
tools/                                   pack generation and verification
bhashini/                                the Bhashini pipeline, unused so far
verification/                            review sheet and recording studio
```

---

## Traps this codebase has already hit

Each of these shipped once and was found by looking, not by a failing test.

**`org.json`'s `optString` returns the string `"null"` for a JSON null**, not
the default. An entry written `{"audio": null}` came back as `audio == "null"`,
which is non-empty, so the proof screen reported 53 of 53 entries had audio when
none did. Use `optText()` in `VerifiedContentPack`, and never write null-valued
keys into the pack.

**A lookup keyed differently from how it is queried fails silently.**
`audioPath()` looked entries up by id against a map keyed by source text, so it
always returned null and looked exactly like "we have no audio yet". Check both
directions agree.

**A comment describing intent is not the code doing it.** Three separate bugs
had a correct comment sitting beside wrong code: the Ol Chiki font sample was
written in Lepcha, the worksheet check asked for a lesson id that could never
resolve, and cold start measured from the wrong event. When a comment states an
intent, verify the code matches it.

**Advertised limits are not real limits.** Applies to APIs generally. Test the
workload you actually depend on, not the thing next to it.

**`adb shell ... > file` corrupts binary on Windows** by translating newlines.
Use `adb exec-out` when pulling a PDF or an APK.

---

## Where the project stands

| Graded item | State |
|---|---|
| Hindi to Santali, one language minimum | Built, 53 entries |
| Voice to voice under 3 seconds | Half. Hindi in works, Santali audio does not exist |
| Bilingual worksheets and flashcards | Built and verified on device |
| Offline on a 2 GB Android 9 tablet | Built and measured |
| Demo video | Not started |
| Public GitHub repository | Not pushed |

Measured: **4.23 MB release APK, 656 ms cold start, 43 MB peak RAM, 0 network
calls, 33 tests passing.**

**There is no Santali audio and it cannot be generated.** Meta's MMS covers
1,143 languages and Santali is not one of them, eSpeak has no voice for it, and
Android ships none. Bhashini is the only synthesis route and that account is
still pending. Do not suggest substituting a related Munda language such as Ho
or Mundari; they are distinct languages and it would be the same class of error
as the transliteration this project already removed once.

The remaining work is a demo video, pushing the repo, one hour with a Santali
speaker, and a physical tablet. **None of it is code.** Be sceptical of any plan
that adds features instead.

---

## Working style

- **Propose the diff and wait.** No sweeping multi-file changes unannounced.
- **Report failures with the output.** Never call partial work complete.
- Match the surrounding style. This codebase comments the *why*, not the *what*,
  and comments are dense where a decision needs defending.
- Do not touch `content/`, `bhashini/`, the pack, or the provenance rules
  without saying why first.

# Ol Saathi — task 7

## Your last block

Verified. 22 pre-flight checks, `checkRecognitionSupport` on API 33+ with an
`ACTION_GET_LANGUAGE_DETAILS` fallback for older devices, assessment prompts now
surfaced in both `ClassroomActivity` and `WorksheetPdf`. Build and tests green.

That closes the last real code gap in the four graded requirements.

---

## Read this before you start

**This is the last task worth doing in code.** After it, everything remaining is
hardware and people: get the APK onto a tablet, measure the latency, record the
demo video, push the repo. Adding more features from here makes the project
worse, not better. The previous attempt reached 54 files and satisfied none of
the four requirements, and it got there one reasonable-sounding addition at a
time.

So: do this, then stop.

---

## The task — teach the app about human review

Every string currently reads **"Machine translation · IndicTrans2"**, which is
honest but is the weakest claim we make. The single largest upgrade available to
this project is one hour from a Santali speaker, and everything is now in place
for that except the app itself.

`verification/santali-review-sheet.html` lets a reviewer mark each entry correct
or wrong and write a correction. `tools/apply_review.py` folds their answers back
into the pack. What is missing is that **the app has no idea any of this
happened.** If the review lands tomorrow, nothing on screen changes, and the
whole point was to be able to say "verified".

### What lands in the pack after a review

`apply_review.py` adds three optional fields to individual entries:

```json
{
  "source": "...",
  "target": "...",
  "reviewedBy": "Somai Murmu",
  "reviewedOn": "2026-08-27",
  "reviewVerdict": "confirmed"        // or "corrected"
}
```

and one block to provenance:

```json
"humanReview": {
  "reviewer": "Somai Murmu",
  "background": "Santali speaker, Dumka",
  "date": "2026-08-27",
  "confirmed": 41, "corrected": 9, "removed": 3, "unreviewed": 0
}
```

All of it is optional. Most entries will not have it, possibly none. **Handle
absence as the normal case**, not as an error.

### 1. A provenance state for reviewed content

`content/Translation.kt` and `content/VerifiedContentPack.kt` are normally
off-limits, but for this task you may edit them. Nobody else is touching them
now.

Add a state above `VERIFIED` in the enum:

```kotlin
HUMAN_VERIFIED("Checked by a Santali speaker"),
```

`VerifiedContentPack` sets it when an entry has a non-empty `reviewedBy` **and**
a `reviewVerdict` of `confirmed` or `corrected`. Otherwise the current logic
stands unchanged.

Colour it distinctly from the machine-translation green. This is a stronger
claim and it should look like one.

**Do not apply it pack-wide.** One reviewer checking forty entries does not
verify the other thirteen. Per entry only. That distinction is the entire value
of the exercise.

### 2. Show the reviewer's name

On the classroom screen, when an entry is `HUMAN_VERIFIED`, show the name and
date underneath in small text: *"Checked by Somai Murmu, 27 Aug 2026"*. A claim
with a name behind it is worth many times one without.

### 3. Put the review status on the Proof screen

Add to the existing content-pack section:

- Human-reviewed: N of M entries
- Reviewer name, background and date, when `provenance.humanReview` exists
- "No human review yet" when it does not, stated plainly rather than hidden

### 4. Two tests

In `app/src/test/java/app/olsaathi/content/`:

- An entry with `reviewedBy` and verdict `confirmed` resolves to
  `HUMAN_VERIFIED`
- An entry with no review fields still resolves exactly as it does today, so a
  pack with no review at all behaves identically

Use a small inline JSON fixture for these rather than the shipped pack, since
the shipped pack has no review yet and you need both branches covered.

---

## Gates

```
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:assembleDebug
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:testDebugUnitTest
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:assembleRelease
```

All pass, and the app behaves exactly as it does today when given the current
pack, which contains no review data.

## Then stop

When this is done, the code is finished for SIH26042. What is left:

1. Push the repo. There is still no git remote, and a public repository is one
   of the six graded deliverables.
2. Get the APK onto an Android 9+ tablet with 2 GB RAM.
3. Run the pre-flight screen on it and install the Hindi offline speech pack.
4. Measure the voice round trip ten times in aeroplane mode, write down the
   median, put the real number on the slide.
5. Record the demo video.
6. Find a Santali speaker and send them the review sheet.

None of those is a coding task. Do not invent one.

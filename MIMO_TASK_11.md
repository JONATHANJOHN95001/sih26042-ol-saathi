# Ol Saathi — task 11

**Read `AGENTS.md` first.** It is new since your last task and it carries the
project's one hard rule plus the four traps this codebase has already shipped.

## Do not touch any code

This task is documentation only. No `.kt`, no `.xml`, no `.json`, no `tools/`.
If you find a code bug while reading, write it down at the end of your summary
and leave it alone.

---

## What changed since these documents were last written

Three things landed and none of them is reflected anywhere:

**1. Every flashcard now has a picture.** 53 of 53 entries carry a drawable in
`app/src/main/res/drawable/ic_card_*.xml`, fetched at build time from Iconify's
public API by `tools/fetch_flashcard_icons.py`. Set is Fluent Emoji Flat, **MIT
licensed**, chosen by measuring how many icons converted cleanly to Android
VectorDrawable: fluent-emoji-flat 11/12, twemoji 9/12, openmoji 7/12, noto 4/12.
Noto was the first choice and lost.

**2. There is a new screen, `ShowClassActivity`.** The teacher turns the tablet
around and the class sees a picture, the Santali at up to 60sp, and the Hindi
underneath. No controls, no provenance, no counters. It is the only screen a
child ever looks at. Reachable from the lesson player.

**3. The numbers moved.** Release APK is now **4.23 MB** (was 4.21). **33 unit
tests**, 0 failures. Cold start still 656 ms on the Android 9 emulator.

---

## Update these three files

### `PROBLEM_STATEMENT_TRACEABILITY_MATRIX.md`

- Row 3, worksheets and flashcards: add that every card now carries a picture,
  and cite `tools/fetch_flashcard_icons.py` and the drawable path.
- Add `app/src/main/java/app/olsaathi/ui/ShowClassActivity.kt` to the supporting
  capability table, against the statement's interest in the child's experience.
- Correct the APK size everywhere it appears.
- **Leave every "not met" exactly as it is.** No Santali audio, no demo video,
  no pushed repo, nothing checked by a Santali speaker. Those are still true.

### `HANDOVER.md`

- The status table needs the flashcard pictures mentioned.
- Add one line under the flashcard section about "Show the class", because a
  teammate opening the app will not find it otherwise.
- The APK size appears more than once. Fix all of them.

### `SIH_DEMO_VIDEO_SCRIPT.md`

This is the one that matters most, because the video is a graded deliverable at
zero. Rework the script so the closing shot is **"Show the class"**: the teacher
speaks Hindi, the Santali appears, they tap the button and turn the tablet to
face the camera. That is the strongest three seconds the app has and the script
predates it entirely.

Keep the script honest. It must not imply audio exists.

---

## Rules for the writing

- Plain English, complete sentences. **Never join two clauses with a dash;**
  rewrite so the sentence flows.
- Do not add a claim these documents cannot support. Every number you write
  should be one stated above or one you verified yourself.
- Match the existing tone. These files are deliberately blunt about what does
  not work, and that is the most valuable thing in them.

## Gate

```
python tools/verify_traceability.py
```

Must exit 0 with every citation resolving. If you cite a file, check it exists
before you write the line, not after.

Quote the citation count in your summary, and list anything you found stale that
this task did not ask you to fix.

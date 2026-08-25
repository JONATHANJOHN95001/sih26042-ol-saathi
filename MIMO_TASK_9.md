# Ol Saathi — task 9

## Task 8, verified on the device

I installed the release build on the Android 9 / 2 GB emulator and walked it.

**The lesson player is right.** 1/10 through 10/10 in correct order, `l10`
correctly last rather than after `l1`, Back disabled on the first line,
`Questions →` on the tenth, then Check 1/3 through 3/3 and `Finish ✓`. Bottom
navigation works. `Lookup: 0 ms` is gone from the Teach screen. 2,000 monkey
events, zero crashes, 43 MB peak.

That is a good piece of work.

---

## The one thing that did not land

**`CheckAndProofActivity` exists and nothing can open it.** It is declared in
the manifest and never launched from anywhere. I grepped: no `startActivity`
targets it, no menu item points at it. Meanwhile `PreFlightActivity` and
`ProofActivity` are both still in the manifest and still wired to the old
buttons.

So the merge produced a third screen rather than replacing two.

This is the third time this project has shipped code nothing reaches. The
comprehension entries sat unused in the pack for days. A silent wav file sat in
the assets referenced by nothing. Now a whole activity. **When you add a screen,
the last step is opening it on a device.** Building it is not finishing it.

### Fix

1. Add an overflow menu (three dots) to the toolbar on **Teach**, **Lessons**
   and **Worksheet**, with one item: **Check & Proof**. A judge should be able
   to reach it from wherever they are.
2. Delete `PreFlightActivity.kt`, `ProofActivity.kt`, their layouts, and their
   manifest entries. `CheckAndProofActivity` replaces both, so leaving them is
   dead weight in a repo people will read.
3. Open it on a device and confirm every check still runs and every proof field
   still populates.

---

## Priority 2 — put the measured numbers on that screen

I measured these on the Android 9 / 2 GB emulator today. They are real, and the
problem statement cares about all of them, so show them rather than leave them
in a slide.

| Field | Value | Where it comes from |
|---|---|---|
| Peak memory | **43 MB** of 2,046 MB | `Debug.MemoryInfo.totalPss` at display time |
| Cold start | **468 ms** median | measure once in `Application.onCreate` to first activity resume |
| Stress | **2,000 events, 0 crashes** | static text, we ran it |
| APK size | **21.9 MB** | static text |

Read memory live rather than hardcoding it. A number that updates in front of a
judge is evidence; a number typed into a string is a claim.

Add a line explaining what it means, because the number alone does not land:

> 43 MB of 2,046 MB. No neural model is loaded at run time, so there is nothing
> to page in or out.

---

## Priority 3 — commit

There were 14 uncommitted files when I checked, and I built mid-flight and tested
a stale APK because of it. Commit in coherent chunks when a piece of work is
done, not at the end of everything.

---

## Do not touch

`content/`, `tools/`, `bhashini/`, the pack, the worksheet, and the provenance
rules. The lesson player is now correct; do not refactor it.

## Gates

```
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:assembleDebug
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:testDebugUnitTest
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:assembleRelease
```

All pass, `PreFlightActivity` and `ProofActivity` no longer exist, and **you have
personally opened Check & Proof on a running device** and watched every check
run. Say so in your summary, and say what it showed.

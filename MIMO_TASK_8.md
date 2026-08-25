# Ol Saathi — task 8

## Why there is a task 8

I said task 7 was the last one. Then I drew the screens out and found something
structural that I had missed while reading code.

**There is no lesson player.** No index, no next, no pager, nothing that walks a
lesson. Every line lives in one scrolling list on the classroom screen, so a
teacher cannot deliver a lesson line by line. She can look phrases up.

That is the difference between a phrase lookup and a teaching tool, and the
problem statement's title is *AI-Powered Vernacular **Pedagogy***. It also
changes the demo: "watch me scroll a list" is a much weaker ninety seconds than
"watch me teach a lesson, line by line, in a language I do not speak".

Open **`design/wireframes.html`** in a browser before you start. Five screens,
drawn with the real fonts and real strings from the shipped pack.

---

## Priority 1 — the lesson player

A new screen. Given a lesson id, it walks that lesson one line at a time.

**Layout**, matching screen 2 in the wireframes:

- Top bar: the lesson title in Hindi, and `2 / 10` on the right
- Progress dots under it, current one elongated
- Card: label `HINDI`, then the Hindi line, large
- Card: label `SANTALI · OL CHIKI`, then the Ol Chiki line, **larger than the
  Hindi**, in forest green, with the provenance pill beneath it
- One large circular Play button
- A row of two buttons: `← Back` outlined, `Next →` filled

**Behaviour**

- Lines come from `pack.entries(lessonId).filter { it.kind == "lesson" }`, in
  order. They are ids like `neema-dadi.l1` through `l10`, so sort numerically by
  the suffix rather than as strings, otherwise `l10` lands between `l1` and `l2`.
- `Back` is disabled on the first line.
- On the last line, `Next →` becomes `Questions →` and moves to the
  comprehension screen.
- Provenance comes from the same `Translation` the classroom uses. If an entry
  is `HUMAN_VERIFIED`, show the reviewer's name exactly as the classroom does.
- If a line is missing from the pack, show `Not in the offline pack` and let the
  teacher move on. Do not skip it silently and do not substitute anything.

**Comprehension screen** (screen 3 in the wireframes)

Same shape, but reading `kind == "check"`, headed `ASK THE CLASS`, with
`Next question` and a final `Finish`. No marking, no scores, no database. The
teacher asks aloud and the class answers aloud.

---

## Priority 2 — bottom navigation, and calm the Teach screen

Three destinations: **Teach**, **Lessons**, **Worksheet**. A teacher should never
hunt for anything mid-class.

The classroom screen currently holds twelve controls competing for attention.
Two changes:

- **Move `textLatency` off it.** "Lookup: 0 ms" is a developer string on the
  teacher's main view. It belongs on the proof screen, where the same number is
  evidence rather than clutter.
- Make the Ol Chiki the largest element on the screen. Right now it is a similar
  size to everything else. Roughly 30sp for Santali against 22sp Hindi, and give
  it room to breathe.

Keep the type input. It is how the demo works when speech is unavailable, which
is currently always.

---

## Priority 3 — merge Pre-Flight and Live Proof

One screen called **Check & Proof**, reachable from the overflow menu rather
than the lesson list. A judge should not have to find two separate screens to
see the same argument.

Keep every existing check and every existing proof field. Add one line:
`Human-reviewed: N of M entries`, reading the `reviewedBy` fields the pack
already supports.

---

## Do not break

- The provenance rules. N1 stands: a miss shows `Not in the offline pack`, never
  a guess, never a nearest match.
- The font loading. Both typefaces are applied with `Typeface.createFromAsset`
  and **throw** rather than falling back. Any new view showing Ol Chiki must set
  that typeface, or it renders as empty boxes.
- The worksheet. It works and is verified; do not refactor it.
- Anything under `content/`, `tools/`, `bhashini/` or the pack.

## Gates

```
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:assembleDebug
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:testDebugUnitTest
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:assembleRelease
```

All pass, and a person can open Lessons, pick "नीमा और दादी", and walk all ten
lines and all three questions using only Back and Next.

## Scope

These three, then stop again. The remaining gaps after this are the demo video,
pushing the repo, a tablet, and a Santali speaker. None of those is code, and
the previous attempt reached 54 files by never accepting that.

# Ol Saathi — task 10

## Task 9, verified on the device

`PreFlightActivity` and `ProofActivity` are gone. `CheckAndProofActivity` is
reachable from the overflow on all three screens. I opened it on the Android 9
emulator, ran the checks, and every section populated: 6 of 7 passing, pack info,
provenance chip, script sample rendering as real Ol Chiki, audio, offline,
pre-flight, build, performance.

One correction to something I said earlier: I reported the screen came up empty.
That was my mistake, not yours. I tapped at y=388 and the button is at y=490.

---

## The one real bug

The Performance section reads:

```
Cold start: 48000 ms
```

Forty-eight seconds. A judge reading that concludes the app takes most of a
minute to start. The real figure is **468 ms**, which I measured five times with
`am start -W`.

The cause is in `CheckAndProofActivity`:

```kotlin
activityResumedMs = System.currentTimeMillis()   // set in THIS activity's onCreate
...
val coldStartMs = activityResumedMs - app.coldStartTimestampMs
```

That measures the time from app launch until somebody opened the Check & Proof
screen. I had been using the app for about forty-eight seconds first.

The KDoc on `coldStartTimestampMs` already says the right thing:

> *Measured as Application.onCreate to first Activity.onResume.*

The comment describes the correct behaviour and the code does something else.
That is the third time this exact shape has appeared: the Lepcha sample had the
right string in a comment beside the wrong escapes, and the worksheet check
asked for a lesson id that could never resolve. **When a comment states an
intent, the next step is checking the code does it.**

### Fix

Record it once, in `OlSaathiApplication`, and never update it:

```kotlin
registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
    override fun onActivityResumed(activity: Activity) {
        if (coldStartMs == 0L) {
            coldStartMs = System.currentTimeMillis() - coldStartTimestampMs
        }
    }
    // other callbacks empty
})
```

`CheckAndProofActivity` then reads `app.coldStartMs` rather than computing it.
Expect roughly 400 to 600 ms on the emulator. **If it reads over a second,
something is still wrong; do not ship the number until it looks like a cold
start.**

---

## Priority 2 — the Latency section will be empty in front of a judge

It currently says `No measurements yet`, and it will keep saying that unless
somebody has used the Teach screen in that session. `recordLatency` is called
from exactly one place, `ClassroomActivity:172`.

The lesson player does lookups too and records nothing. Add the same call there,
so walking a lesson populates the figure. Then a judge who watches the demo and
then opens Check & Proof sees real numbers from the thing they just watched,
which is far stronger than an empty field.

---

## Priority 3 — commit, and check the whole screen once more

After fixing both, open Check & Proof on a device again and read every line.
Report what the Performance section says. If cold start still looks wrong, say
so rather than shipping it.

## Do not touch

`content/`, `tools/`, `bhashini/`, the pack, the worksheet, the lesson player,
the provenance rules.

## Gates

```
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:assembleDebug
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:testDebugUnitTest
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:assembleRelease
```

All pass, and **you have read the Performance section on a running device** and
it shows a cold start under one second and a real latency figure after walking a
lesson. Quote both numbers in your summary.

## After this

This really is the end of the code. What remains is the demo video, pushing the
repo, a tablet, and a Santali speaker. None of it is a coding task.

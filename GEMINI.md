# SIH — 24 hour build

Project rules for any agent working in this repo. These override general defaults.

## The clock is the constraint

- 24 hours, and a judged demo at the end. **A working vertical slice beats a
  complete architecture.** If a choice costs more than ~30 minutes and does not
  move the demo forward, take the cheaper path and note the debt.
- Never start a refactor unless something is broken. Never reorganise folders.
- Prefer boring, known-good libraries over the best available one.

## Offline-first — venue wifi will fail

- Assume the network drops without warning. **Never introduce a dependency that
  must be fetched at runtime** if a local alternative exists.
- Before adding a package, check it is already in the local npm/pip cache.
- If the network is down, fall back to the local LM Studio model rather than
  waiting. Say so explicitly rather than silently stalling.

## Stack — already decided, do not relitigate

- **Supabase** (hosted) for database + auth. **Neon** for scratch branches.
  **Not Firebase**, even though the Firebase plugin is installed.
- Never run `supabase start` — Docker will not fit on this machine.
- Models: Gemini on the user's own key. Nothing billed, ever.

## Definition of done

A task is done when it **runs and has been observed running** — browser opened,
console checked, output confirmed. "Should work" is not done. If something
fails, say so with the error, do not paper over it.

## The demo is scored

SIH judges the presentation, not just the build. Therefore:

- Keep a **`DEMO.md`** updated with the exact click-path a judge will follow.
  If a step breaks, that is a P0 bug regardless of how minor it looks.
- **Record the working demo as soon as it works**, not at the end. A recording
  of a working build beats a live demo that breaks on stage.
- The deck and the app must share one visual identity. Define colour, spacing
  and type tokens once in this repo and reference them everywhere — in Stitch
  prompts, in app CSS, and in the deck.

## Hygiene

- Commit every time something works. Small commits, plain messages.
- Do not add narration comments to code that has none.

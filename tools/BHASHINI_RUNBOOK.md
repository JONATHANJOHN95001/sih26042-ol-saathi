# When the Bhashini keys arrive

**⚠️ This file is a historical copy. The authoritative runbook is now
`bhashini/RUNBOOK.md`.** Everything below has been superseded.

The safe generator lives at `bhashini/build_pack.mjs` and writes into
`bhashini/out/`. `tools/build_pack.mjs` is now a stub that refuses to run. It
used to write straight into the live app assets, which was unsafe for a run
that might crash, return partial results, or produce worse translations than
the IndicTrans2 pack already shipped.

---

See `bhashini/RUNBOOK.md` for the current instructions.

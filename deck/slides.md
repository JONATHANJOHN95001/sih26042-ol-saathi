---
theme: seriph
background: '#0b1020'
title: PROJECT NAME
class: text-center
transition: slide-left
mdc: true
---

# PROJECT NAME

One sentence. What it does, for whom.

<div class="pt-8 text-sm opacity-60">
Smart India Hackathon · Problem Statement #XXXX · Team NAME
</div>

---
layout: statement
---

# THE NUMBER

<div class="text-xl opacity-70 mt-4">
The single statistic that makes the problem undeniable. Cite the source.
</div>

---

# The problem

- Who is hurt, concretely. Name the person, not the category.
- What they do today, and why it fails.
- What it costs — time, money, or lives.

::right::

> Judges reward understanding the problem over cleverness in the solution.
> Spend a real slide here.

---
layout: two-cols
---

# Our solution

One sentence a non-technical judge repeats correctly afterwards.

Then three bullets, no more:

- The core mechanism
- The thing nobody else does
- Why it works offline / at scale / in a village

::right::

<div class="text-6xl pt-20 opacity-20">DEMO</div>

---
layout: center
class: text-center
---

# Live demo

<div class="text-sm opacity-60 mt-4">
Recording as backup. Never demo live without one.
</div>

---

# How it works

```mermaid {scale: 0.8}
graph LR
  A[Citizen] --> B[React PWA]
  B --> C[Async store layer]
  C --> D[(Supabase)]
  C -.offline.-> E[(localStorage)]
```

The store layer is one file. Swapping localStorage for Supabase changes nothing else.

---

# Why this scales

| Concern | Our answer |
| --- | --- |
| 1M users | Single data interface, swap backend, zero component changes |
| No connectivity | PWA with service worker; works fully offline |
| Low-end devices | No UI framework bloat, 6 dependencies total |
| Adoption | Works in the browser. Nothing to install. |

---
layout: statement
---

# Impact

<div class="text-lg opacity-70 mt-4">
If this ships, who is measurably better off, and by how much?
</div>

---
layout: end
---

# Thank you

github.com/USERNAME/REPO

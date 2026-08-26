# Bolt prompt — Ol Saathi demo site

Paste everything inside the fence into Bolt in one go. It embeds real content
from `app/src/main/assets/pack/pack.sat.json`, so the site is alive on the first
generation rather than an empty shell.

Purpose: a judge who will not install an APK can experience the app in five
seconds, and the page doubles as the GitHub landing page and the pitch backdrop.

---

```
Build a single-page site called "Ol Saathi" using React, Vite and Tailwind.
Everything client-side. No backend, no database, no auth, no API calls.
Build the whole thing in one pass, in the section order given below.

WHAT THIS IS
Ol Saathi is a real offline Android app for Smart India Hackathon problem
SIH26042, Government of Jharkhand. A Hindi-speaking teacher uses it to deliver
primary school lessons in Santali, a tribal language written in the Ol Chiki
script, on a cheap tablet with no internet.

This site is NOT the app. It is a companion page that lets someone try the core
idea in a browser and then download the real APK. Say that plainly on the page.
Never imply the website is the product.

FONTS — do this first or the whole page breaks
Santali is written in Ol Chiki, which no device has by default. Load from
Google Fonts and apply explicitly:
  Noto Sans Ol Chiki    -> every Santali string
  Noto Sans Devanagari  -> every Hindi string
If these are missing, every Santali character renders as an empty box and the
page is worthless. Give each a real fallback stack.

────────────────────────────────────────────────────────────────────
SECTION 1 — HERO
────────────────────────────────────────────────────────────────────
Headline:  "The teacher speaks Hindi. The child speaks Santali."
Subhead:   "Ol Saathi closes that gap on a 2 GB tablet with no internet."

Below it, four stat tiles with these exact measured figures:
  4.21 MB     app size
  656 ms      cold start on Android 9
  1 ms        translation lookup
  0           network calls, ever

Two buttons: "Try it below" (scrolls to section 2) and "Download the APK"
(placeholder href="#", the team fills it in).

Small line underneath: "SIH26042 · Government of Jharkhand · Team INNOV8"

────────────────────────────────────────────────────────────────────
SECTION 2 — TRY IT   ← this is the centrepiece, build it carefully
────────────────────────────────────────────────────────────────────
A working phrase lookup that mirrors exactly what the Android app does.

Layout:
  - A search input: "Type or pick a Hindi phrase"
  - Below it, clickable chips for the sample phrases (data below)
  - A result card that updates instantly

The result card shows, top to bottom:
  - The Hindi source, in Devanagari, medium size
  - The Santali target, in Ol Chiki, LARGE, visually the hero of the card
  - The English gloss, small and grey, so a judge who reads neither script
    can still follow
  - Two small chips side by side:
      a NIPUN domain chip (e.g. "Oral Language Development")
      a provenance chip reading "Machine translation · IndicTrans2"

LOOKUP BEHAVIOUR — this is the important part
Match on the Hindi text after trimming whitespace and stripping a trailing
danda (।), full stop, question mark or exclamation mark. Exact match only.

If there is no match, DO NOT guess, DO NOT show the closest result, DO NOT
fall back to anything. Show a distinctly styled "no result" card:

    Not in the offline pack
    Ol Saathi shows nothing rather than inventing a translation.
    A teacher who cannot read Ol Chiki could not tell a wrong answer
    from a right one. The child could.

Style that card as deliberate and confident, not as an error. Amber or neutral,
never red. This is a feature being demonstrated, not a failure.

Add a second row of chips, clearly labelled "Now try one we do not have:"
containing these three phrases, which are deliberately absent from the data:
  "मुझे भूख लगी है।"
  "बारिश हो रही है।"
  "कल छुट्टी है।"
Clicking one triggers the no-result card. This is the memorable moment of the
whole page, so make it feel intentional.

────────────────────────────────────────────────────────────────────
SECTION 3 — WHERE THE AI ACTUALLY IS
────────────────────────────────────────────────────────────────────
Heading: "The model runs before the tablet ever sees it"

Paragraph: "The problem statement asks for an NLP engine, 2 GB of RAM, and full
offline operation. Those pull against each other. A 1.1 billion parameter
translation model cannot run in 2 GB. The distilled 200 million version can, and
when we tested it, it emitted Arabic characters and rendered grandmother as
mother. So the model runs at build time on a laptop, and the tablet ships the
finished, checkable result. Google Translate's offline packs work the same way."

Then a two-column comparison table:

  Row heading            | On-device model      | Build-time model (chosen)
  Size we can afford     | 200M, distilled      | 1.1B, full
  Output quality         | Emitted Arabic script| Clean Ol Chiki
  RAM while running      | about 1 GB of 2 GB   | 43 MB
  Lookup speed           | 2 to 4 seconds       | 1 ms
  Can a human fix it     | No                   | Yes, before it ships

Highlight the chosen column.

Below the table, three small cards naming the actual models:
  "IndicTrans2 1B" / "AI4Bharat, 1,116M parameters. Produced every Santali
   line, English to sat_Olck."
  "IndicTrans2 indic-en 1B" / "1,023M parameters, run backwards to score every
   translation. Median round-trip similarity 0.484."
  "Android on-device ASR" / "Neural Hindi speech recognition, running on the
   tablet in real time with the network off."

────────────────────────────────────────────────────────────────────
SECTION 4 — WHAT IS REAL AND WHAT IS NOT
────────────────────────────────────────────────────────────────────
Heading: "Where the project actually stands"

A status table. Use a clear tick, a half symbol, and a cross. Do not dress up
the incomplete rows.

  Hindi to Santali, 53 entries              | Built
  Bilingual worksheets and flashcards       | Built, verified on device
  Offline on a 2 GB Android 9 tablet        | Built, measured
  Voice to voice under three seconds        | Half. Hindi in works, lookup is
                                              1 ms, Santali audio missing
  Synthesised Santali audio                 | Not met
  Checked by a Santali speaker              | Not yet

Then a callout box, styled calm rather than alarming:

  "Why there is no Santali audio.
   Meta's MMS model covers 1,143 languages and Santali is not one of them.
   eSpeak has no Santali voice. Android ships none. Bhashini is the only
   synthesis route and that account is still pending. So we built the recording
   tools instead, and the fix is twenty minutes with one Santali speaker rather
   than a model we could not find."

A judge trusts a team that says this before being asked. Make the section feel
like confidence, not apology.

────────────────────────────────────────────────────────────────────
SECTION 5 — THE PEDAGOGY
────────────────────────────────────────────────────────────────────
Heading: "Why the mother tongue first"

Four short cards:

  "The policy"      | "NEP 2020 mandates the home language as medium of
                      instruction through Grade 5. NIPUN Bharat sets the
                      deadline: foundational literacy and numeracy by Grade 3."
  "The research"    | "Literacy learned in the first language transfers to the
                      second. Teaching foundations in Santali is not a detour
                      away from Hindi, it is the faster route to it."
  "The user"        | "The teacher, not the child. Ol Saathi never instructs a
                      six-year-old. It equips the adult already standing in the
                      room, which is what one tablet per school allows."
  "The script"      | "Ol Chiki, designed for Santali by Pandit Raghunath Murmu
                      in 1925 and phonemic by design. Not Santali spelled out in
                      Devanagari. That is a literacy decision and a dignity
                      decision at once."

────────────────────────────────────────────────────────────────────
SECTION 6 — FOOTER
────────────────────────────────────────────────────────────────────
"Ol Saathi · SIH26042 · Team INNOV8 · JAIN Deemed-to-be University"
Buttons: "Download the APK" and "View the source" (both href="#" placeholders).

────────────────────────────────────────────────────────────────────
DATA — use exactly this, it is real output from the shipped app
────────────────────────────────────────────────────────────────────
const ENTRIES = [
 {hi:"नमस्ते बच्चों।", ol:"ᱦᱚᱞᱳ ᱜᱤᱫᱽᱨᱟᱹᱠᱚ ᱾", en:"Hello children.", domain:"Classroom Routine"},
 {hi:"सब बैठ जाओ।", ol:"ᱡᱚᱛᱚ ᱦᱚᱲ ᱠᱚ ᱫᱩᱲᱩᱵ ᱮᱱᱟ ᱾", en:"Everyone sit down.", domain:"Classroom Routine"},
 {hi:"मेरे पीछे दोहराओ।", ol:"ᱤᱧ ᱛᱟᱭᱚᱢ ᱵᱟᱨ ᱫᱷᱟᱣ ᱢᱮ ᱾", en:"Repeat after me.", domain:"Oral Language Development"},
 {hi:"ध्यान से सुनो।", ol:"ᱥᱚᱱᱛᱚᱨ ᱠᱟᱛᱮ ᱟᱸᱡᱚᱢ ᱢᱮ ᱾", en:"Listen carefully.", domain:"Oral Language Development"},
 {hi:"अपना नाम लिखो।", ol:"ᱟᱢᱟᱜ ᱧᱩᱛᱩᱢ ᱚᱞ ᱢᱮ ᱾", en:"Write your name.", domain:"Writing"},
 {hi:"बहुत अच्छा!", ol:"ᱟᱹᱰᱤ ᱱᱟᱯᱟᱭ ᱾", en:"Very good!", domain:"Classroom Routine"},
 {hi:"यह क्या है?", ol:"ᱱᱚᱶᱟ ᱫᱚ ᱪᱮᱫ ᱠᱟᱱᱟ", en:"What is this?", domain:"Oral Language Development"},
 {hi:"तुम्हारा नाम क्या है?", ol:"ᱟᱢᱟᱜ ᱧᱩᱛᱩᱢ ᱫᱚ ᱪᱮᱫ", en:"What is your name?", domain:"Oral Language Development"},
 {hi:"एक से दस तक गिनो।", ol:"ᱢᱤᱫ ᱠᱷᱚᱱ ᱜᱮᱞ ᱫᱷᱟᱹᱵᱤᱡ ᱞᱮᱠᱷᱟ ᱢᱮ ᱾", en:"Count from one to ten.", domain:"Number Sense"},
 {hi:"दो और तीन कितने होते हैं?", ol:"ᱵᱟᱨ ᱥᱮᱞᱮᱫ ᱯᱮ ᱫᱚ ᱪᱮᱫ", en:"What is two plus three?", domain:"Number Operations"},
 {hi:"यह अक्षर पढ़ो।", ol:"ᱱᱚᱶᱟ ᱪᱤᱴᱷᱤ ᱫᱚ ᱯᱟᱲᱦᱟᱣ ᱢᱮ ᱾", en:"Read this letter.", domain:"Decoding"},
 {hi:"कहानी सुनो।", ol:"ᱠᱟᱹᱦᱱᱤ ᱫᱚ ᱟᱸᱡᱚᱢ ᱢᱮ ᱾", en:"Listen to the story.", domain:"Oral Language Development"},
 {hi:"पानी पी लो।", ol:"ᱱᱟᱥᱮ ᱫᱟᱜ ᱧᱩᱭ ᱢᱮ ᱾", en:"Drink some water.", domain:"Classroom Routine"},
 {hi:"नीमा दोपहर में दो बजे स्कूल से लौटती है।", ol:"ᱱᱤᱢᱟ ᱫᱚ ᱛᱤᱠᱤᱱ ᱵᱟᱨ ᱴᱟᱲᱟᱝ ᱨᱮ ᱵᱤᱨᱫᱟᱹᱜᱟᱲ ᱠᱷᱚᱱ ᱚᱲᱟᱜ ᱮ ᱦᱤᱡᱩᱜᱼᱟ ᱾", en:"Neema comes home from school at two in the afternoon.", domain:"Reading Comprehension"},
 {hi:"उनके घुटनों में दर्द रहता है।", ol:"ᱩᱱᱤᱭᱟᱜ ᱜᱩᱱᱴᱷᱮ ᱨᱮ ᱦᱟᱥᱩ ᱞᱮᱱᱟ ᱾", en:"Her knees hurt.", domain:"Reading Comprehension"},
 {hi:"इस समय घर पर कौन होता है?", ol:"ᱚᱱᱟ ᱚᱠᱛᱚ ᱚᱲᱟᱜ ᱨᱮ ᱚᱠᱚᱭ ᱢᱮᱱᱟᱜ - ᱟ", en:"Who is at home at that time?", domain:"Oral Language Development"}
];

Show the first eight as chips in section 2. All sixteen are searchable.

────────────────────────────────────────────────────────────────────
DESIGN
────────────────────────────────────────────────────────────────────
Serious and calm. This is a government education project, not a startup. No
gradient-on-gradient, no floating 3D mockups, no marketing superlatives.

  - Deep indigo primary, warm off-white background, one amber accent
  - Ol Chiki text always larger than the Hindi beside it, because it is the
    output being demonstrated and it is unfamiliar to every reader
  - Generous whitespace, strong typographic hierarchy, body text 16px minimum
  - Light and dark mode, both explicitly defined
  - Fully responsive, works on a phone, since a judge may open it on one
  - Subtle scroll-in transitions only. Nothing bounces.

WRITING STYLE FOR ALL COPY ON THE PAGE
Plain, direct English in complete sentences. Never join two clauses with a dash;
rewrite so the sentence flows. No exclamation marks outside the quoted classroom
phrases. Never claim anything the status table contradicts.

Deliver a finished, working page. Every button and every interaction does what
it says.
```

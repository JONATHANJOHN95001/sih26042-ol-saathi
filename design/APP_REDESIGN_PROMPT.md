# App redesign prompt — for Dyad, Emergent, Bolt or v0

Paste everything inside the fence. It works unchanged in any React builder.

The output is a **design prototype**, not the shipping app. Its job is to settle
the look, then hand over a token list that ports into the Kotlin app's
`colors.xml` and `dimens.xml`.

Related: `BOLT_PROMPT.md` is a different thing, a judge-facing demo site.

---

```
Build "Ol Saathi", a mobile-first React app. Frontend only. No backend, no
database, no authentication, no API calls. All data is embedded in the code and
all state is local. Build every screen listed below in one pass.

WHAT THIS IS AND WHY THE DESIGN MATTERS
Ol Saathi helps a Hindi-speaking teacher in a rural Jharkhand primary school
deliver lessons in Santali, a tribal language written in the Ol Chiki script.
It runs offline on a cheap tablet.

This build is a HIGH-FIDELITY DESIGN PROTOTYPE. Its output will be ported to a
native Android app, so the design system must be explicit and portable: name
every colour, every type size and every spacing value as a token.

THE TWO USERS
1. The teacher operates the tablet. An adult, possibly in their fifties, in a
   noisy classroom, sometimes in bright sunlight, often in a hurry. Cannot read
   Ol Chiki. Needs large targets, high contrast, and zero ambiguity.
2. The child is six years old and NEVER touches the device. They see it when the
   teacher turns it around. They need warmth, colour, pictures and very large
   script, not dense text.

Design for the teacher's hands and the child's eyes.

VISUAL IDENTITY — this is the part that must not be generic
Ground the design in Sohrai and Khovar, the traditional mural painting of
Jharkhand: flat geometric animals and plants, bold outlines, earthy pigments on
lime-washed cream walls. Use that as decoration and illustration language, not
as a texture overlay. Modern layout and spacing, traditional motifs.

Do NOT use: purple-to-blue gradients, glassmorphism, floating 3D phone mockups,
neon accents, or generic edtech mascots with big cartoon eyes.

DESIGN TOKENS — define these as CSS variables and list them in a comment block
so they can be copied into Android colors.xml and dimens.xml

Colour
  --cream        #FBF6EC   app background, the lime-washed wall
  --terracotta   #B3502D   primary actions
  --ochre        #E0A226   highlights, progress, the child-facing accent
  --indigo       #2B3A67   Ol Chiki text and headers
  --forest       #3E7A5E   success and confirmation
  --charcoal     #2A2420   body text
  --clay         #EFE3D0   card surfaces and dividers
Dark mode: charcoal background, cream text, keep terracotta and ochre.

Type scale
  Ol Chiki display   40px    the "Show the class" view
  Ol Chiki card      32px    result and lesson cards
  Hindi              22px
  Heading            20px
  Body               16px    never smaller for the teacher
  Gloss and label    13px
Ol Chiki is ALWAYS visually larger than the Hindi beside it. It is the output
being demonstrated and it is unfamiliar to everyone in the room.

Fonts, load from Google Fonts and apply explicitly:
  Noto Sans Ol Chiki    every Santali string
  Noto Sans Devanagari  every Hindi string
  Baloo 2 or similar warm rounded face for headings
If Ol Chiki is missing, every Santali character renders as an empty box.

Spacing and targets
  Base unit 8px. Minimum touch target 56px, larger than the usual 48, because
  the teacher is standing, hurried, and may have wet or chalky hands.
  Card radius 20px. Soft shadows only, no harsh borders.

────────────────────────────────────────────────────────────
SCREEN 1 — HOME
────────────────────────────────────────────────────────────
Warm greeting: "Good morning" with the date.
A small Sohrai-style bird illustration, flat, two colours, geometric. This is
the app's companion character. Reuse it at small size elsewhere. Give it no
face-with-huge-eyes treatment; keep it folk-art, not Pixar.

Three large tappable cards, each with its own illustration and colour:
  "Teach"      terracotta   "Say something in Hindi, show it in Santali"
  "Lessons"    indigo       "Walk a lesson line by line"
  "Materials"  ochre        "Print worksheets and flashcards"

Below: a progress strip, "12 of 53 phrases used this week", as a rounded bar.

────────────────────────────────────────────────────────────
SCREEN 2 — TEACH   ← the hero screen
────────────────────────────────────────────────────────────
A large circular microphone button, terracotta, at least 120px across, centred
low on the screen where a thumb reaches.

States, all visually distinct:
  Idle       "Tap and speak in Hindi"
  Listening  the button pulses, an animated waveform of soft bars appears
  Thinking   brief
  Result     the card below appears

The result card, top to bottom:
  Hindi source, Devanagari, 22px
  A thin ochre divider
  Santali target, Ol Chiki, 32px, indigo, the visual hero of the card
  English gloss, 13px, muted
  A row: a NIPUN domain pill, a provenance pill reading
         "Machine translation, IndicTrans2", a circular play button
  A wide ochre button: "Show the class"

Also give a text input as a fallback, since a demo may happen in a room too
noisy for the microphone.

NO-RESULT BEHAVIOUR, this is important
If the phrase is not in the data, never guess and never show a near match.
Show a distinct card, amber and calm, never red:
    "Not in the offline pack.
     Ol Saathi shows nothing rather than inventing a translation. A teacher who
     cannot read Ol Chiki could not tell a wrong answer from a right one. The
     child could."
Style it as a deliberate feature, not an error.

────────────────────────────────────────────────────────────
SCREEN 3 — SHOW THE CLASS   ← the child-facing view
────────────────────────────────────────────────────────────
Full screen, no chrome, landscape-friendly. The teacher turns the tablet around.

  A large Sohrai-style illustration relevant to the phrase, top half
  The Ol Chiki at 40px or larger, centred, indigo on cream
  The Hindi below it, smaller
  Nothing else. No buttons except a small X in a corner and a play button.

This is the only screen a child ever looks at. It should feel like a picture
book page, not an app.

────────────────────────────────────────────────────────────
SCREEN 4 — LESSONS AND LESSON PLAYER
────────────────────────────────────────────────────────────
Lesson list: illustrated cards, each with a title, a line count, and a progress
ring showing how far through the teacher got.

Lesson player: one line per card, swipeable left and right, with large arrow
buttons as well since swiping is not obvious to every teacher. Progress dots
across the top. Same card anatomy as the Teach result. A "Show the class"
button on every line.

────────────────────────────────────────────────────────────
SCREEN 5 — MATERIALS
────────────────────────────────────────────────────────────
Two large preview cards, not bare buttons:
  "Worksheet"   a small paper-preview thumbnail, "3 pages, Hindi and Santali"
  "Flashcards"  a small grid thumbnail, "7 pages, 6 cards each, cut and keep"
A lesson picker above them. Share and Print buttons below.

────────────────────────────────────────────────────────────
SCREEN 6 — CHECK AND PROOF
────────────────────────────────────────────────────────────
Deliberately plainer than the rest. This screen is for a judge or a technician,
not a child. A clean list of checks with tick and cross icons, monospace values,
and an honest status table. Do not decorate it.

Rows: content pack 53 entries, Ol Chiki font present, NIPUN alignment 7 domains,
Santali audio 0 of 53, network calls 0, cold start 656 ms, app size 4.21 MB.

────────────────────────────────────────────────────────────
NAVIGATION
────────────────────────────────────────────────────────────
A bottom bar with four items: Home, Teach, Lessons, Materials. Icons plus
labels, never icons alone. The active item gets a filled ochre pill behind it.

────────────────────────────────────────────────────────────
DATA — real output from the shipped app, use exactly this
────────────────────────────────────────────────────────────
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
 {hi:"पानी पी लो।", ol:"ᱱᱟᱥᱮ ᱫᱟᱜ ᱧᱩᱭ ᱢᱮ ᱾", en:"Drink some water.", domain:"Classroom Routine"}
];

const LESSON = {
 id:"neema-dadi", title:"Neema and her grandmother", lines:[
 {hi:"नीमा दोपहर में दो बजे स्कूल से लौटती है।", ol:"ᱱᱤᱢᱟ ᱫᱚ ᱛᱤᱠᱤᱱ ᱵᱟᱨ ᱴᱟᱲᱟᱝ ᱨᱮ ᱵᱤᱨᱫᱟᱹᱜᱟᱲ ᱠᱷᱚᱱ ᱚᱲᱟᱜ ᱮ ᱦᱤᱡᱩᱜᱼᱟ ᱾", en:"Neema comes home from school at two in the afternoon."},
 {hi:"उनके घुटनों में दर्द रहता है।", ol:"ᱩᱱᱤᱭᱟᱜ ᱜᱩᱱᱴᱷᱮ ᱨᱮ ᱦᱟᱥᱩ ᱞᱮᱱᱟ ᱾", en:"Her knees hurt."},
 {hi:"इस समय घर पर कौन होता है?", ol:"ᱚᱱᱟ ᱚᱠᱛᱚ ᱚᱲᱟᱜ ᱨᱮ ᱚᱠᱚᱭ ᱢᱮᱱᱟᱜ - ᱟ", en:"Who is at home at that time?"}
]};

Absent phrases for demonstrating the no-result card:
 "मुझे भूख लगी है।"  "बारिश हो रही है।"  "कल छुट्टी है।"

────────────────────────────────────────────────────────────
PRIORITIES IF ANYTHING HAS TO BE TRADED OFF
────────────────────────────────────────────────────────────
1. Ol Chiki must render as actual script, never as empty boxes.
2. Build "Show the class" properly. It is the only screen a child ever sees.
3. Keep the no-result card. Never add a fuzzy match or a "did you mean".
4. No gamification. No points, streaks, badges or confetti. A teacher opens
   this forty times a day and delight that charms once soon irritates.
5. Illustrations flat and geometric in Sohrai style. No 3D, no stock clipart,
   no emoji as icons.
6. Target is a low-end 8-inch Android tablet in portrait, bright classroom.
   High contrast. No light grey text on cream.
7. No marketing copy. Check and Proof must keep showing "Santali audio: 0 of 53"
   because that is true.
8. Frontend only. No backend, no login, no analytics, no network calls.

────────────────────────────────────────────────────────────
MOTION AND TONE
────────────────────────────────────────────────────────────
Motion is gentle and purposeful: cards fade and rise 8px, the mic pulses while
listening, the progress ring fills. Nothing bounces, spins or confettis.

All interface copy in plain, complete English sentences. Never join two clauses
with a dash; rewrite so the sentence flows.

Deliver a finished, working prototype at phone and tablet widths. Every button
does what it says. End by printing the full design token list so it can be
copied into the Android project.
```

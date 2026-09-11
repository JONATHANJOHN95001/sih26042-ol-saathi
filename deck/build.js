const pptxgen = require('pptxgenjs');

const pres = new pptxgen();
pres.layout = 'LAYOUT_WIDE';           // 13.3 x 7.5
pres.author = 'Team INNOV8';
pres.title = 'Ol Saathi — SIH26042';

// Jharkhand: forest green for the land, terracotta for its red soil,
// ochre as the single sharp accent.
const FOREST = '1B4332';
const FOREST_MID = '2D6A4F';
const TERRA = 'B85042';
const OCHRE = 'E9A03B';
const INK = '1C1C1C';
const MUTED = '6B6B6B';
const LINE = 'DCDCDC';
const WHITE = 'FFFFFF';

const H = 'Cambria';
const B = 'Calibri';

const W = 13.3;

/** Fresh object every time: pptxgenjs mutates option objects in place. */
const shadow = () => ({ type: 'outer', angle: 90, blur: 12, offset: 3, color: '000000', opacity: 0.12 });

function titleBlock(s, kicker, title, dark) {
  if (kicker) {
    s.addText(kicker.toUpperCase(), {
      x: 0.7, y: 0.45, w: 11.9, h: 0.3, margin: 0,
      fontFace: B, fontSize: 12, bold: true, charSpacing: 2,
      color: dark ? OCHRE : TERRA,
    });
  }
  s.addText(title, {
    x: 0.7, y: kicker ? 0.78 : 0.6, w: 11.9, h: 0.85, margin: 0,
    fontFace: H, fontSize: 34, bold: true,
    color: dark ? WHITE : INK,
  });
}

/** Numbered circle used as the deck's one repeating motif. */
function badge(s, n, x, y, size, fill) {
  s.addShape(pres.ShapeType.ellipse, {
    x, y, w: size, h: size, fill: { color: fill || TERRA }, shadow: shadow(),
  });
  s.addText(String(n), {
    x, y, w: size, h: size, margin: 0,
    align: 'center', valign: 'middle',
    fontFace: H, fontSize: size > 0.7 ? 20 : 15, bold: true, color: WHITE,
  });
}

function card(s, x, y, w, h, fill) {
  s.addShape(pres.ShapeType.roundRect, {
    x, y, w, h, rectRadius: 0.08,
    fill: { color: fill || 'F7F7F5' },
    line: { color: LINE, width: 0.75 },
  });
}

/* ─────────────────────────── 1 · title ─────────────────────────── */
{
  const s = pres.addSlide();
  s.background = { color: FOREST };

  s.addShape(pres.ShapeType.ellipse, { x: 10.3, y: -1.5, w: 5.2, h: 5.2, fill: { color: FOREST_MID } });
  s.addShape(pres.ShapeType.ellipse, { x: 11.9, y: 4.9, w: 2.6, h: 2.6, fill: { color: TERRA } });

  s.addText('Ol Saathi', {
    x: 0.9, y: 2.0, w: 9, h: 1.3, margin: 0,
    fontFace: H, fontSize: 66, bold: true, color: WHITE,
  });
  s.addText('ओल साथी', {
    x: 0.95, y: 3.25, w: 9, h: 0.6, margin: 0,
    fontFace: B, fontSize: 26, color: OCHRE,
  });
  s.addText('A teacher speaks Hindi. A child hears her mother tongue. With no internet.', {
    x: 0.95, y: 4.0, w: 7.2, h: 0.9, margin: 0,
    fontFace: B, fontSize: 17, color: 'CFE0D6', lineSpacing: 26,
  });

  // The deck is about Ol Chiki, so show Ol Chiki. Rendered from the same
  // font file that ships in the APK. Reads "ol chiki", verified per glyph.
  s.addImage({ path: 'olchiki-name.png', x: 8.35, y: 3.5, w: 3.9, h: 1.05 });

  s.addShape(pres.ShapeType.line, { x: 0.95, y: 5.15, w: 3.2, h: 0, line: { color: OCHRE, width: 2 } });

  s.addText([
    { text: 'SIH26042', options: { bold: true, color: WHITE } },
    { text: '   Government of Jharkhand, Dept of Higher & Technical Education', options: { color: 'A8C3B4' } },
  ], { x: 0.95, y: 5.45, w: 9.5, h: 0.32, margin: 0, fontFace: B, fontSize: 13 });
  s.addText('Team INNOV8   ·   JAIN Deemed-to-be University', {
    x: 0.95, y: 5.82, w: 9.5, h: 0.32, margin: 0, fontFace: B, fontSize: 13, color: 'A8C3B4',
  });

  s.addNotes('Ol Saathi. "Ol" is Santali for write, and opens the name of Ol Chiki, the script Raghunath Murmu created for Santali in 1925. "Saathi" is Hindi for companion. Half Santali, half Hindi, like the product.');
}

/* ─────────────────────────── 2 · problem ─────────────────────────── */
{
  const s = pres.addSlide();
  titleBlock(s, 'The problem', 'PALASH works. It cannot scale.');

  const stats = [
    ['5,000+', 'tribal-area primary schools where children are taught in a language they do not speak at home'],
    ['3', 'languages the state needs: Ho, Mundari and Santali, all Austroasiatic, all low-resource'],
    ['~0', 'teachers posted there who speak them. Almost every one was trained in Hindi'],
  ];
  stats.forEach(([n, label], i) => {
    const x = 0.7 + i * 4.05;
    card(s, x, 2.0, 3.75, 2.5);
    s.addText(n, {
      x: x + 0.28, y: 2.2, w: 3.2, h: 0.85, margin: 0,
      fontFace: H, fontSize: 46, bold: true, color: TERRA,
    });
    s.addText(label, {
      x: x + 0.28, y: 3.1, w: 3.2, h: 1.25, margin: 0,
      fontFace: B, fontSize: 13, color: MUTED, lineSpacing: 18, valign: 'top',
    });
  });

  s.addText(
    'Jharkhand’s PALASH programme has measurably improved foundational literacy by teaching in the mother tongue. ' +
    'The bottleneck is not method or funding. It is that there is no one in the room who speaks the language, ' +
    'and training a generation of teachers is a decade of work.',
    { x: 0.7, y: 4.85, w: 11.9, h: 1.0, margin: 0, fontFace: B, fontSize: 15, color: INK, lineSpacing: 24 },
  );

  s.addText('Ol Saathi puts the language in the room today, on a tablet the school already has.', {
    x: 0.7, y: 5.95, w: 11.9, h: 0.45, margin: 0,
    fontFace: B, fontSize: 15, bold: true, italic: true, color: FOREST,
  });
}

/* ─────────────────────────── 3 · what it does ─────────────────────────── */
{
  const s = pres.addSlide();
  titleBlock(s, 'The ninety-second demo', 'What a teacher actually does');

  const steps = [
    ['Teacher speaks Hindi', 'She presses to talk and says\n“जोहार बच्चों”. No typing, no\nlanguage setting to choose.'],
    ['Santali appears', 'The sentence shows in Ol Chiki\nscript, labelled Machine\ntranslation, with the Hindi\nkept above it.'],
    ['The class hears it', 'Pre-rendered audio plays from\nthe device. No network, no\nwaiting, no model loading.'],
    ['It goes home on paper', 'The same lines print as a\nbilingual worksheet and\nflashcards, NIPUN coded.'],
  ];
  steps.forEach(([h, body], i) => {
    const x = 0.7 + i * 3.05;
    card(s, x, 2.05, 2.8, 3.15);
    badge(s, i + 1, x + 0.25, 2.3, 0.62);
    s.addText(h, {
      x: x + 0.25, y: 3.08, w: 2.32, h: 0.6, margin: 0,
      fontFace: H, fontSize: 15, bold: true, color: INK,
    });
    s.addText(body, {
      x: x + 0.25, y: 3.68, w: 2.32, h: 1.35, margin: 0,
      fontFace: B, fontSize: 11.5, color: MUTED, lineSpacing: 16, valign: 'top',
    });
    if (i < 3) {
      s.addText('›', {
        x: x + 2.82, y: 3.35, w: 0.24, h: 0.4, margin: 0,
        align: 'center', fontFace: B, fontSize: 24, bold: true, color: OCHRE,
      });
    }
  });

  s.addShape(pres.ShapeType.roundRect, {
    x: 0.7, y: 5.55, w: 11.9, h: 0.85, rectRadius: 0.06,
    fill: { color: 'F0F5F1' }, line: { color: 'CFE0D6', width: 0.75 },
  });
  s.addText([
    { text: 'Measured, not budgeted.  ', options: { bold: true, color: FOREST } },
    { text: 'Median 98 ms from speech result to first sound, over seven phrases with the network off, against a 3,000 ms ceiling. ' +
            'Recognition time is not in that figure, and the app shows its own measurements on the Check and Proof screen.', options: { color: INK } },
  ], { x: 1.0, y: 5.73, w: 11.3, h: 0.5, margin: 0, fontFace: B, fontSize: 13, lineSpacing: 19 });
}

/* ─────────────────────────── 4 · the screen ─────────────────────────── */
{
  const s = pres.addSlide();
  titleBlock(s, 'The product', 'One screen, and one promise on it');

  s.addImage({ path: 'mock-screen.png', x: 0.85, y: 1.85, w: 3.55, h: 4.35 });

  const points = [
    ['Hindi stays on screen', 'The teacher keeps her own words in view, so she always knows what was sent.'],
    ['Ol Chiki, in the real script', 'Rendered with Noto Sans Ol Chiki, bundled in the APK. Android ships no Ol Chiki font, so without it every character would be an empty box.'],
    ['The provenance chip', 'Says what produced the line and who, if anyone, has checked it. Machine output says machine output. It is the first thing we built and the last thing we would remove.'],
    ['Press to talk, nothing to configure', 'No language field, no settings, no typing in a script she cannot read.'],
  ];
  points.forEach(([h, t], i) => {
    const y = 1.95 + i * 1.08;
    badge(s, i + 1, 5.0, y + 0.05, 0.44, FOREST);
    s.addText(h, {
      x: 5.62, y: y, w: 7.0, h: 0.32, margin: 0,
      fontFace: H, fontSize: 15, bold: true, color: INK, valign: 'top',
    });
    s.addText(t, {
      x: 5.62, y: y + 0.34, w: 7.0, h: 0.68, margin: 0,
      fontFace: B, fontSize: 12, color: MUTED, lineSpacing: 16, valign: 'top',
    });
  });

  s.addText([
    { text: 'The word on the screen is ', options: { color: INK } },
    { text: 'johar', options: { color: FOREST, bold: true } },
    { text: ', the greeting shared across Santali, Ho and Mundari. It is the first thing a teacher would say, ' +
            'and the only Santali in this deck, because no Santali speaker has checked our pack yet. ' +
            'The app says that on every line it shows.', options: { color: INK } },
  ], { x: 0.85, y: 6.4, w: 11.75, h: 0.7, margin: 0, fontFace: B, fontSize: 12.5, italic: true, lineSpacing: 18 });

  s.addNotes('The chip is the whole argument. Anyone can put Ol Chiki on a screen. Saying where it came from, and refusing to show anything you cannot source, is the part nobody else will do.');
}

/* ─────────────────────────── 5 · Bhashini ─────────────────────────── */
{
  const s = pres.addSlide();
  titleBlock(s, 'Where every line comes from', 'Four models, each named on screen');

  s.addText(
    'Each piece of content records the model that produced it. These are the four in the shipped app:',
    { x: 0.7, y: 1.75, w: 11.9, h: 0.4, margin: 0, fontFace: B, fontSize: 14, color: INK },
  );

  const models = [
    ['Translation, in the pack', 'AI4Bharat IndicTrans2 1.1B', 'Santali and 16 more languages, from the human-written English for each Hindi line. Run locally at build time.'],
    ['Translation, live', 'Bhashini IndicTrans v2', 'Only for a sentence not in the pack, and only when the tablet is online.'],
    ['Speech, five languages', 'Bhashini AI4Bharat Indic-TTS', 'Tamil, Malayalam, Telugu, Bengali and Bodo. Generated once, shipped as audio.'],
    ['Speech, Santali', 'AI4Bharat Indic Parler-TTS', 'Bhashini has no Santali voice; we checked with a working key. Run locally, labelled unchecked.'],
  ];
  models.forEach(([kind, name, note], i) => {
    const x = 0.7 + (i % 2) * 6.1;
    const y = 2.3 + Math.floor(i / 2) * 1.72;
    card(s, x, y, 5.8, 1.5);
    s.addShape(pres.ShapeType.ellipse, { x: x + 0.28, y: y + 0.32, w: 0.42, h: 0.42, fill: { color: FOREST } });
    s.addText(kind.toUpperCase(), {
      x: x + 0.85, y: y + 0.22, w: 4.7, h: 0.26, margin: 0,
      fontFace: B, fontSize: 10, bold: true, charSpacing: 1.5, color: TERRA,
    });
    s.addText(name, {
      x: x + 0.85, y: y + 0.46, w: 4.7, h: 0.3, margin: 0,
      fontFace: H, fontSize: 15, bold: true, color: INK,
    });
    s.addText(note, {
      x: x + 0.85, y: y + 0.78, w: 4.7, h: 0.6, margin: 0,
      fontFace: B, fontSize: 11.5, color: MUTED, lineSpacing: 15, valign: 'top',
    });
  });

  s.addText([
    { text: 'Why this matters for defensibility.  ', options: { bold: true, color: FOREST } },
    { text: 'Our team does not read Santali, so we cannot be the authority on it, and neither can a model. ' +
            'Every entry records the model and timestamp that produced it, and a two-route cross-check ranks the twelve lines a Santali speaker should read first.',
      options: { color: INK } },
  ], { x: 0.7, y: 5.85, w: 11.9, h: 0.75, margin: 0, fontFace: B, fontSize: 13, lineSpacing: 19 });
}

/* ─────────────────────────── 5 · architecture ─────────────────────────── */
{
  const s = pres.addSlide();
  titleBlock(s, 'Architecture', 'Translate once online. Run forever offline.');

  s.addText('BUILD TIME  ·  once, with a network', {
    x: 0.7, y: 1.78, w: 5.7, h: 0.3, margin: 0,
    fontFace: B, fontSize: 11, bold: true, charSpacing: 1.5, color: TERRA,
  });
  card(s, 0.7, 2.15, 5.7, 3.3);
  const build = [
    'Hindi FLN content: 53 lines, classroom phrases plus NCERT Sarangi lesson lines',
    'Translated by IndicTrans2, then spoken by Bhashini Indic-TTS, or by Indic Parler-TTS for Santali',
    'Target text and audio written into the APK, with the model named for each line',
  ];
  build.forEach((t, i) => {
    badge(s, i + 1, 1.0, 2.45 + i * 1.0, 0.44, FOREST);
    s.addText(t, {
      x: 1.6, y: 2.45 + i * 1.0, w: 4.5, h: 0.8, margin: 0,
      fontFace: B, fontSize: 12.5, color: INK, lineSpacing: 17, valign: 'top',
    });
  });

  s.addText('→', {
    x: 6.5, y: 3.4, w: 0.4, h: 0.5, margin: 0,
    align: 'center', fontFace: B, fontSize: 28, bold: true, color: OCHRE,
  });

  s.addText('RUN TIME  ·  in the classroom, no network', {
    x: 7.0, y: 1.78, w: 5.6, h: 0.3, margin: 0,
    fontFace: B, fontSize: 11, bold: true, charSpacing: 1.5, color: TERRA,
  });
  card(s, 7.0, 2.15, 5.6, 3.3, 'F0F5F1');
  const run = [
    'Teacher speaks. Android speech recognition, Hindi, on device',
    'Hash lookup in the shipped pack. Microseconds, zero network',
    'Ol Chiki on screen, Santali audio played from local storage',
  ];
  run.forEach((t, i) => {
    badge(s, i + 1, 7.3, 2.45 + i * 1.0, 0.44, TERRA);
    s.addText(t, {
      x: 7.9, y: 2.45 + i * 1.0, w: 4.4, h: 0.8, margin: 0,
      fontFace: B, fontSize: 12.5, color: INK, lineSpacing: 17, valign: 'top',
    });
  });

  s.addText([
    { text: 'No machine learning runs on the tablet. ', options: { bold: true, color: FOREST } },
    { text: 'The problem statement requires full offline operation “after initial content synchronisation”, and this is exactly that. ' +
            'It also means nothing can fail on stage: no network, no model load, no variance.', options: { color: INK } },
  ], { x: 0.7, y: 5.7, w: 11.9, h: 0.75, margin: 0, fontFace: B, fontSize: 13, lineSpacing: 19 });
}

/* ─────────────────────────── 6 · the rule ─────────────────────────── */
{
  const s = pres.addSlide();
  s.background = { color: FOREST };
  titleBlock(s, 'The design rule we will not break', 'The app never invents a translation', true);

  s.addText(
    'A teacher who does not speak Santali cannot tell a good translation from a bad one. The children can. ' +
    'So every piece of output carries its provenance, shown on screen next to the text.',
    { x: 0.7, y: 1.85, w: 11.9, h: 0.7, margin: 0, fontFace: B, fontSize: 15, color: 'CFE0D6', lineSpacing: 23 },
  );

  const states = [
    ['MACHINE TRANSLATION', 'Produced by a named model\nduring the pack build and\nshipped verbatim. Says so,\nand says it is unchecked.', '52B788'],
    ['TRANSLITERATED', 'Hindi respelled in Ol Chiki.\nReadable aloud, but it is not\nthe language. Always labelled\nas such, never as translation.', OCHRE],
    ['UNAVAILABLE', 'Nothing verified for this input.\nThe app says so and shows\nnothing, rather than offering\na plausible guess.', 'E07A5F'],
  ];
  states.forEach(([label, body, col], i) => {
    const x = 0.7 + i * 4.05;
    s.addShape(pres.ShapeType.roundRect, {
      x, y: 2.75, w: 3.75, h: 2.35, rectRadius: 0.08,
      fill: { color: FOREST_MID }, line: { color: col, width: 1.25 },
    });
    s.addShape(pres.ShapeType.ellipse, { x: x + 0.3, y: 3.02, w: 0.26, h: 0.26, fill: { color: col } });
    s.addText(label, {
      x: x + 0.68, y: 2.98, w: 2.9, h: 0.3, margin: 0,
      fontFace: B, fontSize: 11.5, bold: true, charSpacing: 1.2, color: col,
    });
    s.addText(body, {
      x: x + 0.3, y: 3.45, w: 3.15, h: 1.5, margin: 0,
      fontFace: B, fontSize: 12, color: 'D9E6DE', lineSpacing: 17, valign: 'top',
    });
  });

  s.addText(
    'This is not caution for its own sake. Output that looks plausible and is wrong is the single failure mode that would ' +
    'discredit the whole project in a Jharkhand classroom, and it is invisible to everyone in the room except the children.',
    { x: 0.7, y: 5.45, w: 11.9, h: 0.8, margin: 0, fontFace: B, fontSize: 13.5, italic: true, color: OCHRE, lineSpacing: 20 },
  );
}

/* ─────────────────────────── 7 · requirements ─────────────────────────── */
{
  const s = pres.addSlide();
  titleBlock(s, 'Against the problem statement', 'The four things it asks us to deliver');

  const rows = [
    ['1', 'Hindi to tribal language,\nminimum one language', 'Santali in Ol Chiki, 53 lines, with synthesised audio labelled unchecked. 16 more languages in the same format. Ho and Mundari have no model anywhere yet.'],
    ['2', 'Real-time voice translation,\nunder three seconds', 'Measured median 98 ms from speech result to first sound, network off, over seven phrases. Recognition time not included.'],
    ['3', 'Auto-generated bilingual\nworksheet output', 'A4 worksheets and flashcards from the same pack: Hindi line, target line, NIPUN Bharat outcome code on every item.'],
    ['4', 'Full offline operation on a\nlow-end Android tablet', 'minSdk 28. On a 2 GB Android 9 emulator: 43 to 45 MB peak memory, zero network calls, tested with the network off.'],
  ];
  rows.forEach(([n, req, how], i) => {
    const y = 1.85 + i * 1.12;
    s.addShape(pres.ShapeType.roundRect, {
      x: 0.7, y, w: 11.9, h: 1.0, rectRadius: 0.06,
      fill: { color: i % 2 ? 'F7F7F5' : WHITE }, line: { color: LINE, width: 0.75 },
    });
    badge(s, n, 0.98, y + 0.28, 0.44, FOREST);
    s.addText(req, {
      x: 1.6, y: y + 0.16, w: 3.3, h: 0.7, margin: 0,
      fontFace: H, fontSize: 13.5, bold: true, color: INK, lineSpacing: 17,
    });
    s.addText(how, {
      x: 5.1, y: y + 0.2, w: 7.3, h: 0.65, margin: 0,
      fontFace: B, fontSize: 12.5, color: MUTED, lineSpacing: 17, valign: 'top',
    });
  });

  s.addText('Also required at submission: a demo video and a public GitHub repository. Both are planned into the build schedule.', {
    x: 0.7, y: 6.5, w: 11.9, h: 0.4, margin: 0, fontFace: B, fontSize: 12, italic: true, color: MUTED,
  });
}

/* ─────────────────────────── 8 · stack ─────────────────────────── */
{
  const s = pres.addSlide();
  titleBlock(s, 'Technical stack', 'Deliberately small');

  const cols = [
    ['Models', FOREST, [
      'AI4Bharat IndicTrans2 1B, run locally',
      'Bhashini Indic-TTS for five languages',
      'Indic Parler-TTS for Santali, run locally',
      'Bhashini live translation when online',
    ]],
    ['Android application', TERRA, [
      'Kotlin, minSdk 28, targets 2 GB tablets',
      'Android SpeechRecognizer, hi-IN, offline',
      'PdfDocument for the bilingual worksheet',
      'Noto Sans Ol Chiki and Devanagari bundled',
    ]],
    ['Content and assurance', OCHRE, [
      'NCERT Sarangi Class 2 Hindi, hand-checked',
      'NIPUN Bharat outcome codes on every item',
      'Node pack generator, resumable, provenance',
      'CI checks assets are real, not placeholders',
    ]],
  ];
  cols.forEach(([head, col, items], i) => {
    const x = 0.7 + i * 4.05;
    card(s, x, 1.95, 3.75, 3.05);
    s.addShape(pres.ShapeType.ellipse, { x: x + 0.3, y: 2.25, w: 0.3, h: 0.3, fill: { color: col } });
    s.addText(head, {
      x: x + 0.72, y: 2.19, w: 2.85, h: 0.42, margin: 0,
      fontFace: H, fontSize: 14.5, bold: true, color: INK,
    });
    s.addText(
      items.map((t, j) => ({ text: t, options: { bullet: true, breakLine: j < items.length - 1 } })),
      { x: x + 0.32, y: 2.72, w: 3.15, h: 2.15, margin: 0,
        fontFace: B, fontSize: 11.5, color: MUTED, lineSpacing: 16, paraSpaceAfter: 8, valign: 'top' },
    );
  });

  s.addText([
    { text: 'What we are deliberately not building:  ', options: { bold: true, color: TERRA } },
    { text: 'on-device neural translation, vector search, OCR, mesh sync, a custom keyboard. ' +
            'Hindi and Santali are from different language families, so on-device character mapping cannot produce meaning, ' +
            'and the rest is scope that does not serve the four requirements.', options: { color: INK } },
  ], { x: 0.7, y: 5.6, w: 11.9, h: 0.9, margin: 0, fontFace: B, fontSize: 13, lineSpacing: 19 });
}

/* ─────────────────────────── 9 · close ─────────────────────────── */
{
  const s = pres.addSlide();
  s.background = { color: FOREST };
  s.addShape(pres.ShapeType.ellipse, { x: -2.4, y: 5.1, w: 4.4, h: 4.4, fill: { color: FOREST_MID } });
  s.addShape(pres.ShapeType.ellipse, { x: 11.4, y: -1.1, w: 3.4, h: 3.4, fill: { color: TERRA } });

  s.addText('What we are asking for', {
    x: 0.9, y: 1.25, w: 11, h: 0.6, margin: 0,
    fontFace: H, fontSize: 32, bold: true, color: WHITE,
  });
  s.addText(
    'Bhashini access now runs live translation and speech for five languages. What is missing is people: ' +
    'a Santali speaker to review the pack, and a Santali voice to replace our synthesised one.',
    { x: 0.9, y: 2.0, w: 10.6, h: 0.8, margin: 0, fontFace: B, fontSize: 15, color: 'CFE0D6', lineSpacing: 23 },
  );

  const asks = [
    ['Santali first', 'Text, synthesised audio and a printed worksheet today. A speaker review turns it from machine output into checked content.'],
    ['Ho and Mundari next', 'No model exists for either yet. The app takes a new language the day one does.'],
    ['Beyond Jharkhand', '17 languages already ship in the same pack format, same app.'],
  ];
  asks.forEach(([h, t], i) => {
    const x = 0.9 + i * 3.95;
    s.addShape(pres.ShapeType.roundRect, {
      x, y: 3.1, w: 3.6, h: 1.55, rectRadius: 0.08,
      fill: { color: FOREST_MID }, line: { color: '3F8768', width: 1 },
    });
    s.addText(h, {
      x: x + 0.28, y: 3.32, w: 3.05, h: 0.35, margin: 0,
      fontFace: H, fontSize: 15, bold: true, color: OCHRE,
    });
    s.addText(t, {
      x: x + 0.28, y: 3.72, w: 3.05, h: 0.8, margin: 0,
      fontFace: B, fontSize: 11.5, color: 'D9E6DE', lineSpacing: 16, valign: 'top',
    });
  });

  s.addShape(pres.ShapeType.line, { x: 0.9, y: 5.25, w: 3.2, h: 0, line: { color: OCHRE, width: 2 } });
  s.addText('Ol Saathi  ·  ओल साथी', {
    x: 0.9, y: 5.5, w: 7, h: 0.45, margin: 0,
    fontFace: H, fontSize: 22, bold: true, color: WHITE,
  });
  s.addText('Team INNOV8   ·   JAIN Deemed-to-be University   ·   SIH26042, Government of Jharkhand', {
    x: 0.9, y: 6.0, w: 11, h: 0.35, margin: 0, fontFace: B, fontSize: 13, color: 'A8C3B4',
  });

  s.addNotes('Close on the ask: Bhashini API access for Santali translation and TTS. Everything else we build ourselves.');
}

pres.writeFile({ fileName: 'Ol-Saathi-SIH26042.pptx' }).then((f) => console.log('wrote', f));

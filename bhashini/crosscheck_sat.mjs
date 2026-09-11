/**
 * Second opinion on the Santali pack, without a Santali speaker.
 *
 * The pack's Santali came from AI4Bharat IndicTrans2 run locally, translating
 * the human-written English for each line. This asks Bhashini to translate the
 * Hindi for the same line straight into Santali, and compares the two. Where
 * two routes land on the same Ol Chiki, the line is less likely to be a
 * one-off model error. Where they part ways, a speaker should look first.
 *
 * It then folds in the round-trip scores from tools/backtranslate_qa.py, which
 * translated each pack line back to English, so a line both checks distrust
 * rises to the top of the list a Santali speaker will be handed.
 *
 * What it is not: a review. Both routes are machine translation, and both come
 * from the IndicTrans2 family, so agreement can also mean a shared mistake.
 * Nothing in the pack is changed and no entry is marked checked.
 *
 *   node bhashini/with_keys.mjs bhashini/crosscheck_sat.mjs
 *
 * Raw responses are cached in bhashini/out/crosscheck/sat.json (gitignored),
 * so a run that stops can be run again. The report goes to
 * verification/santali-crosscheck.md.
 */
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ENDPOINT = 'https://dhruva-api.bhashini.gov.in/services/inference/pipeline';
const SERVICE = 'ai4bharat/indictrans-v2-all-gpu--t4';
const KEY = process.env.BHASHINI_INFERENCE_KEY || '';

if (!KEY) {
  console.error('BHASHINI_INFERENCE_KEY is not set. Run this through bhashini/with_keys.mjs.');
  process.exit(2);
}
const redact = (s) => String(s).split(KEY).join('<redacted>');

const pack = JSON.parse(readFileSync(path.join(ROOT, 'app/src/main/assets/pack/pack.sat.json'), 'utf8'));
const cacheDir = path.join(ROOT, 'bhashini', 'out', 'crosscheck');
const cacheFile = path.join(cacheDir, 'sat.json');
mkdirSync(cacheDir, { recursive: true });
const cache = existsSync(cacheFile) ? JSON.parse(readFileSync(cacheFile, 'utf8')) : {};

// Round-trip scores from tools/backtranslate_qa.py, if that has been run.
const btFile = path.join(ROOT, 'verification', 'back-translation-report.json');
const roundTrip = new Map();
if (existsSync(btFile)) {
  for (const r of JSON.parse(readFileSync(btFile, 'utf8')).rows || []) roundTrip.set(r.id, r);
}

async function translate(hindi) {
  const res = await fetch(ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: KEY },
    body: JSON.stringify({
      pipelineTasks: [{
        taskType: 'translation',
        config: { language: { sourceLanguage: 'hi', targetLanguage: 'sat' }, serviceId: SERVICE },
      }],
      inputData: { input: [{ source: hindi }] },
    }),
  });
  const body = await res.text();
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${body.slice(0, 160)}`);
  const out = JSON.parse(body).pipelineResponse?.[0]?.output?.[0]?.target || '';
  if (!out) throw new Error('no translation in the response');
  return out;
}

// chrF: character n-gram F-score, n = 1..4, beta = 2, spaces removed. The usual
// metric for scripts where word boundaries and suffixes vary; 1 is identical.
function chrF(a, b) {
  const strip = (s) => [...s.replace(/\s+/g, '')];
  const A = strip(a), B = strip(b);
  let p = 0, r = 0, n = 0;
  for (let k = 1; k <= 4; k++) {
    const grams = (xs) => {
      const m = new Map();
      for (let i = 0; i + k <= xs.length; i++) {
        const g = xs.slice(i, i + k).join('');
        m.set(g, (m.get(g) || 0) + 1);
      }
      return m;
    };
    const ga = grams(A), gb = grams(B);
    const ta = [...ga.values()].reduce((x, y) => x + y, 0);
    const tb = [...gb.values()].reduce((x, y) => x + y, 0);
    if (!ta || !tb) continue;
    let hit = 0;
    for (const [g, c] of ga) hit += Math.min(c, gb.get(g) || 0);
    p += hit / ta; r += hit / tb; n++;
  }
  if (!n) return 0;
  p /= n; r /= n;
  const beta2 = 4;
  return p + r === 0 ? 0 : ((1 + beta2) * p * r) / (beta2 * p + r);
}

// Ol Chiki block plus the punctuation and digits a line can legitimately hold.
const olChikiOnly = (s) => /^[᱐-᱿\s.,!?'"()\-–:;।॥0-9]+$/.test(s);

/**
 * Failures visible without reading Santali: the decoder looping on one word,
 * or one letter stuck repeating. These are properties of the string, not
 * judgements about the language, so they can be flagged by a machine.
 */
function broken(s) {
  const why = [];
  if (/(.)\1{3,}/u.test(s.replace(/\s/g, ''))) why.push('a letter repeats four or more times in a row');
  const words = s.replace(/[᱾!?.,]/g, ' ').split(/\s+/).filter(Boolean);
  const counts = new Map();
  words.forEach((w) => counts.set(w, (counts.get(w) || 0) + 1));
  const looped = [...counts.entries()].filter(([, c]) => c >= 3);
  if (looped.length) why.push('one word repeats three or more times');
  return why;
}

const ids = Object.keys(pack.entries).sort();
let fetched = 0;
const failed = [];
for (const id of ids) {
  if (cache[id]) continue;
  const hindi = (pack.entries[id].source || '').trim();
  if (!hindi) { failed.push(`${id}: no Hindi source`); continue; }
  try {
    cache[id] = await translate(hindi);
    fetched++;
    writeFileSync(cacheFile, JSON.stringify(cache, null, 2));
  } catch (e) {
    failed.push(`${id}: ${redact(e.message)}`);
  }
}

const ROUND_TRIP_LOW = 0.5;
const CHRF_DIFFERENT = 0.4;

const rows = ids.filter((id) => cache[id]).map((id) => {
  const e = pack.entries[id];
  const bt = roundTrip.get(id);
  return {
    id,
    hindi: e.source,
    en: e.en || '',
    pack: e.target,
    bhashini: cache[id],
    score: chrF(e.target, cache[id]),
    script: olChikiOnly(cache[id]),
    packBroken: broken(e.target),
    bhashiniBroken: broken(cache[id]),
    rt: bt ? bt.score : null,
    backEn: bt ? bt.back_en : '',
  };
});
const band = (s) => (s >= 0.999 ? 'identical' : s >= 0.7 ? 'close' : s >= 0.4 ? 'partial' : 'different');
const counts = { identical: 0, close: 0, partial: 0, different: 0 };
rows.forEach((r) => { counts[band(r.score)]++; });
const notOlChiki = rows.filter((r) => !r.script);
const bhashiniBroken = rows.filter((r) => r.bhashiniBroken.length);
const packBroken = rows.filter((r) => r.packBroken.length);
// Both checks distrust the pack line: its own round trip came back different,
// and a working second route disagrees with it.
const reviewFirst = rows
  .filter((r) => r.rt !== null && r.rt < ROUND_TRIP_LOW && r.score < CHRF_DIFFERENT && !r.bhashiniBroken.length)
  .sort((a, b) => a.rt + a.score - (b.rt + b.score));
rows.sort((a, b) => a.score - b.score);

const flag = (r) => [
  r.packBroken.length ? 'pack looks broken' : '',
  r.bhashiniBroken.length ? 'Bhashini output broken' : '',
  reviewFirst.includes(r) ? 'review first' : '',
].filter(Boolean).join(', ');
const cell = (s) => String(s).replace(/\|/g, '/');

const today = new Date().toISOString().slice(0, 10);
const md = [
  '# Santali cross-check: two machine routes compared',
  '',
  `Generated ${today} by \`bhashini/crosscheck_sat.mjs\`. **This is not a review.** No Santali speaker has checked any line, and nothing in the pack was changed.`,
  '',
  '- **Pack route:** human-written English, translated to Santali by AI4Bharat IndicTrans2 1B, run locally.',
  `- **Second route:** the Hindi for the same line, translated straight to Santali by Bhashini (\`${SERVICE}\`).`,
  '- **Round trip:** each pack line translated back to English by `tools/backtranslate_qa.py` and compared with the English it came from (0 to 1, higher is closer).',
  '- Both routes are the IndicTrans2 family, so agreement can also be a shared mistake. Disagreement is the useful signal.',
  '- Similarity between routes is chrF (character n-grams 1 to 4, beta 2, spaces ignored). 1.00 means identical.',
  '- Two routes can both be right and still differ: the pack translated English, Bhashini translated Hindi, and a short classroom phrase has more than one good Santali rendering.',
  '',
  '| Band | chrF | Lines |',
  '|---|---|---|',
  `| Identical | 1.00 | ${counts.identical} |`,
  `| Close | 0.70 to 0.99 | ${counts.close} |`,
  `| Partial | 0.40 to 0.69 | ${counts.partial} |`,
  `| Different | below 0.40 | ${counts.different} |`,
  '',
  `${rows.length} of ${ids.length} lines compared.` +
    (failed.length ? ` ${failed.length} could not be fetched.` : '') +
    (notOlChiki.length ? ` ${notOlChiki.length} Bhashini outputs contained characters outside Ol Chiki: ${notOlChiki.map((r) => r.id).join(', ')}.` : ' Every Bhashini output was pure Ol Chiki.'),
  '',
  '## Broken outputs, visible without reading Santali',
  '',
  `**Bhashini:** ${bhashiniBroken.length} of ${rows.length} outputs loop on a word or a letter` +
    (bhashiniBroken.length ? ` (${bhashiniBroken.map((r) => r.id).join(', ')}). For these lines the comparison says nothing about the pack.` : '.'),
  '',
  `**Pack:** ${packBroken.length} of ${rows.length} shipped lines show the same pattern` +
    (packBroken.length ? ` (${packBroken.map((r) => `${r.id}: ${r.packBroken.join('; ')}`).join(', ')}).` : '.'),
  '',
  '## Review first',
  '',
  `Lines where the pack's own round trip scored below ${ROUND_TRIP_LOW} and a working second route also disagrees (chrF below ${CHRF_DIFFERENT}). ` +
    'Two independent signals pointing at the same line. Give these to a Santali speaker before anything else.',
  '',
  ...(reviewFirst.length
    ? ['| Entry | Round trip | chrF | English source | Pack line | Pack line, back in English |', '|---|---|---|---|---|---|',
       ...reviewFirst.map((r) => `| ${r.id} | ${r.rt.toFixed(2)} | ${r.score.toFixed(2)} | ${cell(r.en)} | ${cell(r.pack)} | ${cell(r.backEn)} |`)]
    : ['None.']),
  '',
  '## Every line, least agreement first',
  '',
  '| Entry | chrF | Round trip | Flags | Hindi | Pack (IndicTrans2 via English) | Bhashini (from Hindi) |',
  '|---|---|---|---|---|---|---|',
  ...rows.map((r) => `| ${r.id} | ${r.score.toFixed(2)} | ${r.rt === null ? 'n/a' : r.rt.toFixed(2)} | ${flag(r)} | ${cell(r.hindi)} | ${cell(r.pack)} | ${cell(r.bhashini)} |`),
  '',
].join('\n');

mkdirSync(path.join(ROOT, 'verification'), { recursive: true });
writeFileSync(path.join(ROOT, 'verification', 'santali-crosscheck.md'), md);

console.log(`fetched ${fetched} new, ${rows.length} of ${ids.length} compared`);
console.log(`identical ${counts.identical}, close ${counts.close}, partial ${counts.partial}, different ${counts.different}`);
console.log(`non Ol Chiki outputs: ${notOlChiki.length}`);
console.log(`broken Bhashini outputs: ${bhashiniBroken.length}${bhashiniBroken.length ? ' (' + bhashiniBroken.map((r) => r.id).join(', ') + ')' : ''}`);
console.log(`broken pack lines: ${packBroken.length}${packBroken.length ? ' (' + packBroken.map((r) => r.id).join(', ') + ')' : ''}`);
console.log(`review first: ${reviewFirst.length}${reviewFirst.length ? ' (' + reviewFirst.map((r) => r.id).join(', ') + ')' : ''}`);
if (failed.length) {
  console.log('failed:\n  ' + failed.join('\n  '));
  process.exitCode = 1;
}
console.log('report: verification/santali-crosscheck.md');

/**
 * Build the verified content pack.
 *
 * The app does not translate at runtime. It looks up content that was
 * translated once, here, by Bhashini, and shipped inside the APK. That makes
 * the offline requirement trivial, makes the sub-three-second requirement a
 * measurement rather than a hope, and means the government's own platform is
 * the authority on the Santali rather than us.
 *
 * Every entry records which Bhashini service produced it and when, so the pack
 * can defend itself. If a judge questions a translation, the answer is in the
 * data.
 *
 * USAGE
 *   Dry run, no keys needed, shows exactly what would be fetched:
 *       node tools/build_pack.mjs --dry-run
 *
 *   Real run:
 *       BHASHINI_USER_ID=...  BHASHINI_ULCA_KEY=...  BHASHINI_INFERENCE_KEY=... \
 *       node tools/build_pack.mjs
 *
 *   Options:
 *       --lang sat        target language (default sat, Santali)
 *       --no-audio        text only, skip text-to-speech
 *       --limit N         only the first N items, for a quick trial
 *
 * Resumable. An existing pack is loaded first and untouched entries are kept,
 * so a run that dies halfway costs only the remainder.
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const PACK_DIR = path.join(ROOT, 'app', 'src', 'main', 'assets', 'pack');
const AUDIO_DIR = path.join(PACK_DIR, 'audio');

const CONFIG_URL = 'https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline';
const PIPELINE_ID = '64392f96daac500b55c543cd';
const SOURCE = 'hi';

const argv = process.argv.slice(2);
const has = (f) => argv.includes(f);
const val = (f, d) => { const i = argv.indexOf(f); return i >= 0 ? argv[i + 1] : d; };

const DRY = has('--dry-run');
const WITH_AUDIO = !has('--no-audio');
const TARGET = val('--lang', 'sat');
const LIMIT = Number(val('--limit', '0')) || 0;

const USER_ID = process.env.BHASHINI_USER_ID || '';
const ULCA_KEY = process.env.BHASHINI_ULCA_KEY || '';
const INFERENCE_KEY = process.env.BHASHINI_INFERENCE_KEY || '';

const c = { g: '\x1b[32m', r: '\x1b[31m', y: '\x1b[33m', b: '\x1b[36m', d: '\x1b[2m', x: '\x1b[0m' };
const ok = (m) => console.log(`${c.g}  ok  ${c.x} ${m}`);
const bad = (m) => console.log(`${c.r} fail ${c.x} ${m}`);
const warn = (m) => console.log(`${c.y} warn ${c.x} ${m}`);
const head = (m) => console.log(`\n${c.b}${m}${c.x}\n${'-'.repeat(60)}`);

// ── gather the source text ────────────────────────────────────────────────

/** Everything that needs translating, as a flat list of {id, hi, en, nipun, kind}. */
function collectSource() {
  const items = [];

  const phrasesPath = path.join(ROOT, 'tools', 'phrases.hi.json');
  const phrases = JSON.parse(fs.readFileSync(phrasesPath, 'utf8'));
  for (const p of phrases.phrases) {
    items.push({ id: p.id, hi: p.hi, en: p.en, nipun: p.nipun, kind: 'phrase' });
  }

  const lessonsPath = path.join(ROOT, 'content', 'lessons.json');
  if (fs.existsSync(lessonsPath)) {
    const doc = JSON.parse(fs.readFileSync(lessonsPath, 'utf8'));
    for (const lesson of doc.lessons ?? []) {
      for (const line of lesson.lines ?? []) {
        items.push({
          id: `${lesson.id}.${line.id}`,
          hi: line.hi,
          en: line.en ?? '',
          nipun: 'FL-RD',
          kind: 'lesson',
          lesson: lesson.id,
          lessonTitle: lesson.title,
          image: line.image ?? '',
        });
      }
      for (const q of lesson.checks ?? lesson.questions ?? []) {
        const hi = q.hi ?? q.question ?? '';
        if (!hi) continue;
        items.push({
          id: `${lesson.id}.${q.id ?? 'q'}`,
          hi,
          en: q.en ?? '',
          nipun: 'FL-OL',
          kind: 'check',
          lesson: lesson.id,
        });
      }
    }
  } else {
    warn('content/lessons.json not found, phrases only');
  }

  return LIMIT ? items.slice(0, LIMIT) : items;
}

// ── Bhashini ──────────────────────────────────────────────────────────────

async function getPipeline(tasks) {
  const res = await fetch(CONFIG_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', userID: USER_ID, ulcaApiKey: ULCA_KEY },
    body: JSON.stringify({ pipelineTasks: tasks, pipelineRequestConfig: { pipelineId: PIPELINE_ID } }),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`config ${res.status}: ${text.slice(0, 240)}`);
  return JSON.parse(text);
}

async function compute(ep, authKey, authValue, tasks, inputData) {
  const res = await fetch(ep, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', [authKey]: authValue },
    body: JSON.stringify({ pipelineTasks: tasks, inputData }),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`compute ${res.status}: ${text.slice(0, 240)}`);
  return JSON.parse(text);
}

/** Retry around a transient failure, because a 200-call run will hit one. */
async function withRetry(label, fn, attempts = 3) {
  let lastErr;
  for (let i = 1; i <= attempts; i++) {
    try {
      return await fn();
    } catch (e) {
      lastErr = e;
      if (i < attempts) {
        const wait = 800 * i;
        warn(`${label} attempt ${i} failed (${e.message.slice(0, 80)}), retrying in ${wait}ms`);
        await new Promise((r) => setTimeout(r, wait));
      }
    }
  }
  throw lastErr;
}

// ── main ──────────────────────────────────────────────────────────────────

async function main() {
  console.log(`\n  BUILD VERIFIED PACK  ${SOURCE} -> ${TARGET}${DRY ? c.d + '   (dry run)' + c.x : ''}`);
  console.log('  ' + '='.repeat(58));

  const items = collectSource();
  const counts = items.reduce((a, i) => ({ ...a, [i.kind]: (a[i.kind] ?? 0) + 1 }), {});
  head('Source');
  ok(`${items.length} strings: ` + Object.entries(counts).map(([k, v]) => `${v} ${k}`).join(', '));

  const packPath = path.join(PACK_DIR, `pack.${TARGET}.json`);
  let pack = { language: TARGET, source: SOURCE, generated: null, provenance: {}, entries: {} };
  if (fs.existsSync(packPath)) {
    pack = JSON.parse(fs.readFileSync(packPath, 'utf8'));
    ok(`existing pack loaded, ${Object.keys(pack.entries).length} entries kept`);
  }

  const todo = items.filter((i) => !pack.entries[i.id]?.target);
  ok(`${todo.length} still to translate, ${items.length - todo.length} already done`);

  if (DRY) {
    head('Dry run, nothing sent');
    for (const i of todo.slice(0, 8)) console.log(`  ${c.d}${i.id.padEnd(16)}${c.x} ${i.hi}`);
    if (todo.length > 8) console.log(`  ${c.d}... and ${todo.length - 8} more${c.x}`);
    console.log(`\n  Would call Bhashini ${todo.length}x for translation` +
      (WITH_AUDIO ? ` and ${todo.length}x for audio` : ', audio skipped'));
    console.log(`  Would write ${path.relative(ROOT, packPath)}`);
    if (WITH_AUDIO) console.log(`  Would write ${todo.length} wav files to ${path.relative(ROOT, AUDIO_DIR)}`);
    console.log(`\n  Set the three BHASHINI_* environment variables and rerun without --dry-run.\n`);
    return;
  }

  if (!USER_ID || !ULCA_KEY || !INFERENCE_KEY) {
    bad('Missing credentials');
    console.log('\n  Set all three, then rerun:');
    console.log('    BHASHINI_USER_ID, BHASHINI_ULCA_KEY, BHASHINI_INFERENCE_KEY');
    console.log('\n  Or use --dry-run to see what would happen.\n');
    process.exit(1);
  }

  // translation service
  head('Resolving models');
  const tCfg = await getPipeline([
    { taskType: 'translation', config: { language: { sourceLanguage: SOURCE, targetLanguage: TARGET } } },
  ]);
  const tService = tCfg.pipelineResponseConfig?.[0]?.config?.[0]?.serviceId;
  if (!tService) { bad(`No ${SOURCE}->${TARGET} translation model on this pipeline`); process.exit(1); }
  ok(`translation: ${tService}`);

  const inf = tCfg.pipelineInferenceAPIEndPoint;
  const endpoint = inf?.callbackUrl;
  const authKey = inf?.inferenceApiKey?.name || 'Authorization';
  const authValue = inf?.inferenceApiKey?.value || INFERENCE_KEY;

  // tts service, optional
  let ttsService = null;
  if (WITH_AUDIO) {
    try {
      const cfg = await getPipeline([{ taskType: 'tts', config: { language: { sourceLanguage: TARGET } } }]);
      ttsService = cfg.pipelineResponseConfig?.[0]?.config?.[0]?.serviceId ?? null;
      if (ttsService) ok(`text to speech: ${ttsService}`);
      else warn(`no ${TARGET} text to speech, the pack will be text only`);
    } catch (e) {
      warn(`text to speech lookup failed (${e.message.slice(0, 60)}), continuing text only`);
    }
  }

  pack.provenance = {
    translationService: tService,
    ttsService,
    pipelineId: PIPELINE_ID,
    platform: 'Bhashini (MeitY, Government of India)',
    note: 'Translations produced by the Government of India language platform, not by this team.',
  };

  fs.mkdirSync(AUDIO_DIR, { recursive: true });

  head(`Translating ${todo.length} strings`);
  let done = 0, failed = 0, withAudio = 0;

  for (const item of todo) {
    try {
      const out = await withRetry(item.id, () => compute(
        endpoint, authKey, authValue,
        [{ taskType: 'translation', config: { language: { sourceLanguage: SOURCE, targetLanguage: TARGET }, serviceId: tService } }],
        { input: [{ source: item.hi }] },
      ));
      const target = out.pipelineResponse?.[0]?.output?.[0]?.target;
      if (!target) throw new Error('empty translation');

      const entry = {
        source: item.hi,
        target,
        en: item.en,
        nipun: item.nipun,
        kind: item.kind,
        service: tService,
        at: new Date().toISOString(),
      };
      if (item.lesson) entry.lesson = item.lesson;
      if (item.image) entry.image = item.image;

      if (ttsService) {
        try {
          const a = await withRetry(`${item.id} audio`, () => compute(
            endpoint, authKey, authValue,
            [{ taskType: 'tts', config: { language: { sourceLanguage: TARGET }, serviceId: ttsService, gender: 'female' } }],
            { input: [{ source: target }] },
          ));
          const b64 = a.pipelineResponse?.[0]?.audio?.[0]?.audioContent;
          if (b64) {
            const file = `${item.id}.wav`;
            fs.writeFileSync(path.join(AUDIO_DIR, file), Buffer.from(b64, 'base64'));
            entry.audio = `pack/audio/${file}`;
            withAudio++;
          }
        } catch {
          // Audio is a bonus. A missing voice is not a failed entry.
        }
      }

      pack.entries[item.id] = entry;
      done++;
      process.stdout.write(`\r  ${done + failed}/${todo.length}  ${c.d}${item.id.padEnd(18)}${c.x}`);

      // Save every 10, so a crash costs at most ten calls.
      if (done % 10 === 0) {
        pack.generated = new Date().toISOString();
        fs.writeFileSync(packPath, JSON.stringify(pack, null, 2), 'utf8');
      }
    } catch (e) {
      failed++;
      console.log(`\n  ${c.r}skip${c.x} ${item.id}: ${e.message.slice(0, 90)}`);
    }
  }

  pack.generated = new Date().toISOString();
  fs.writeFileSync(packPath, JSON.stringify(pack, null, 2), 'utf8');

  head('Result');
  ok(`${done} translated, ${withAudio} with audio, ${failed} failed`);
  ok(`pack: ${path.relative(ROOT, packPath)}`);
  console.log(`\n  Read a few of these out loud to someone who speaks ${TARGET} before the demo.`);
  console.log(`  The pack records the Bhashini service that produced each line, so the`);
  console.log(`  provenance is in the data if anyone asks.\n`);
}

main().catch((e) => { bad('Unexpected: ' + e.message); process.exit(1); });

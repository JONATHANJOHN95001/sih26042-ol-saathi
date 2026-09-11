/**
 * Build-time speech for a pack, from the target text it already has.
 *
 * Unlike build_pack.mjs this never re-translates and never rewrites a pack. It
 * reads each entry's existing target line, asks Bhashini to speak it, and saves
 * the float WAV Bhashini returns under bhashini/out/audio/<lang>/<entry id>.wav,
 * which is gitignored. tools/apply_audio_ogg.py then encodes those to OGG and
 * writes the pack's audio fields. Keeping the two steps apart means a failed or
 * half-finished synthesis run can never leave a pack pointing at missing files.
 *
 * It calls Dhruva directly with the inference key; BhashiniClient.kt explains
 * why the handshake is skippable. Run it with the keys loaded from
 * local.properties:
 *
 *   node bhashini/with_keys.mjs bhashini/pack_audio.mjs --lang tam
 *   node bhashini/with_keys.mjs bhashini/pack_audio.mjs --lang tam --limit 3
 *
 * Clips that already exist are skipped, so a run that stops halfway can simply
 * be run again.
 */
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ENDPOINT = 'https://dhruva-api.bhashini.gov.in/services/inference/pipeline';
const KEY = process.env.BHASHINI_INFERENCE_KEY || '';

// Pack code to [Bhashini code, speech model]. Only languages where a real
// request returned real audio (10 Sep 2026). Santali has no speech model.
const SPEECH = {
  tam: ['ta', 'ai4bharat/indic-tts-coqui-dravidian-gpu--t4'],
  mal: ['ml', 'ai4bharat/indic-tts-coqui-dravidian-gpu--t4'],
  tel: ['te', 'ai4bharat/indic-tts-coqui-dravidian-gpu--t4'],
  ben: ['bn', 'ai4bharat/indic-tts-coqui-indo_aryan-gpu--t4'],
  // Bodo is the one tribal language in the packs with a Bhashini voice
  // (the misc model, confirmed with a real synthesis on 10 Sep 2026).
  brx: ['brx', 'ai4bharat/indic-tts-coqui-misc-gpu--t4'],
};

const argv = process.argv.slice(2);
const val = (flag, fallback) => {
  const i = argv.indexOf(flag);
  return i >= 0 ? argv[i + 1] : fallback;
};
const LANG = val('--lang', '');
const LIMIT = parseInt(val('--limit', '0'), 10) || 0;

if (!KEY) {
  console.error('BHASHINI_INFERENCE_KEY is not set. Run this through bhashini/with_keys.mjs.');
  process.exit(2);
}
if (!SPEECH[LANG]) {
  console.error(`--lang must be one of: ${Object.keys(SPEECH).join(', ')}`);
  process.exit(2);
}

const redact = (s) => String(s).split(KEY).join('<redacted>');
const [code, service] = SPEECH[LANG];
const pack = JSON.parse(readFileSync(path.join(ROOT, 'app/src/main/assets/pack', `pack.${LANG}.json`), 'utf8'));
const outDir = path.join(ROOT, 'bhashini', 'out', 'audio', LANG);
mkdirSync(outDir, { recursive: true });

async function speak(text) {
  const t0 = Date.now();
  const res = await fetch(ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: KEY },
    body: JSON.stringify({
      pipelineTasks: [{
        taskType: 'tts',
        config: { language: { sourceLanguage: code }, serviceId: service, gender: 'female' },
      }],
      inputData: { input: [{ source: text }] },
    }),
  });
  const body = await res.text();
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${body.slice(0, 160)}`);
  const b64 = JSON.parse(body).pipelineResponse?.[0]?.audio?.[0]?.audioContent || '';
  if (!b64) throw new Error('no audio in the response');
  return { wav: Buffer.from(b64, 'base64'), ms: Date.now() - t0 };
}

const ids = Object.keys(pack.entries).sort();
const todo = LIMIT ? ids.slice(0, LIMIT) : ids;
let made = 0;
let skipped = 0;
const failed = [];
const times = [];

for (const id of todo) {
  const file = path.join(outDir, `${id}.wav`);
  if (existsSync(file)) { skipped++; continue; }
  const text = (pack.entries[id].target || '').trim();
  if (!text) { failed.push(`${id}: no target text`); continue; }
  try {
    const { wav, ms } = await speak(text);
    writeFileSync(file, wav);
    made++;
    times.push(ms);
    console.log(`  ${id.padEnd(18)} ${String(ms).padStart(5)} ms  ${wav.length} bytes`);
  } catch (e) {
    failed.push(`${id}: ${redact(e.message)}`);
    console.log(`  ${id.padEnd(18)} FAILED ${redact(e.message)}`);
  }
}

times.sort((a, b) => a - b);
const median = times.length ? `, median ${times[Math.floor(times.length / 2)]} ms` : '';
console.log(`\n${LANG}: ${made} made, ${skipped} already present, ${failed.length} failed${median}`);
if (failed.length) {
  console.log('Failed:\n  ' + failed.join('\n  '));
  process.exitCode = 1;
}
console.log(`Clips are in ${outDir}. Next: .venv-tts python tools/apply_audio_ogg.py --lang ${LANG}`);

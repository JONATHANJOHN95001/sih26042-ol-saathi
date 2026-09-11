/**
 * Where does Santali speech live, and which script does it want?
 * Uses only pipeline IDs published on the current Bhashini docs
 * (dibd-bhashini.gitbook.io, Pipeline Search Call page). Keys are redacted
 * from everything printed.
 *
 *   node bhashini/with_keys.mjs bhashini/probe_sat_tts.mjs
 */
import { writeFileSync, mkdirSync } from 'node:fs';

const USER = process.env.BHASHINI_USER_ID || '';
const ULCA = process.env.BHASHINI_ULCA_KEY || '';
const INF = process.env.BHASHINI_INFERENCE_KEY || '';
const CFG = 'https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline';
const PIPES = {
  'IIT Madras (ASR, TTS)': '660fa5bec7fb5b0328229016',
  'Initial / MeitY (ASR, NMT, translit, TTS)': '64392f96daac500b55c543cd',
};
const red = (s) => [USER, ULCA, INF].filter(Boolean).reduce((a, k) => a.split(k).join('<redacted>'), String(s));
const H = { 'Content-Type': 'application/json', userID: USER, ulcaApiKey: ULCA };
const post = async (url, headers, body) => {
  const r = await fetch(url, { method: 'POST', headers, body: JSON.stringify(body) });
  const t = await r.text(); let j = null; try { j = JSON.parse(t); } catch {}
  return { status: r.status, json: j, text: t };
};
const config = (pipelineId, tasks) => post(CFG, H, { pipelineTasks: tasks, pipelineRequestConfig: { pipelineId } });
const show = (r) => r.json?.pipelineResponseConfig
  ? JSON.stringify(r.json.pipelineResponseConfig.map((t) => ({ task: t.taskType, services: t.config.map((c) => ({ serviceId: c.serviceId, modelId: c.modelId, language: c.language })) })))
  : red(r.text).slice(0, 200).replace(/\s+/g, ' ');

let ttsCfg = null, ttsPipe = '';
console.log('=== 1. config: TTS sat on each published pipeline ===');
for (const [name, id] of Object.entries(PIPES)) {
  const r = await config(id, [{ taskType: 'tts', config: { language: { sourceLanguage: 'sat' } } }]);
  console.log(`  ${name} ${id} -> ${r.status} ${show(r)}`);
  if (r.status === 200 && !ttsCfg) { ttsCfg = r.json; ttsPipe = id; }
}

console.log('\n=== 2. config: full chain on one pipeline ===');
for (const [name, id] of Object.entries(PIPES)) {
  const r = await config(id, [
    { taskType: 'asr', config: { language: { sourceLanguage: 'hi' } } },
    { taskType: 'translation', config: { language: { sourceLanguage: 'hi', targetLanguage: 'sat' } } },
    { taskType: 'tts', config: { language: { sourceLanguage: 'sat' } } },
  ]);
  console.log(`  ${name} -> ${r.status} ${show(r).slice(0, 200)}`);
}

console.log('\n=== 3. config: transliteration sat on MeitY (Ol Chiki -> Devanagari bridge?) ===');
const tr = await config(PIPES['Initial / MeitY (ASR, NMT, translit, TTS)'], [{ taskType: 'transliteration', config: { language: { sourceLanguage: 'sat' } } }]);
console.log(`  -> ${tr.status} ${show(tr)}`);

if (ttsCfg) {
  const ep = ttsCfg.pipelineInferenceAPIEndPoint;
  const svc = ttsCfg.pipelineResponseConfig[0].config[0];
  console.log(`\n=== 4. compute TTS on ${ttsPipe}: serviceId ${svc.serviceId}, script ${JSON.stringify(svc.language)} ===`);
  console.log(`  compute URL ${ep.callbackUrl}, header ${ep.inferenceApiKey.name}, same key as dashboard: ${ep.inferenceApiKey.value === INF}`);
  const auth = { 'Content-Type': 'application/json', [ep.inferenceApiKey.name]: ep.inferenceApiKey.value };
  mkdirSync('bhashini/out/probe', { recursive: true });
  const samples = {
    olck: 'ᱟᱢᱨᱮᱱ ᱜᱤᱫᱽᱨᱟᱹ ᱠᱚᱣᱟᱜ ᱯᱚᱛᱚᱵ ᱮᱛᱚᱦᱚᱵ ᱢᱮ ᱾',   // Bhashini's own hi->sat output from the last probe
    deva: 'जोहार',                                            // "Johar", Santali greeting in Devanagari
  };
  for (const [script, text] of Object.entries(samples)) {
    const r = await post(ep.callbackUrl, auth, {
      pipelineTasks: [{ taskType: 'tts', config: { language: svc.language, serviceId: svc.serviceId, gender: 'female' } }],
      inputData: { input: [{ source: text }] },
    });
    const b64 = r.json?.pipelineResponse?.[0]?.audio?.[0]?.audioContent || '';
    if (b64) writeFileSync(`bhashini/out/probe/sat_${script}.wav`, Buffer.from(b64, 'base64'));
    console.log(`  ${script}: ${r.status}, ${b64.length} base64 chars${b64 ? `, saved bhashini/out/probe/sat_${script}.wav` : ''} ${b64 ? '' : red(r.text).slice(0, 200)}`);
  }
}

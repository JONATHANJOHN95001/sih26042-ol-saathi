/**
 * Probe the full Bhashini flow for Hindi speech -> Santali speech, the way the
 * Postman collection does it: search -> config -> compute. Prints shapes and
 * ids only; the key is redacted from anything echoed.
 *
 *   node bhashini/with_keys.mjs bhashini/probe_chain.mjs
 */
const USER = process.env.BHASHINI_USER_ID || '';
const ULCA = process.env.BHASHINI_ULCA_KEY || '';
const INF = process.env.BHASHINI_INFERENCE_KEY || '';
const BASE = 'https://meity-auth.ulcacontrib.org/ulca/apis';
const PIPELINE_ID = '64392f96daac500b55c543cd';
const red = (s) => [USER, ULCA, INF].filter(Boolean).reduce((a, k) => a.split(k).join('<redacted>'), String(s));
const ulcaHeaders = { 'Content-Type': 'application/json', userID: USER, ulcaApiKey: ULCA };

async function post(url, headers, body) {
  const r = await fetch(url, { method: 'POST', headers, body: JSON.stringify(body) });
  const t = await r.text();
  let j = null; try { j = JSON.parse(t); } catch {}
  return { status: r.status, json: j, text: t };
}

// 1. SEARCH
console.log('=== 1. pipeline search ===');
const searchBody = {
  pipelineTasks: [{ taskType: 'asr' }, { taskType: 'translation' }, { taskType: 'tts' }],
};
let searchUrl = '';
for (const path of ['/v0/pipeline/search', '/v0/model/pipeline/search', '/v0/model/searchPipeline', '/v1/pipeline/search']) {
  const r = await post(BASE + path, ulcaHeaders, searchBody);
  console.log(`  POST ${path} -> ${r.status} ${red(r.text).slice(0, 160).replace(/\s+/g, ' ')}`);
  if (r.status === 200 && r.json) { searchUrl = BASE + path; console.log('  FOUND. body sample:', red(JSON.stringify(r.json)).slice(0, 700)); break; }
}

// 2. CONFIG, full chain hi audio -> hi text -> sat text -> sat audio
const chain = (withTts) => [
  { taskType: 'asr', config: { language: { sourceLanguage: 'hi' } } },
  { taskType: 'translation', config: { language: { sourceLanguage: 'hi', targetLanguage: 'sat' } } },
  ...(withTts ? [{ taskType: 'tts', config: { language: { sourceLanguage: 'sat' } } }] : []),
];
const cfgUrl = BASE + '/v0/model/getModelsPipeline';
console.log('\n=== 2a. config ASR+NMT+TTS hi -> sat ===');
let r = await post(cfgUrl, ulcaHeaders, { pipelineTasks: chain(true), pipelineRequestConfig: { pipelineId: PIPELINE_ID } });
console.log(`  -> ${r.status} ${red(r.text).slice(0, 300).replace(/\s+/g, ' ')}`);

console.log('\n=== 2b. config ASR+NMT hi -> sat ===');
r = await post(cfgUrl, ulcaHeaders, { pipelineTasks: chain(false), pipelineRequestConfig: { pipelineId: PIPELINE_ID } });
console.log(`  -> ${r.status}`);
const cfg = r.json || {};
const svc = (cfg.pipelineResponseConfig || []).map((t) => [t.taskType, (t.config || []).map((c) => c.serviceId)]);
console.log('  services offered:', JSON.stringify(svc));
const ep = cfg.pipelineInferenceAPIEndPoint || {};
console.log('  callbackUrl:', ep.callbackUrl, '| header name:', ep.inferenceApiKey?.name, '| key matches dashboard inference key:', ep.inferenceApiKey?.value === INF);

// 3. COMPUTE: make Hindi audio with Hindi TTS, then run ASR+NMT on it
const compUrl = ep.callbackUrl || 'https://dhruva-api.bhashini.gov.in/services/inference/pipeline';
const auth = { 'Content-Type': 'application/json', [ep.inferenceApiKey?.name || 'Authorization']: INF };
console.log('\n=== 3a. make a Hindi test clip (Hindi TTS) ===');
const hiCfg = await post(cfgUrl, ulcaHeaders, { pipelineTasks: [{ taskType: 'tts', config: { language: { sourceLanguage: 'hi' } } }], pipelineRequestConfig: { pipelineId: PIPELINE_ID } });
const hiTts = hiCfg.json?.pipelineResponseConfig?.[0]?.config?.[0]?.serviceId;
console.log('  hindi tts service:', hiTts);
const sentence = 'बच्चों, अपनी किताब खोलो।';
r = await post(compUrl, auth, { pipelineTasks: [{ taskType: 'tts', config: { language: { sourceLanguage: 'hi' }, serviceId: hiTts, gender: 'female', samplingRate: 16000 } }], inputData: { input: [{ source: sentence }] } });
const b64 = r.json?.pipelineResponse?.[0]?.audio?.[0]?.audioContent || '';
console.log(`  -> ${r.status}, ${b64.length} base64 chars of audio`);

console.log('\n=== 3b. compute ASR+NMT on that clip ===');
const tasks = (cfg.pipelineResponseConfig || []).map((t) => ({
  taskType: t.taskType,
  config: { language: t.config[0].language, serviceId: t.config[0].serviceId, ...(t.taskType === 'asr' ? { audioFormat: 'wav', samplingRate: 16000 } : {}) },
}));
const t0 = Date.now();
r = await post(compUrl, auth, { pipelineTasks: tasks, inputData: { audio: [{ audioContent: b64 }] } });
console.log(`  -> ${r.status} in ${Date.now() - t0} ms`);
for (const p of r.json?.pipelineResponse || []) console.log(`  ${p.taskType}:`, JSON.stringify(p.output?.[0] || {}));
if (!r.json) console.log('  ', red(r.text).slice(0, 300));
console.log('\n  said:', sentence);

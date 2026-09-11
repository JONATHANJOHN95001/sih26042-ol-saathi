/**
 * Santali speech from the pack's own Devanagari rendering.
 *
 * Every Santali pack entry carries `bridge`: its Ol Chiki target respelled in
 * Devanagari by tools/olchiki_bridge.py, letter for letter. The IITM Santali
 * voice (pipeline 660fa5bec7fb5b0328229016, Bhashini/IITM/TTS) returns 0.02 s
 * of silence for Ol Chiki input and speech for Devanagari, and Bhashini's own
 * sat->hi transliterator leaves Ol Chiki letters behind. So this sends the
 * bridge text instead. Keys redacted from output.
 *
 *   node bhashini/with_keys.mjs bhashini/probe_sat_packbridge.mjs
 */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';

const USER = process.env.BHASHINI_USER_ID || '';
const ULCA = process.env.BHASHINI_ULCA_KEY || '';
const INF = process.env.BHASHINI_INFERENCE_KEY || '';
const CFG = 'https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline';
const IITM = '660fa5bec7fb5b0328229016';
const red = (s) => [USER, ULCA, INF].filter(Boolean).reduce((a, k) => a.split(k).join('<redacted>'), String(s));
const post = async (url, headers, body) => {
  const r = await fetch(url, { method: 'POST', headers, body: JSON.stringify(body) });
  const t = await r.text(); let j = null; try { j = JSON.parse(t); } catch {}
  return { status: r.status, json: j, text: t };
};

const cfg = await post(CFG, { 'Content-Type': 'application/json', userID: USER, ulcaApiKey: ULCA },
  { pipelineTasks: [{ taskType: 'tts', config: { language: { sourceLanguage: 'sat' } } }], pipelineRequestConfig: { pipelineId: IITM } });
const svc = cfg.json?.pipelineResponseConfig?.[0]?.config?.[0];
const ep = cfg.json?.pipelineInferenceAPIEndPoint;
if (!svc) { console.log('config failed', cfg.status, red(cfg.text).slice(0, 200)); process.exit(1); }
const auth = { 'Content-Type': 'application/json', [ep.inferenceApiKey.name]: ep.inferenceApiKey.value };

const pack = JSON.parse(readFileSync('app/src/main/assets/pack/pack.sat.json', 'utf8'));
const ids = ['p01', 'p02', 'p10', 'p19', 'neema-dadi.c1', 'neema-dadi.l9'];
mkdirSync('bhashini/out/probe', { recursive: true });
const hasOlck = (s) => /[᱐-᱿]/.test(s);
for (const id of ids) {
  const e = pack.entries[id];
  const bridge = (e?.bridge || '').trim();
  if (!bridge) { console.log(`${id}: no bridge field`); continue; }
  const t0 = Date.now();
  const r = await post(ep.callbackUrl, auth, {
    pipelineTasks: [{ taskType: 'tts', config: { language: { sourceLanguage: 'sat' }, serviceId: svc.serviceId, gender: 'female' } }],
    inputData: { input: [{ source: bridge }] },
  });
  const b64 = r.json?.pipelineResponse?.[0]?.audio?.[0]?.audioContent || '';
  if (b64) writeFileSync(`bhashini/out/probe/sat_pack_${id}.wav`, Buffer.from(b64, 'base64'));
  console.log(`${id.padEnd(14)} ${r.status} ${String(Date.now() - t0).padStart(5)} ms  olck-left:${hasOlck(bridge)}  ${bridge}${b64 ? '' : '  ' + red(r.text).slice(0, 160)}`);
}

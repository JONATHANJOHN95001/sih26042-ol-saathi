/**
 * Ol Chiki -> Devanagari -> Santali speech. Tests the bridge that closes the
 * script gap: Bhashini's hi->sat translation answers in Ol Chiki, and the IITM
 * Santali voice speaks Devanagari input. Uses only service ids the config call
 * returned for this account. Keys redacted from output.
 *
 *   node bhashini/with_keys.mjs bhashini/probe_sat_bridge.mjs
 */
import { writeFileSync, mkdirSync } from 'node:fs';

const USER = process.env.BHASHINI_USER_ID || '';
const ULCA = process.env.BHASHINI_ULCA_KEY || '';
const INF = process.env.BHASHINI_INFERENCE_KEY || '';
const CFG = 'https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline';
const MEITY = '64392f96daac500b55c543cd';
const IITM = '660fa5bec7fb5b0328229016';
const red = (s) => [USER, ULCA, INF].filter(Boolean).reduce((a, k) => a.split(k).join('<redacted>'), String(s));
const post = async (url, headers, body) => {
  const r = await fetch(url, { method: 'POST', headers, body: JSON.stringify(body) });
  const t = await r.text(); let j = null; try { j = JSON.parse(t); } catch {}
  return { status: r.status, json: j, text: t };
};
const H = { 'Content-Type': 'application/json', userID: USER, ulcaApiKey: ULCA };

// Config calls, so every service id below comes from Bhashini, not from us.
const xl = await post(CFG, H, { pipelineTasks: [{ taskType: 'transliteration', config: { language: { sourceLanguage: 'sat', targetLanguage: 'hi' } } }], pipelineRequestConfig: { pipelineId: MEITY } });
const xlSvc = xl.json?.pipelineResponseConfig?.[0]?.config?.[0];
console.log(`config transliteration sat->hi: ${xl.status}, serviceId ${xlSvc?.serviceId}, ${JSON.stringify(xlSvc?.language)}`);
const tts = await post(CFG, H, { pipelineTasks: [{ taskType: 'tts', config: { language: { sourceLanguage: 'sat' } } }], pipelineRequestConfig: { pipelineId: IITM } });
const ttsSvc = tts.json?.pipelineResponseConfig?.[0]?.config?.[0];
const ep = tts.json?.pipelineInferenceAPIEndPoint;
console.log(`config tts sat (IITM): ${tts.status}, serviceId ${ttsSvc?.serviceId}`);
if (!xlSvc || !ttsSvc) { console.log(red(xl.text).slice(0, 200), red(tts.text).slice(0, 200)); process.exit(1); }
const auth = { 'Content-Type': 'application/json', [ep.inferenceApiKey.name]: ep.inferenceApiKey.value };

mkdirSync('bhashini/out/probe', { recursive: true });
const lines = {
  books: 'ᱟᱢᱨᱮᱱ ᱜᱤᱫᱽᱨᱟᱹ ᱠᱚᱣᱟᱜ ᱯᱚᱛᱚᱵ ᱮᱛᱚᱦᱚᱵ ᱢᱮ ᱾',  // Bhashini's hi->sat for "बच्चों अपनी किताब खोलो"
  johar: 'ᱡᱚᱦᱟᱨ',
};
for (const [name, olck] of Object.entries(lines)) {
  const t0 = Date.now();
  const r1 = await post(ep.callbackUrl, auth, {
    pipelineTasks: [{ taskType: 'transliteration', config: { language: xlSvc.language, serviceId: xlSvc.serviceId, isSentence: true, numSuggestions: 1 } }],
    inputData: { input: [{ source: olck }] },
  });
  const out = r1.json?.pipelineResponse?.[0]?.output?.[0]?.target;
  const deva = Array.isArray(out) ? out[0] : out;
  console.log(`\n${name}: translit ${r1.status} in ${Date.now() - t0} ms: ${olck} -> ${deva ?? red(r1.text).slice(0, 200)}`);
  if (!deva) continue;
  const t1 = Date.now();
  const r2 = await post(ep.callbackUrl, auth, {
    pipelineTasks: [{ taskType: 'tts', config: { language: { sourceLanguage: 'sat' }, serviceId: ttsSvc.serviceId, gender: 'female' } }],
    inputData: { input: [{ source: deva }] },
  });
  const b64 = r2.json?.pipelineResponse?.[0]?.audio?.[0]?.audioContent || '';
  if (b64) writeFileSync(`bhashini/out/probe/sat_bridge_${name}.wav`, Buffer.from(b64, 'base64'));
  console.log(`${name}: tts ${r2.status} in ${Date.now() - t1} ms, ${b64.length} base64 chars${b64 ? ` -> bhashini/out/probe/sat_bridge_${name}.wav` : ' ' + red(r2.text).slice(0, 200)}`);
}

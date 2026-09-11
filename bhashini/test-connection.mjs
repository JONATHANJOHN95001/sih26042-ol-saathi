/**
 * Bhashini connection test — run this FIRST.
 *
 * Proves three things before you build anything:
 *   1. Your credentials work
 *   2. Hindi -> Santali translation is actually available
 *   3. Whether Santali text-to-speech exists (the open question)
 *
 * SETUP
 *   1. Sign up at https://bhashini.gov.in  (Sign Up -> verify email)
 *   2. Go to My Profile -> generate your API key
 *   3. Fill in the three values below
 *   4. Run:  node bhashini/test-connection.mjs
 */

// Read from the environment, never from this file. The repository has to be
// public for the submission, and a pasted key would go public with it.
//
//   PowerShell:
//     $env:BHASHINI_USER_ID="..."; $env:BHASHINI_ULCA_KEY="..."; $env:BHASHINI_INFERENCE_KEY="..."
//     node bhashini/test-connection.mjs
//
//   Git Bash:
//     BHASHINI_USER_ID=... BHASHINI_ULCA_KEY=... BHASHINI_INFERENCE_KEY=... node bhashini/test-connection.mjs
//
// Same three variables as tools/build_pack.mjs, so set them once per shell.
const USER_ID       = process.env.BHASHINI_USER_ID       || '';
const ULCA_API_KEY  = process.env.BHASHINI_ULCA_KEY      || '';
const INFERENCE_KEY = process.env.BHASHINI_INFERENCE_KEY || '';
const CONFIG_URL = 'https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline';
const PIPELINE_ID = '64392f96daac500b55c543cd'; // MeitY pipeline

const SOURCE = 'hi';        // Hindi
const TARGET = 'sat';       // Santali
const SAMPLE = 'पानी जीवन के लिए बहुत ज़रूरी है।';   // "Water is very important for life."

const ok   = (m) => console.log('\x1b[32m  PASS \x1b[0m ' + m);
const bad  = (m) => console.log('\x1b[31m  FAIL \x1b[0m ' + m);
const warn = (m) => console.log('\x1b[33m  WARN \x1b[0m ' + m);
const head = (m) => console.log('\n\x1b[36m' + m + '\x1b[0m\n' + '-'.repeat(58));

if (!USER_ID || !ULCA_API_KEY || !INFERENCE_KEY) {
  console.log('');
  console.log('  Set these three environment variables first:');
  console.log('    BHASHINI_USER_ID, BHASHINI_ULCA_KEY, BHASHINI_INFERENCE_KEY');
  console.log('');
  console.log('  All three come from bhashini.gov.in, log in, My Profile, Generate.');
  console.log('');
  process.exit(1);
}

/** Step 1 of the Bhashini flow: ask which models can do this task. */
async function getPipeline(tasks) {
  const res = await fetch(CONFIG_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'userID': USER_ID,
      'ulcaApiKey': ULCA_API_KEY,
    },
    body: JSON.stringify({
      pipelineTasks: tasks,
      pipelineRequestConfig: { pipelineId: PIPELINE_ID },
    }),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`config ${res.status}: ${text.slice(0, 300)}`);
  return JSON.parse(text);
}

/** Step 2: actually run the model. */
async function compute(endpoint, authKey, authValue, tasks, inputData) {
  const res = await fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', [authKey]: authValue },
    body: JSON.stringify({ pipelineTasks: tasks, inputData }),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`compute ${res.status}: ${text.slice(0, 300)}`);
  return JSON.parse(text);
}

async function main() {
  console.log('\n  BHASHINI CONNECTION TEST');
  console.log('  ' + '='.repeat(56));

  // ── TEST 1 · credentials + translation availability ──
  head('TEST 1  Can we translate Hindi -> Santali?');

  let cfg;
  try {
    cfg = await getPipeline([
      { taskType: 'translation',
        config: { language: { sourceLanguage: SOURCE, targetLanguage: TARGET } } },
    ]);
    ok('Credentials accepted');
  } catch (e) {
    bad('Config call failed — ' + e.message);
    console.log('\n  Most likely: wrong userID / ulcaApiKey, or Hindi->Santali');
    console.log('  is not offered on this pipeline. Try TARGET = "or" (Odia)');
    console.log('  to check whether it is your keys or the language.\n');
    process.exit(1);
  }

  const task = cfg.pipelineResponseConfig?.[0];
  const serviceId = task?.config?.[0]?.serviceId;
  const inf = cfg.pipelineInferenceAPIEndPoint;

  if (!serviceId) {
    bad('No Santali translation model offered on this pipeline');
    console.log('\n  Translation to Santali is NOT available here.');
    console.log('  -> Fall back to SIH26092, or pick a different target language.\n');
    process.exit(1);
  }
  ok('Santali translation model found: ' + serviceId);

  const endpoint  = inf?.callbackUrl;
  const authKey   = inf?.inferenceApiKey?.name  || 'Authorization';
  const authValue = inf?.inferenceApiKey?.value || INFERENCE_KEY;

  // ── TEST 2 · a real translation ──
  head('TEST 2  Translate a real sentence');
  console.log('  Hindi in : ' + SAMPLE);
  try {
    const out = await compute(
      endpoint, authKey, authValue,
      [{ taskType: 'translation',
         config: { language: { sourceLanguage: SOURCE, targetLanguage: TARGET }, serviceId } }],
      { input: [{ source: SAMPLE }] }
    );
    const translated = out.pipelineResponse?.[0]?.output?.[0]?.target;
    if (translated) {
      console.log('  Santali  : ' + translated);
      ok('TRANSLATION WORKS — this project is viable');
    } else {
      warn('Call succeeded but returned no text');
      console.log('  ' + JSON.stringify(out).slice(0, 400));
    }
  } catch (e) {
    bad('Translation failed — ' + e.message);
  }

  // ── TEST 3 · does Santali speech output exist? ──
  head('TEST 3  Does Santali text-to-speech exist?');
  try {
    const ttsCfg = await getPipeline([
      { taskType: 'tts', config: { language: { sourceLanguage: TARGET } } },
    ]);
    const ttsService = ttsCfg.pipelineResponseConfig?.[0]?.config?.[0]?.serviceId;
    if (ttsService) {
      ok('Santali TTS available: ' + ttsService);
      console.log('  -> You can do full audio output. Build the voice feature.');
    } else {
      warn('No Santali TTS model offered');
      console.log('  -> Plan for Santali TEXT + illustrations instead of audio.');
      console.log('  -> Demo full audio using Hindi or Odia as a second language.');
    }
  } catch (e) {
    warn('TTS check failed — ' + e.message);
    console.log('  -> Assume no Santali audio. Design around text output.');
  }

  // ── TEST 4 · speech input ──
  head('TEST 4  Does Hindi speech-to-text exist? (teacher speaks)');
  try {
    const asrCfg = await getPipeline([
      { taskType: 'asr', config: { language: { sourceLanguage: SOURCE } } },
    ]);
    const asrService = asrCfg.pipelineResponseConfig?.[0]?.config?.[0]?.serviceId;
    if (asrService) ok('Hindi ASR available: ' + asrService);
    else warn('No Hindi ASR — teacher types instead of speaking');
  } catch (e) {
    warn('ASR check failed — ' + e.message);
  }

  console.log('\n  ' + '='.repeat(56));
  console.log('  Send me this whole output and I will tell you exactly');
  console.log('  what to build.\n');
}

main().catch((e) => { bad('Unexpected: ' + e.message); process.exit(1); });

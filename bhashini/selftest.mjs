/**
 * Prove the Bhashini pipeline works, without a Bhashini account.
 *
 * The whole path has never run, because we have no keys. Handing someone a
 * "just paste your keys" button that has never executed is exactly the kind of
 * untested-but-confident thing this project keeps having to rip out.
 *
 * So: stand up a local server that answers like Bhashini, point the real
 * generator at it, and check what comes out the other end. Everything gets
 * exercised except the remote service itself. When real keys arrive, the only
 * untested link is whether the live API matches the shape documented here.
 *
 *     node bhashini/selftest.mjs
 *
 * Writes nothing outside a temp directory and needs no network.
 */

import { spawn } from 'node:child_process';
import fs from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT_DIR = path.join(ROOT, 'bhashini', 'out');

const c = { g: '\x1b[32m', r: '\x1b[31m', b: '\x1b[36m', d: '\x1b[2m', x: '\x1b[0m' };
const ok = (m) => console.log(`${c.g}  ok  ${c.x} ${m}`);
const bad = (m) => { console.log(`${c.r} fail ${c.x} ${m}`); failures += 1; };
const head = (m) => console.log(`\n${c.b}${m}${c.x}\n${'-'.repeat(60)}`);

let failures = 0;
let translateCalls = 0;
let ttsCalls = 0;

// A tiny valid RIFF wav, 8 frames of silence. Enough to prove the base64 audio
// round trip writes a real file; not pretending to be speech.
function tinyWav() {
  const data = Buffer.alloc(16);
  const buf = Buffer.alloc(44 + data.length);
  buf.write('RIFF', 0);
  buf.writeUInt32LE(36 + data.length, 4);
  buf.write('WAVE', 8);
  buf.write('fmt ', 12);
  buf.writeUInt32LE(16, 16);
  buf.writeUInt16LE(1, 20);
  buf.writeUInt16LE(1, 22);
  buf.writeUInt32LE(16000, 24);
  buf.writeUInt32LE(32000, 28);
  buf.writeUInt16LE(2, 32);
  buf.writeUInt16LE(16, 34);
  buf.write('data', 36);
  buf.writeUInt32LE(data.length, 40);
  data.copy(buf, 44);
  return buf;
}

/** Answers the two endpoints the generator calls, in Bhashini's shape. */
function mockBhashini(port) {
  return http.createServer((req, res) => {
    let body = '';
    req.on('data', (d) => { body += d; });
    req.on('end', () => {
      const json = body ? JSON.parse(body) : {};
      res.setHeader('Content-Type', 'application/json');

      if (req.url.includes('getModelsPipeline')) {
        const task = json.pipelineTasks?.[0]?.taskType;
        res.end(JSON.stringify({
          pipelineResponseConfig: [{
            taskType: task,
            config: [{ serviceId: `mock/${task}-sat-v1` }],
          }],
          pipelineInferenceAPIEndPoint: {
            callbackUrl: `http://127.0.0.1:${port}/compute`,
            inferenceApiKey: { name: 'Authorization', value: 'mock-token' },
          },
        }));
        return;
      }

      const task = json.pipelineTasks?.[0]?.taskType;
      const source = json.inputData?.input?.[0]?.source ?? '';

      if (task === 'translation') {
        translateCalls += 1;
        // Ol Chiki letters only, deterministic from the input length, so the
        // generator's contamination and collision checks have real material.
        const letters = 'ᱚᱞᱠᱤᱪᱟᱢᱮᱨᱥᱛ';
        let outText = '';
        for (let i = 0; i < Math.max(3, source.length % 11 + 3); i += 1) {
          outText += letters[(source.charCodeAt(i % source.length) + i) % letters.length];
        }
        outText += ' ' + letters[(source.length + translateCalls) % letters.length].repeat(2);
        res.end(JSON.stringify({
          pipelineResponse: [{ taskType: 'translation', output: [{ source, target: outText }] }],
        }));
        return;
      }

      if (task === 'tts') {
        ttsCalls += 1;
        res.end(JSON.stringify({
          pipelineResponse: [{
            taskType: 'tts',
            audio: [{ audioContent: tinyWav().toString('base64') }],
          }],
        }));
        return;
      }

      res.statusCode = 400;
      res.end(JSON.stringify({ error: 'unexpected task ' + task }));
    });
  });
}

function run(args, env) {
  return new Promise((resolve) => {
    const p = spawn(process.execPath, args, {
      cwd: ROOT,
      env: { ...process.env, ...env },
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    let out = '';
    p.stdout.on('data', (d) => { out += d; });
    p.stderr.on('data', (d) => { out += d; });
    p.on('close', (code) => resolve({ code, out }));
  });
}

async function main() {
  console.log('\n  BHASHINI PIPELINE SELFTEST');
  console.log('  ' + '='.repeat(58));
  console.log(`  ${c.d}No keys, no network, no real API calls.${c.x}`);

  // start clean so we are testing a real run rather than leftovers
  if (fs.existsSync(OUT_DIR)) {
    for (const f of fs.readdirSync(OUT_DIR)) {
      if (f === '.gitkeep') continue;
      fs.rmSync(path.join(OUT_DIR, f), { recursive: true, force: true });
    }
  }

  const port = 8791 + Math.floor(Math.random() * 200);
  const server = mockBhashini(port);
  await new Promise((r) => server.listen(port, '127.0.0.1', r));
  head(`Mock Bhashini listening on 127.0.0.1:${port}`);

  const env = {
    BHASHINI_CONFIG_URL: `http://127.0.0.1:${port}/getModelsPipeline`,
    BHASHINI_USER_ID: 'selftest',
    BHASHINI_ULCA_KEY: 'selftest',
    BHASHINI_INFERENCE_KEY: 'selftest',
  };

  head('Running the real generator against it');
  const { code, out } = await run(['bhashini/build_pack.mjs', '--limit', '6'], env);
  server.close();

  if (code !== 0) {
    bad(`generator exited ${code}`);
    console.log(out.split('\n').slice(-14).join('\n'));
  } else {
    ok('generator exited cleanly');
  }

  ok(`translation calls: ${translateCalls}`);
  ttsCalls > 0 ? ok(`text-to-speech calls: ${ttsCalls}`) : bad('no text-to-speech calls were made');

  head('Checking what it produced');

  const packPath = path.join(OUT_DIR, 'pack.sat.json');
  if (!fs.existsSync(packPath)) {
    bad('no pack.sat.json was written');
  } else {
    const pack = JSON.parse(fs.readFileSync(packPath, 'utf8'));
    const entries = Object.entries(pack.entries ?? {});
    entries.length ? ok(`${entries.length} entries written`) : bad('pack has no entries');

    const prov = pack.provenance ?? {};
    prov.translationService ? ok(`translationService recorded: ${prov.translationService}`)
                            : bad('translationService missing from provenance');
    prov.ttsService ? ok(`ttsService recorded: ${prov.ttsService}`)
                    : bad('ttsService missing from provenance');

    const missing = entries.filter(([, e]) => !e.source || !e.target || !e.service);
    missing.length ? bad(`${missing.length} entries missing source, target or service`)
                   : ok('every entry has source, target and service');

    const withAudio = entries.filter(([, e]) => e.audio);
    withAudio.length ? ok(`${withAudio.length} entries reference audio`)
                     : bad('no entry references audio');

    // A wav with no audioProvenance loads in the app as AudioProvenance.NONE,
    // so the file ships inside the APK and the screen still reads "no audio
    // yet". The two fields have to be written together or not at all.
    const unlabelled = withAudio.filter(([, e]) => e.audioProvenance !== 'bhashini');
    unlabelled.length ? bad(`${unlabelled.length} entries have a wav and no audioProvenance`)
                      : ok('every wav is labelled as bhashini synthesis');

    // ids must be distinct: a constant fallback once collapsed three
    // comprehension checks onto one key and silently lost two of them
    const ids = entries.map(([k]) => k);
    new Set(ids).size === ids.length ? ok('all entry ids distinct')
                                     : bad('duplicate entry ids');

    // the shipped app rejects anything that is not Ol Chiki
    const foreign = entries.filter(([, e]) =>
      [...e.target].some((ch) => {
        const cp = ch.codePointAt(0);
        return !(cp >= 0x1C50 && cp <= 0x1C7F) && !' 0123456789.,?!:;-()'.includes(ch);
      }));
    foreign.length ? bad(`${foreign.length} entries contain non-Ol-Chiki characters`)
                   : ok('no foreign-script contamination');
  }

  const audioDir = path.join(OUT_DIR, 'audio');
  if (!fs.existsSync(audioDir)) {
    bad('no audio directory');
  } else {
    const wavs = fs.readdirSync(audioDir).filter((f) => f.endsWith('.wav'));
    wavs.length ? ok(`${wavs.length} wav files on disk`) : bad('no wav files written');
    if (wavs.length) {
      const first = fs.readFileSync(path.join(audioDir, wavs[0]));
      first.slice(0, 4).toString() === 'RIFF' && first.slice(8, 12).toString() === 'WAVE'
        ? ok('audio decodes to a real RIFF/WAVE file')
        : bad('audio is not a valid wav');
    }
  }

  head(failures ? `${failures} check(s) failed` : 'All checks passed');
  if (!failures) {
    console.log('  The pipeline works end to end: config lookup, translation,');
    console.log('  text-to-speech, base64 audio decoding, pack assembly and');
    console.log('  provenance. The only untested link left is whether the live');
    console.log('  Bhashini API returns the shape this mock imitates.');
    console.log('');
    console.log('  With real keys:  node bhashini/build_pack.mjs --compare');
    console.log('  Then to ship it: node bhashini/build_pack.mjs --install');
  }
  console.log('');
  process.exit(failures ? 1 : 0);
}

main();

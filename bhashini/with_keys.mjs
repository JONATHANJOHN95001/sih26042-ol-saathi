// Loads the BHASHINI_* values from the repo's local.properties, which is
// gitignored, into the environment and runs a script with them. It prints only
// names and lengths, never a value, so a key cannot leak into a log or a chat
// transcript through this path.
//
//   node bhashini/with_keys.mjs bhashini/test-connection.mjs
//   node bhashini/with_keys.mjs bhashini/pack_audio.mjs --lang tam
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const env = { ...process.env };
const loaded = [];

for (const line of readFileSync(path.join(ROOT, 'local.properties'), 'utf8').split(/\r?\n/)) {
  const m = line.match(/^(BHASHINI_[A-Z_]+)=(.*)$/);
  if (!m) continue;
  const value = m[2].trim();
  if (!value || value === 'PASTE_HERE') {
    console.error(`${m[1]} is not filled in yet in local.properties.`);
    process.exit(2);
  }
  env[m[1]] = value;
  loaded.push(`${m[1]} (${value.length} chars)`);
}

if (loaded.length === 0) {
  console.error('No BHASHINI_* values found in local.properties.');
  process.exit(2);
}
console.log('Loaded: ' + loaded.join(', '));

const [script, ...args] = process.argv.slice(2);
if (!script) {
  console.error('Usage: node bhashini/with_keys.mjs <script> [args]');
  process.exit(2);
}
const r = spawnSync(process.execPath, [path.resolve(ROOT, script), ...args], { stdio: 'inherit', env, cwd: ROOT });
process.exit(r.status ?? 1);

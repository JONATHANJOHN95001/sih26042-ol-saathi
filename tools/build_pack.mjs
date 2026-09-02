/**
 * ⚠️  DO NOT RUN THIS SCRIPT. ⚠️
 *
 * This file used to write directly into app/src/main/assets/pack/, which
 * means every run silently replaced the shipped content with whatever
 * Bhashini returned. A bad translation, a failed TTS call that left the pack
 * half-populated, or a crash mid-run would have produced a broken APK that
 * looked normal until a teacher tried to use it.
 *
 * The safe generator is:
 *
 *     node bhashini/build_pack.mjs          # writes to bhashini/out/
 *     node bhashini/build_pack.mjs --install # copies into the app AFTER you review
 *
 * See bhashini/RUNBOOK.md for the full sequence.
 */

console.error('');
console.error('  This script writes directly into the live app assets and is no longer');
console.error('  safe to run. Use the generator in bhashini/ instead:');
console.error('');
console.error('    node bhashini/build_pack.mjs --dry-run');
console.error('    node bhashini/build_pack.mjs --compare');
console.error('    node bhashini/build_pack.mjs --install');
console.error('');
console.error('  See bhashini/RUNBOOK.md for details.');
console.error('');
process.exit(1);

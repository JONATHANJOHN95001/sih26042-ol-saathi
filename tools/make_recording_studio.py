# -*- coding: utf-8 -*-
"""
Build a self-contained recording studio for a Santali speaker.

Why this exists
---------------
The problem statement asks for synthesised audio in the tribal language. There
is no way to produce it from a model: Meta's MMS covers 1,143 languages and
Santali is not among them, and Bhashini is the only service that offers Santali
TTS. So the honest remaining source is a person reading the lines aloud.

That is not a downgrade. A recording by a Santali speaker is better evidence
than synthesised speech, and it is the same hour of the same person's time that
the translation review already needs.

What it produces
----------------
One HTML file. It opens in any browser with no install and no network, shows
each line in Hindi and Ol Chiki with the English gloss, records from the
microphone, and exports every clip as a zip of 16-bit PCM WAV files named by
entry id. tools/apply_audio.py then drops that zip into the app.

The Ol Chiki font is embedded, because the speaker's phone will not have it and
every character would otherwise render as an empty box.
"""
import base64
import io
import json
import os

PACK = 'app/src/main/assets/pack/pack.sat.json'
FONT_OL = 'app/src/main/assets/fonts/NotoSansOlChiki-Regular.ttf'
FONT_DEVA = 'app/src/main/assets/fonts/NotoSansDevanagari-Regular.ttf'
OUT = 'verification/santali-recording-studio.html'


def b64(path):
    with open(path, 'rb') as fh:
        return base64.b64encode(fh.read()).decode('ascii')


HTML = u"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Ol Saathi · Santali recording studio</title>
<style>
@font-face{font-family:OlChiki;src:url(data:font/ttf;base64,__OL__) format('truetype');}
@font-face{font-family:Deva;src:url(data:font/ttf;base64,__DEVA__) format('truetype');}
*{box-sizing:border-box}
body{margin:0;font:16px/1.55 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;
     background:#10141c;color:#e8ecf4;-webkit-text-size-adjust:100%}
header{position:sticky;top:0;z-index:5;background:#161c28;border-bottom:1px solid #263143;
       padding:14px 18px;display:flex;gap:14px;align-items:center;flex-wrap:wrap}
h1{font-size:16px;margin:0;font-weight:650;letter-spacing:.2px}
.sub{color:#8b98ad;font-size:13px}
.bar{flex:1;min-width:120px;height:6px;background:#263143;border-radius:99px;overflow:hidden}
.bar i{display:block;height:100%;background:#4ade80;width:0;transition:width .25s}
main{max-width:760px;margin:0 auto;padding:22px 18px 120px}
.card{background:#161c28;border:1px solid #263143;border-radius:14px;padding:22px;margin-bottom:16px}
.meta{display:flex;justify-content:space-between;color:#8b98ad;font-size:12px;
      text-transform:uppercase;letter-spacing:.6px;margin-bottom:16px}
.hi{font-family:Deva,serif;font-size:27px;line-height:1.5;margin:0 0 10px}
.ol{font-family:OlChiki,serif;font-size:30px;line-height:1.7;color:#8ab4ff;margin:0 0 10px}
.en{color:#8b98ad;font-size:14px;font-style:italic;margin:0}
.ctl{display:flex;gap:10px;margin-top:20px;flex-wrap:wrap}
button{font:inherit;font-weight:600;border:0;border-radius:10px;padding:13px 20px;
       cursor:pointer;background:#263143;color:#e8ecf4;transition:.15s}
button:hover:not(:disabled){filter:brightness(1.25)}
button:disabled{opacity:.35;cursor:not-allowed}
.rec{background:#dc2626;color:#fff;min-width:150px}
.rec.on{background:#ef4444;animation:pulse 1s infinite}
@keyframes pulse{50%{opacity:.55}}
.ok{background:#16a34a;color:#fff}
.done{color:#4ade80;font-size:13px;margin-top:12px;min-height:18px}
nav{position:fixed;bottom:0;left:0;right:0;background:#161c28;border-top:1px solid #263143;
    padding:12px 18px;display:flex;gap:10px;justify-content:center;align-items:center}
nav .n{color:#8b98ad;font-size:13px;min-width:74px;text-align:center}
.note{background:#1a2333;border-left:3px solid #4ade80;padding:14px 16px;border-radius:0 10px 10px 0;
      margin-bottom:22px;font-size:14px;color:#b9c4d4}
.note b{color:#e8ecf4}
kbd{background:#263143;border-radius:5px;padding:2px 7px;font-size:12px;font-family:inherit}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(46px,1fr));gap:6px;margin-top:8px}
.grid a{display:block;text-align:center;padding:7px 0;border-radius:7px;background:#263143;
        color:#8b98ad;font-size:11px;text-decoration:none;cursor:pointer}
.grid a.has{background:#14532d;color:#4ade80}
.grid a.cur{outline:2px solid #8ab4ff}
</style>
</head>
<body>
<header>
  <h1>Santali recording studio</h1>
  <div class="bar"><i id="bar"></i></div>
  <div class="sub" id="count">0 of 0</div>
  <button id="export" class="ok" disabled>Export</button>
</header>

<main>
  <div class="note">
    <b>Thank you for doing this.</b> Read each line aloud in Santali, naturally,
    as you would say it to a class of six-year-olds. Press record, speak, press
    stop. If it comes out wrong just record it again, the new one replaces the
    old. Nothing is uploaded anywhere; everything stays on this device until you
    press Export.
    <div style="margin-top:10px">
      <kbd>space</kbd> record or stop &nbsp; <kbd>&larr;</kbd> <kbd>&rarr;</kbd> move
      &nbsp; <kbd>P</kbd> play back
    </div>
  </div>

  <div class="card">
    <div class="meta"><span id="id"></span><span id="domain"></span></div>
    <p class="hi" id="hi"></p>
    <p class="ol" id="ol"></p>
    <p class="en" id="en"></p>
    <div class="ctl">
      <button id="rec" class="rec">Record</button>
      <button id="play" disabled>Play back</button>
      <button id="clear" disabled>Discard</button>
    </div>
    <div class="done" id="status"></div>
  </div>

  <div class="grid" id="grid"></div>
</main>

<nav>
  <button id="prev">Previous</button>
  <div class="n" id="pos"></div>
  <button id="next">Next</button>
</nav>

<script>
const ENTRIES = __ENTRIES__;
let idx = 0;
const clips = {};              // id -> {blob, url}
let ctx, stream, node, src, chunks = [], recording = false, sampleRate = 48000;

const $ = id => document.getElementById(id);

// ── WAV encoding ──────────────────────────────────────────────────────
// MediaRecorder gives webm/opus, whose container support varies across the
// cheap Android tablets this has to play on. Capturing raw PCM and writing a
// plain 16-bit WAV avoids that question entirely.
function downsample(buf, from, to) {
  if (to >= from) return buf;
  const ratio = from / to, out = new Float32Array(Math.round(buf.length / ratio));
  let o = 0, i = 0;
  while (o < out.length) {
    const next = Math.round((o + 1) * ratio);
    let sum = 0, n = 0;
    for (let j = i; j < next && j < buf.length; j++) { sum += buf[j]; n++; }
    out[o++] = n ? sum / n : 0;
    i = next;
  }
  return out;
}

function encodeWav(samples, rate) {
  const buf = new ArrayBuffer(44 + samples.length * 2);
  const v = new DataView(buf);
  const str = (off, s) => { for (let i = 0; i < s.length; i++) v.setUint8(off + i, s.charCodeAt(i)); };
  str(0, 'RIFF'); v.setUint32(4, 36 + samples.length * 2, true); str(8, 'WAVE');
  str(12, 'fmt '); v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true);
  v.setUint32(24, rate, true); v.setUint32(28, rate * 2, true);
  v.setUint16(32, 2, true); v.setUint16(34, 16, true);
  str(36, 'data'); v.setUint32(40, samples.length * 2, true);
  let off = 44;
  for (let i = 0; i < samples.length; i++, off += 2) {
    const s = Math.max(-1, Math.min(1, samples[i]));
    v.setInt16(off, s < 0 ? s * 0x8000 : s * 0x7fff, true);
  }
  return new Blob([buf], { type: 'audio/wav' });
}

// ── ZIP writing, STORE method, no compression ─────────────────────────
// A few hundred KB of WAV does not need deflating, and STORE keeps this to
// thirty lines with no library and no network.
function crc32(u8) {
  let c, t = crc32.t;
  if (!t) {
    t = crc32.t = [];
    for (let n = 0; n < 256; n++) {
      c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      t[n] = c >>> 0;
    }
  }
  c = 0xffffffff;
  for (let i = 0; i < u8.length; i++) c = t[(c ^ u8[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

async function makeZip(files) {
  const enc = new TextEncoder(), locals = [], central = [];
  let offset = 0;
  for (const f of files) {
    const name = enc.encode(f.name);
    const data = new Uint8Array(await f.blob.arrayBuffer());
    const crc = crc32(data);
    const h = new DataView(new ArrayBuffer(30));
    h.setUint32(0, 0x04034b50, true); h.setUint16(4, 20, true); h.setUint16(6, 0, true);
    h.setUint16(8, 0, true); h.setUint16(10, 0, true); h.setUint16(12, 0, true);
    h.setUint32(14, crc, true); h.setUint32(18, data.length, true);
    h.setUint32(22, data.length, true); h.setUint16(26, name.length, true); h.setUint16(28, 0, true);
    locals.push(new Uint8Array(h.buffer), name, data);

    const c = new DataView(new ArrayBuffer(46));
    c.setUint32(0, 0x02014b50, true); c.setUint16(4, 20, true); c.setUint16(6, 20, true);
    c.setUint32(16, crc, true); c.setUint32(20, data.length, true);
    c.setUint32(24, data.length, true); c.setUint16(28, name.length, true);
    c.setUint32(42, offset, true);
    central.push(new Uint8Array(c.buffer), name);
    offset += 30 + name.length + data.length;
  }
  let cSize = 0;
  for (const p of central) cSize += p.length;
  const end = new DataView(new ArrayBuffer(22));
  end.setUint32(0, 0x06054b50, true);
  end.setUint16(8, files.length, true); end.setUint16(10, files.length, true);
  end.setUint32(12, cSize, true); end.setUint32(16, offset, true);
  return new Blob([...locals, ...central, new Uint8Array(end.buffer)], { type: 'application/zip' });
}

// ── recording ─────────────────────────────────────────────────────────
async function startRec() {
  if (!stream) {
    stream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true }
    });
    ctx = new (window.AudioContext || window.webkitAudioContext)();
    sampleRate = ctx.sampleRate;
    src = ctx.createMediaStreamSource(stream);
    node = ctx.createScriptProcessor(4096, 1, 1);
    node.onaudioprocess = e => {
      if (recording) chunks.push(new Float32Array(e.inputBuffer.getChannelData(0)));
    };
    src.connect(node); node.connect(ctx.destination);
  }
  if (ctx.state === 'suspended') await ctx.resume();
  chunks = []; recording = true;
  $('rec').textContent = 'Stop'; $('rec').classList.add('on');
  $('status').textContent = 'Recording, speak now.';
}

function stopRec() {
  recording = false;
  $('rec').textContent = 'Record'; $('rec').classList.remove('on');
  let len = 0;
  for (const c of chunks) len += c.length;
  if (!len) { $('status').textContent = 'Nothing was captured, try again.'; return; }
  const flat = new Float32Array(len);
  let o = 0;
  for (const c of chunks) { flat.set(c, o); o += c.length; }
  const down = downsample(flat, sampleRate, 16000);
  const blob = encodeWav(down, 16000);
  const e = ENTRIES[idx];
  if (clips[e.id]) URL.revokeObjectURL(clips[e.id].url);
  clips[e.id] = { blob, url: URL.createObjectURL(blob) };
  const secs = (down.length / 16000).toFixed(1);
  $('status').textContent = 'Saved, ' + secs + 's, ' + Math.round(blob.size / 1024) + ' KB.';
  render();
}

// ── view ──────────────────────────────────────────────────────────────
function render() {
  const e = ENTRIES[idx];
  $('id').textContent = e.id;
  $('domain').textContent = e.domain || '';
  $('hi').textContent = e.hi;
  $('ol').textContent = e.ol;
  $('en').textContent = e.en;
  $('pos').textContent = (idx + 1) + ' / ' + ENTRIES.length;
  const n = Object.keys(clips).length;
  $('count').textContent = n + ' of ' + ENTRIES.length + ' recorded';
  $('bar').style.width = (100 * n / ENTRIES.length) + '%';
  $('export').disabled = n === 0;
  $('play').disabled = !clips[e.id];
  $('clear').disabled = !clips[e.id];
  $('prev').disabled = idx === 0;
  $('next').disabled = idx === ENTRIES.length - 1;
  if (!recording) $('status').textContent = clips[e.id] ? 'Recorded. Record again to replace it.' : '';

  const g = $('grid');
  g.innerHTML = '';
  ENTRIES.forEach((x, i) => {
    const a = document.createElement('a');
    a.textContent = x.id.replace('neema-dadi.', '');
    if (clips[x.id]) a.className = 'has';
    if (i === idx) a.className += ' cur';
    a.onclick = () => { if (!recording) { idx = i; render(); } };
    g.appendChild(a);
  });
}

$('rec').onclick = () => recording ? stopRec() : startRec();
$('play').onclick = () => { const c = clips[ENTRIES[idx].id]; if (c) new Audio(c.url).play(); };
$('clear').onclick = () => {
  const e = ENTRIES[idx];
  if (clips[e.id]) { URL.revokeObjectURL(clips[e.id].url); delete clips[e.id]; render(); }
};
$('prev').onclick = () => { if (idx > 0) { idx--; render(); } };
$('next').onclick = () => { if (idx < ENTRIES.length - 1) { idx++; render(); } };

$('export').onclick = async () => {
  const files = ENTRIES.filter(e => clips[e.id])
                       .map(e => ({ name: e.id + '.wav', blob: clips[e.id].blob }));
  const manifest = {
    recordedAt: new Date().toISOString(),
    provenance: 'native',
    count: files.length,
    total: ENTRIES.length,
    ids: files.map(f => f.name.replace('.wav', ''))
  };
  files.push({ name: 'manifest.json',
               blob: new Blob([JSON.stringify(manifest, null, 2)], { type: 'application/json' }) });
  const zip = await makeZip(files);
  const a = document.createElement('a');
  a.href = URL.createObjectURL(zip);
  a.download = 'santali-recordings.zip';
  a.click();
};

document.onkeydown = ev => {
  if (ev.target.tagName === 'INPUT') return;
  if (ev.code === 'Space') { ev.preventDefault(); $('rec').click(); }
  else if (ev.key === 'ArrowLeft') $('prev').click();
  else if (ev.key === 'ArrowRight') $('next').click();
  else if (ev.key === 'p' || ev.key === 'P') $('play').click();
};

window.onbeforeunload = e => {
  if (Object.keys(clips).length) { e.preventDefault(); return e.returnValue = 'Recordings not exported yet.'; }
};

render();
</script>
</body>
</html>
"""


def main():
    pack = json.load(io.open(PACK, encoding='utf-8'))
    entries = []
    for eid in sorted(pack['entries']):
        e = pack['entries'][eid]
        if not e.get('target'):
            continue
        entries.append({
            'id': eid,
            'hi': e.get('source', ''),
            'ol': e.get('target', ''),
            'en': e.get('en', ''),
            'domain': e.get('nipunDomain', ''),
        })

    html = (HTML
            .replace('__OL__', b64(FONT_OL))
            .replace('__DEVA__', b64(FONT_DEVA))
            .replace('__ENTRIES__', json.dumps(entries, ensure_ascii=False)))

    outdir = os.path.dirname(OUT)
    if outdir and not os.path.isdir(outdir):
        os.makedirs(outdir)
    io.open(OUT, 'w', encoding='utf-8', newline='\n').write(html)
    print('wrote %s' % OUT)
    print('  %d lines to record' % len(entries))
    print('  %.1f MB (fonts embedded so Ol Chiki renders anywhere)'
          % (os.path.getsize(OUT) / 1024.0 / 1024.0))


if __name__ == '__main__':
    main()

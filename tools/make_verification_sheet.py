# -*- coding: utf-8 -*-
"""
Build a review sheet a Santali speaker can actually fill in.

Right now every string in the app is labelled "Machine translation ·
IndicTrans2". One hour from someone who reads Ol Chiki turns that into
"Verified by a native speaker", which is the single largest credibility upgrade
available to this project and the one thing no other team is likely to have.

So the job of this file is to remove every excuse not to do that review.

  - One self-contained HTML file. No install, no login, no internet.
  - Both fonts embedded as base64, so Ol Chiki renders on the reviewer's phone
    even though Android and Windows ship no Ol Chiki font. Without this they
    see empty boxes and the review is impossible.
  - Works on a phone, because it will be sent over WhatsApp.
  - Prints cleanly, because some people would rather use a pen.
  - Marks and corrections are saved in the browser as you go, and export to a
    JSON file that feeds straight back into the pack.

    python tools/make_verification_sheet.py
"""
import base64
import io
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
PACK = ROOT / 'app' / 'src' / 'main' / 'assets' / 'pack' / 'pack.sat.json'
FONTS = ROOT / 'app' / 'src' / 'main' / 'assets' / 'fonts'
OUT = ROOT / 'verification' / 'santali-review-sheet.html'


def b64(path):
    return base64.b64encode(path.read_bytes()).decode('ascii')


def main():
    pack = json.loads(PACK.read_text(encoding='utf-8'))
    prov = pack.get('provenance', {})

    order = {'phrase': 0, 'lesson': 1, 'check': 2}
    rows = sorted(pack['entries'].items(),
                  key=lambda kv: (order.get(kv[1].get('kind'), 9), kv[0]))

    olck = b64(FONTS / 'NotoSansOlChiki-Regular.ttf')
    deva = b64(FONTS / 'NotoSansDevanagari-Regular.ttf')

    items_json = json.dumps(
        [{'id': k, 'hi': v['source'], 'en': v.get('en', ''),
          'sat': v['target'], 'kind': v.get('kind', '')} for k, v in rows],
        ensure_ascii=False)

    html = HTML % {
        'olck': olck,
        'deva': deva,
        'count': len(rows),
        'service': prov.get('translationService', 'unknown'),
        'generated': (pack.get('generated') or '')[:10],
        'items': items_json,
    }

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(html, encoding='utf-8')
    kb = len(html.encode('utf-8')) // 1024
    print('wrote %s  (%d KB, %d entries)' % (OUT.relative_to(ROOT), kb, len(rows)))
    print('Both fonts are embedded, so Ol Chiki renders even on a phone with no')
    print('Ol Chiki font installed. Send it over WhatsApp as a file.')


HTML = r'''<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Ol Saathi — Santali review sheet</title>
<style>
  @font-face { font-family:"OlChiki"; src:url(data:font/ttf;base64,%(olck)s) format("truetype"); }
  @font-face { font-family:"Deva";    src:url(data:font/ttf;base64,%(deva)s) format("truetype"); }

  :root { --ink:#1c1c1c; --muted:#6b6b6b; --line:#dcdcdc; --forest:#1b4332; --terra:#b85042; --ok:#2d6a4f; }
  * { box-sizing:border-box; }
  body { margin:0; font-family:system-ui,Segoe UI,Roboto,sans-serif; color:var(--ink);
         background:#fff; line-height:1.5; }
  header { background:var(--forest); color:#fff; padding:22px 18px; }
  header h1 { margin:0 0 4px; font-size:21px; }
  header p { margin:0; color:#cfe0d6; font-size:14px; }
  .wrap { max-width:900px; margin:0 auto; padding:18px; }

  .brief { background:#f0f5f1; border:1px solid #cfe0d6; border-radius:10px;
           padding:14px 16px; margin:18px 0; font-size:14.5px; }
  .brief b { color:var(--forest); }
  .brief .hi { font-family:"Deva",serif; }

  .who { display:flex; flex-wrap:wrap; gap:10px; margin:16px 0 24px; }
  .who input { flex:1 1 200px; padding:9px 11px; border:1px solid var(--line);
               border-radius:8px; font-size:14px; font-family:inherit; }

  .item { border:1px solid var(--line); border-radius:10px; padding:14px;
          margin-bottom:12px; }
  .item.done-ok  { border-color:var(--ok);    background:#f4faf6; }
  .item.done-bad { border-color:var(--terra); background:#fdf5f4; }
  .n { font-size:11px; color:var(--muted); letter-spacing:.08em; text-transform:uppercase; }
  .en { color:var(--muted); font-size:13.5px; margin:2px 0 6px; }
  .hi { font-family:"Deva",serif; font-size:19px; margin-bottom:6px; }
  .sat { font-family:"OlChiki",serif; font-size:23px; color:var(--forest);
         margin-bottom:10px; line-height:1.7; }

  .marks { display:flex; gap:8px; flex-wrap:wrap; }
  .marks button { flex:0 0 auto; padding:8px 14px; border-radius:20px; cursor:pointer;
                  border:1px solid var(--line); background:#fff; font-size:14px;
                  font-family:inherit; }
  .marks button.sel-ok  { background:var(--ok);    color:#fff; border-color:var(--ok); }
  .marks button.sel-bad { background:var(--terra); color:#fff; border-color:var(--terra); }
  .fix { display:none; width:100%%; margin-top:10px; padding:10px; font-size:18px;
         font-family:"OlChiki",serif; border:1px solid var(--terra); border-radius:8px; }
  .item.done-bad .fix { display:block; }

  .bar { position:sticky; bottom:0; background:#fff; border-top:1px solid var(--line);
         padding:12px 18px; display:flex; gap:10px; align-items:center; flex-wrap:wrap; }
  .bar .count { flex:1 1 auto; font-size:14px; color:var(--muted); }
  .bar button { padding:10px 16px; border-radius:8px; border:0; cursor:pointer;
                background:var(--forest); color:#fff; font-size:14px; font-family:inherit; }
  .bar button.ghost { background:#fff; color:var(--ink); border:1px solid var(--line); }

  footer { padding:24px 18px 40px; color:var(--muted); font-size:12.5px; }

  @media print {
    .marks, .bar, .who input { display:none; }
    .item { break-inside:avoid; page-break-inside:avoid; }
    .fix { display:block !important; height:44px; border-color:var(--line); }
    header { background:#fff; color:#000; border-bottom:2px solid #000; }
    header p { color:#333; }
  }
</style>
</head>
<body>

<header>
  <h1>Ol Saathi — Santali review</h1>
  <p>%(count)s phrases translated by %(service)s on %(generated)s. Please check them.</p>
</header>

<div class="wrap">

  <div class="brief">
    <p style="margin:0 0 8px"><b>What this is.</b> We are building a free app that helps a
    Hindi-speaking teacher deliver primary school lessons in Santali, for Smart India
    Hackathon problem statement SIH26042 set by the Government of Jharkhand.</p>
    <p style="margin:0 0 8px"><b>What we need.</b> A machine translated these. Nobody who reads
    Santali has checked them. For each one, tap <b>Correct</b> or <b>Wrong</b>. If it is wrong
    and you can write the right version, please do. If you are unsure, skip it.</p>
    <p style="margin:0" class="hi"><b>हमें क्या चाहिए:</b> ये अनुवाद मशीन ने किए हैं। कृपया हर वाक्य
    देखकर बताइए कि संताली सही है या नहीं। गलत हो तो सही रूप लिख दीजिए।</p>
  </div>

  <div class="who">
    <input id="who-name"    placeholder="Your name">
    <input id="who-contact" placeholder="Phone or email (optional)">
    <input id="who-note"    placeholder="Where you are from / your Santali background">
  </div>

  <div id="list"></div>
</div>

<div class="bar">
  <span class="count" id="count">0 reviewed</span>
  <button class="ghost" onclick="window.print()">Print</button>
  <button onclick="save()">Download my answers</button>
</div>

<footer>
  Team INNOV8 · JAIN (Deemed-to-be University) · SIH26042.
  Nothing you type leaves this file until you press download. There is no internet
  connection and no tracking here.
</footer>

<script>
const ITEMS = %(items)s;
const KEY = "olsaathi-review";
let state = {};
try { state = JSON.parse(localStorage.getItem(KEY) || "{}"); } catch (e) { state = {}; }

const KIND = { phrase: "Classroom phrase", lesson: "Lesson line", check: "Question" };

function render() {
  const list = document.getElementById("list");
  list.innerHTML = "";
  ITEMS.forEach((it, i) => {
    const st = state[it.id] || {};
    const div = document.createElement("div");
    div.className = "item" + (st.verdict === "ok" ? " done-ok" : st.verdict === "bad" ? " done-bad" : "");
    div.innerHTML =
      '<div class="n">' + (i + 1) + " of " + ITEMS.length + " · " + (KIND[it.kind] || it.kind) + '</div>' +
      '<div class="en"></div><div class="hi"></div><div class="sat"></div>' +
      '<div class="marks">' +
        '<button class="' + (st.verdict === "ok" ? "sel-ok" : "") + '" data-v="ok">Correct</button>' +
        '<button class="' + (st.verdict === "bad" ? "sel-bad" : "") + '" data-v="bad">Wrong</button>' +
      '</div>' +
      '<input class="fix" placeholder="Write the correct Santali here">';
    div.querySelector(".en").textContent = it.en;
    div.querySelector(".hi").textContent = it.hi;
    div.querySelector(".sat").textContent = it.sat;
    const fix = div.querySelector(".fix");
    fix.value = st.fix || "";
    fix.addEventListener("input", () => { mark(it.id, state[it.id].verdict, fix.value); });
    div.querySelectorAll(".marks button").forEach((b) => {
      b.addEventListener("click", () => mark(it.id, b.dataset.v, fix.value));
    });
    list.appendChild(div);
  });
  const n = Object.values(state).filter((s) => s.verdict).length;
  document.getElementById("count").textContent =
    n + " of " + ITEMS.length + " reviewed";
}

function mark(id, verdict, fix) {
  state[id] = { verdict: verdict, fix: fix || "" };
  localStorage.setItem(KEY, JSON.stringify(state));
  render();
}

function save() {
  const out = {
    reviewer: {
      name: document.getElementById("who-name").value,
      contact: document.getElementById("who-contact").value,
      note: document.getElementById("who-note").value,
    },
    reviewedAt: new Date().toISOString(),
    pack: { service: "%(service)s", generated: "%(generated)s" },
    verdicts: state,
  };
  const blob = new Blob([JSON.stringify(out, null, 2)], { type: "application/json" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = "santali-review.json";
  a.click();
}

render();
</script>
</body>
</html>
'''

if __name__ == '__main__':
    main()

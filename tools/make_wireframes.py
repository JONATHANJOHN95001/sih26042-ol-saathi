# -*- coding: utf-8 -*-
"""
Build the Ol Saathi screen wireframes, with the real scripts in them.

Stitch produced a sensible design system but rendered every Santali line as
empty boxes, because its renderer has no Ol Chiki font. For an app whose entire
purpose is displaying Ol Chiki, a mockup that cannot show Ol Chiki is not a
mockup of this app.

So the palette and type scale below come from Stitch's "Rural Education
Framework", and the screens are drawn here with the fonts that actually ship in
the APK, using real strings from the real pack.

    python tools/make_wireframes.py

Output is one self-contained HTML file. Open it, or hand it to whoever is
building the screens.
"""
import base64
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
PACK = ROOT / 'app' / 'src' / 'main' / 'assets' / 'pack' / 'pack.sat.json'
FONTS = ROOT / 'app' / 'src' / 'main' / 'assets' / 'fonts'
OUT = ROOT / 'design' / 'wireframes.html'


def b64(p):
    return base64.b64encode(p.read_bytes()).decode('ascii')


def main():
    pack = json.loads(PACK.read_text(encoding='utf-8'))
    e = pack['entries']

    def get(eid):
        v = e.get(eid, {})
        return v.get('source', ''), v.get('target', ''), v.get('en', '')

    p01 = get('p01')
    lesson = [get('neema-dadi.l%d' % i) for i in range(1, 5)]
    checks = [get('neema-dadi.c%d' % i) for i in range(1, 4)]
    phrases = [get('p%02d' % i) for i in (6, 10, 18, 14, 27, 33)]

    html = TEMPLATE % {
        'olck': b64(FONTS / 'NotoSansOlChiki-Regular.ttf'),
        'deva': b64(FONTS / 'NotoSansDevanagari-Regular.ttf'),
        'p01_hi': p01[0], 'p01_sat': p01[1],
        'l1_hi': lesson[0][0], 'l1_sat': lesson[0][1], 'l1_en': lesson[0][2],
        'l2_hi': lesson[1][0], 'l2_sat': lesson[1][1],
        'c1_hi': checks[0][0], 'c1_sat': checks[0][1],
        'chips': ''.join(
            '<span class="chip">%s</span>' % h for h, _s, _en in phrases),
        'total': len(e),
    }

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(html, encoding='utf-8')
    print('wrote %s (%d KB)' % (OUT.relative_to(ROOT), len(html.encode()) // 1024))
    print('Real Ol Chiki, real Devanagari, real strings from the shipped pack.')


TEMPLATE = r'''<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Ol Saathi — screens</title>
<style>
@font-face{font-family:"OlChiki";src:url(data:font/ttf;base64,%(olck)s) format("truetype");}
@font-face{font-family:"Deva";src:url(data:font/ttf;base64,%(deva)s) format("truetype");}
:root{
  --forest:#1B4332; --forest2:#2D6A4F; --terra:#B85042; --ochre:#E9A03B;
  --bg:#FAFAF7; --card:#FFFFFF; --line:#D1D5D1; --ink:#1A1C1B; --muted:#5F6B64;
  --ok:#2D6A4F;
}
*{box-sizing:border-box;}
body{margin:0;background:#EDEEEA;color:var(--ink);
     font-family:"Deva",system-ui,Segoe UI,Roboto,sans-serif;line-height:1.5;}
.page{max-width:1500px;margin:0 auto;padding:28px 20px 60px;}
h1{font-size:26px;margin:0 0 4px;color:var(--forest);}
.sub{color:var(--muted);margin:0 0 8px;font-size:15px;}
.note{background:#fff;border:1px solid var(--line);border-left:4px solid var(--ochre);
      border-radius:10px;padding:14px 16px;margin:18px 0 26px;font-size:14.5px;}
.grid{display:flex;gap:22px;flex-wrap:wrap;align-items:flex-start;}

/* a tablet frame */
.dev{width:440px;background:#111;border-radius:22px;padding:10px;flex:0 0 auto;}
.scr{background:var(--bg);border-radius:14px;overflow:hidden;height:640px;
     display:flex;flex-direction:column;}
.cap{color:#3b3f3c;font-size:13px;margin:10px 2px 0;font-family:system-ui,sans-serif;}
.cap b{color:var(--forest);}

.bar{background:var(--forest);color:#fff;padding:11px 14px;display:flex;
     align-items:center;justify-content:space-between;flex:0 0 auto;}
.bar .t{font-weight:700;font-size:15px;font-family:system-ui,sans-serif;}
.bar .r{font-size:11px;background:rgba(255,255,255,.16);padding:3px 9px;border-radius:999px;
        font-family:system-ui,sans-serif;}
.body{padding:14px;overflow:hidden;flex:1 1 auto;}
.lab{font-size:9.5px;letter-spacing:.11em;color:var(--muted);font-weight:700;
     font-family:system-ui,sans-serif;margin-bottom:3px;}
.card{background:var(--card);border:1px solid var(--line);border-radius:14px;
      padding:12px 14px;margin-bottom:10px;}
.hi{font-family:"Deva",serif;font-size:20px;line-height:1.45;}
.sat{font-family:"OlChiki",serif;color:var(--forest);font-size:25px;line-height:1.75;}
.sat.big{font-size:29px;}
.pill{display:inline-flex;align-items:center;gap:6px;font-size:10.5px;
      background:#E9EDEA;border:1px solid #BFCFC5;color:#1f4c39;
      padding:4px 10px;border-radius:999px;font-family:system-ui,sans-serif;}
.pill .dot{width:7px;height:7px;border-radius:50%%;background:var(--ok);}
.pill.grey{background:#EFEFEC;border-color:var(--line);color:var(--muted);}
.pill.grey .dot{background:#9AA39D;}

.acts{display:flex;gap:26px;justify-content:center;margin:14px 0 10px;}
.act{text-align:center;font-family:system-ui,sans-serif;}
.circ{width:66px;height:66px;border-radius:50%%;display:grid;place-items:center;
      color:#fff;font-size:26px;margin:0 auto 5px;}
.circ.mic{background:var(--terra);} .circ.play{background:var(--forest);}
.act span{font-size:11px;color:var(--muted);}

.chips{display:flex;gap:7px;flex-wrap:wrap;}
.chip{background:#E9EDEA;border:2px solid var(--forest);border-radius:999px;
      padding:7px 12px;font-family:"Deva",serif;font-size:14px;}

.nav{display:flex;border-top:1px solid var(--line);background:#fff;flex:0 0 auto;
     font-family:system-ui,sans-serif;}
.nav div{flex:1;text-align:center;padding:9px 0;font-size:11px;color:var(--muted);}
.nav div.on{color:var(--forest);font-weight:700;box-shadow:inset 0 3px 0 var(--forest);}

.dots{display:flex;gap:5px;justify-content:center;margin:9px 0 12px;}
.dots i{width:8px;height:8px;border-radius:50%%;background:#CDD5CF;}
.dots i.on{background:var(--forest);width:22px;border-radius:999px;}
.pager{display:flex;gap:9px;margin-top:11px;}
.btn{flex:1;text-align:center;padding:12px;border-radius:999px;font-weight:700;
     font-family:system-ui,sans-serif;font-size:13px;}
.btn.p{background:var(--forest);color:#fff;} .btn.s{background:#fff;border:2px solid var(--forest);color:var(--forest);}
.rowline{display:flex;justify-content:space-between;font-size:11px;color:var(--muted);
         font-family:system-ui,sans-serif;margin-bottom:6px;}
.chk{border:1px solid var(--line);border-radius:10px;padding:9px 11px;margin-bottom:7px;
     font-family:system-ui,sans-serif;font-size:12px;}
.chk.ok{border-color:#9CC5AE;background:#F3F9F5;color:#1f4c39;}
.chk.bad{border-color:#E3B3AC;background:#FDF5F4;color:#8a4038;}
.big{font-size:34px;font-weight:700;color:var(--forest);font-family:system-ui,sans-serif;}
</style></head><body>
<div class="page">
<h1>Ol Saathi — screen designs</h1>
<p class="sub">Real Ol Chiki, real Devanagari, real strings from the shipped pack of %(total)s entries.</p>

<div class="note"><b>The one structural change.</b> The app currently has no way to walk a
lesson. Everything lives on one screen as a scrolling list, so a teacher cannot deliver
line by line. Screen 2 below is the missing piece, and it is the difference between a
phrase lookup and a teaching tool. The problem statement's title says <i>Pedagogy</i>.</div>

<div class="grid">

  <!-- 1 TEACH -->
  <div>
  <div class="dev"><div class="scr">
    <div class="bar"><span class="t">Ol Saathi</span><span class="r">Santali ▾</span></div>
    <div class="body">
      <div class="lab">HINDI · WHAT YOU SAID</div>
      <div class="card"><div class="hi">%(p01_hi)s</div></div>
      <div class="lab">SANTALI · OL CHIKI</div>
      <div class="card">
        <div class="sat big">%(p01_sat)s</div>
        <div style="margin-top:9px"><span class="pill"><span class="dot"></span>Machine translation · IndicTrans2</span></div>
      </div>
      <div class="acts">
        <div class="act"><div class="circ mic">&#9679;</div><span>Hold to speak</span></div>
        <div class="act"><div class="circ play">&#9654;</div><span>Play Santali</span></div>
      </div>
      <div class="lab">QUICK PHRASES</div>
      <div class="chips">%(chips)s</div>
    </div>
    <div class="nav"><div class="on">Teach</div><div>Lessons</div><div>Worksheet</div></div>
  </div></div>
  <p class="cap"><b>1 · Teach</b><br>The live screen. Santali is the largest thing on it.
  Provenance sits under the text, never hidden.</p>
  </div>

  <!-- 2 LESSON PLAYER -->
  <div>
  <div class="dev"><div class="scr">
    <div class="bar"><span class="t">नीमा और दादी</span><span class="r">2 / 10</span></div>
    <div class="body">
      <div class="dots"><i></i><i class="on"></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i></div>
      <div class="lab">HINDI</div>
      <div class="card"><div class="hi">%(l2_hi)s</div></div>
      <div class="lab">SANTALI · OL CHIKI</div>
      <div class="card">
        <div class="sat">%(l2_sat)s</div>
        <div style="margin-top:9px"><span class="pill"><span class="dot"></span>Machine translation</span></div>
      </div>
      <div class="acts" style="margin:10px 0 4px">
        <div class="act"><div class="circ play">&#9654;</div><span>Play Santali</span></div>
      </div>
      <div class="pager"><div class="btn s">← Back</div><div class="btn p">Next →</div></div>
    </div>
    <div class="nav"><div>Teach</div><div class="on">Lessons</div><div>Worksheet</div></div>
  </div></div>
  <p class="cap"><b>2 · Lesson player</b> &nbsp;<b style="color:#B85042">NEW</b><br>
  One line at a time, with progress. This is what delivering a lesson looks like.</p>
  </div>

  <!-- 3 COMPREHENSION -->
  <div>
  <div class="dev"><div class="scr">
    <div class="bar"><span class="t">नीमा और दादी</span><span class="r">Check 1 / 3</span></div>
    <div class="body">
      <div class="lab">ASK THE CLASS</div>
      <div class="card"><div class="hi">%(c1_hi)s</div></div>
      <div class="lab">SANTALI · OL CHIKI</div>
      <div class="card"><div class="sat">%(c1_sat)s</div></div>
      <div class="acts" style="margin:10px 0 4px">
        <div class="act"><div class="circ play">&#9654;</div><span>Play question</span></div>
      </div>
      <div class="pager"><div class="btn s">← Back</div><div class="btn p">Next question</div></div>
      <div style="margin-top:12px;font-size:11px;color:var(--muted);font-family:system-ui,sans-serif">
        No marking, no scores. The teacher asks, the class answers aloud.</div>
    </div>
    <div class="nav"><div>Teach</div><div class="on">Lessons</div><div>Worksheet</div></div>
  </div></div>
  <p class="cap"><b>3 · Comprehension</b><br>The assessment prompts the statement asks for,
  after the last line.</p>
  </div>

  <!-- 4 WORKSHEET -->
  <div>
  <div class="dev"><div class="scr">
    <div class="bar"><span class="t">Worksheet</span><span class="r">A4</span></div>
    <div class="body">
      <div class="lab">LESSON</div>
      <div class="card"><div class="hi" style="font-size:17px">नीमा और दादी &nbsp;▾</div></div>
      <div class="lab">PREVIEW</div>
      <div class="card" style="padding:14px">
        <div style="font-size:11px;color:var(--muted);font-family:system-ui,sans-serif">Name: ____________</div>
        <hr style="border:0;border-top:1px solid var(--line);margin:9px 0">
        <div class="hi" style="font-size:14px">%(l1_hi)s</div>
        <div class="sat" style="font-size:17px">%(l1_sat)s</div>
        <div style="height:16px;border-bottom:1px dashed var(--line);margin:7px 0"></div>
        <div style="font-size:9.5px;color:var(--muted);font-family:system-ui,sans-serif">
          NIPUN Bharat · FL-RD · Page 1</div>
      </div>
      <div class="pager"><div class="btn s">Share</div><div class="btn p">Print</div></div>
    </div>
    <div class="nav"><div>Teach</div><div>Lessons</div><div class="on">Worksheet</div></div>
  </div></div>
  <p class="cap"><b>4 · Worksheet</b><br>Already built and working. Add a preview so a
  teacher sees it before printing.</p>
  </div>

  <!-- 5 DIAGNOSTICS -->
  <div>
  <div class="dev"><div class="scr">
    <div class="bar"><span class="t">Check &amp; Proof</span><span class="r">for judges</span></div>
    <div class="body">
      <div class="big">6 / 7 ready</div>
      <div style="font-size:11px;color:var(--muted);margin-bottom:10px;font-family:system-ui,sans-serif">
        Run before every class</div>
      <div class="chk ok">✓ Content pack · %(total)s entries</div>
      <div class="chk ok">✓ Ol Chiki font · <span class="sat" style="font-size:15px">ᱚᱞ ᱪᱤᱠᱤ</span></div>
      <div class="chk bad">✗ Hindi offline speech not installed<br>
        <span style="font-size:10.5px">Settings › System › Languages › On-device speech</span></div>
      <div class="chk ok">✓ Worksheet generates · 57 KB</div>
      <div class="rowline" style="margin-top:11px"><span>Network calls this session</span><b>0</b></div>
      <div class="rowline"><span>Lookup, median</span><b>1 ms</b></div>
      <div class="rowline"><span>Human-reviewed</span><b>0 of %(total)s</b></div>
    </div>
    <div class="nav"><div>Teach</div><div>Lessons</div><div>Worksheet</div></div>
  </div></div>
  <p class="cap"><b>5 · Check &amp; Proof</b><br>Merge Pre-Flight and Live Proof. Judges
  should see one screen, not hunt two.</p>
  </div>

</div>
</div></body></html>
'''

if __name__ == '__main__':
    main()

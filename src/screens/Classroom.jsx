import { useEffect, useMemo, useRef, useState } from 'react'
import {
  ArrowLeft, ArrowRight, Volume2, Mic, Pencil, WifiOff, Wifi, Check, X,
} from 'lucide-react'
import lessons from '../../content/lessons.json'
import { LANGUAGES } from '../lib/bhashini.js'
import './classroom.css'

/*
 * The classroom companion. One device at the front of the room.
 *
 * This is deliberately ONE screen, not a teacher app and a child app.
 * A village primary school has one device, not thirty, so the class
 * sees this and the teacher drives it from the bar along the bottom.
 * That decision also deletes accounts, login and sync from the build.
 *
 * The class-facing area is audio first and picture second. Children in
 * Class 1 and 2 cannot read in any script, so leading with text would
 * look impressive to a judge and be useless to a seven-year-old. The
 * Ol Chiki is there for the teacher and for older children.
 *
 * MOCK is on until the Bhashini keys land, so the screens can be built
 * and reviewed today rather than blocking on an account.
 */

const MOCK = !import.meta.env.VITE_BHASHINI_USER_ID

/* Hand-checked Santali for the first lesson, so the mock is honest
   about what the real thing will show rather than lorem ipsum. */
const MOCK_SANTALI = {
  l1: 'ᱱᱤᱢᱟ ᱛᱤᱠᱤᱱ ᱵᱟᱨ ᱴᱟᱲᱟᱝ ᱨᱮ ᱥᱠᱩᱞ ᱠᱷᱚᱱ ᱨᱩᱲᱟᱹᱜ ᱠᱟᱱᱟᱭ᱾',
  l2: 'ᱱᱚᱬᱟ ᱚᱠᱛᱚ ᱨᱮ ᱚᱲᱟᱜ ᱨᱮ ᱜᱤᱛᱤᱡ ᱡᱟᱶᱟᱸ ᱜᱮ ᱛᱟᱦᱮᱸᱱᱟᱭ᱾',
  l3: 'ᱡᱟᱶᱟᱸ ᱡᱟᱦᱟᱸᱛᱮ ᱵᱟᱝ ᱪᱟᱞᱟᱜᱼᱟᱭ᱾',
  l4: 'ᱩᱱᱤᱭᱟᱜ ᱴᱷᱩᱸᱴᱩ ᱨᱮ ᱦᱟᱥᱩ ᱛᱟᱦᱮᱸᱱᱟ᱾',
  l5: 'ᱩᱱᱤ ᱱᱤᱢᱟ ᱠᱷᱟᱹᱛᱤᱨ ᱟᱹᱰᱤ ᱛᱟᱹᱝᱜᱤ ᱠᱟᱱᱟᱭ᱾',
  l6: 'ᱱᱤᱢᱟ ᱫᱤᱱᱟᱹᱢ ᱡᱚᱢ ᱡᱚᱢ ᱛᱮ ᱥᱠᱩᱞ ᱨᱮᱱᱟᱜ ᱠᱟᱛᱷᱟ ᱞᱟᱹᱭ ᱟᱭ᱾',
  l7: 'ᱟᱹᱭᱩᱵ ᱨᱮ ᱱᱤᱢᱟ ᱮᱱᱮᱡ ᱪᱟᱞᱟᱜᱼᱟᱭ᱾',
  l8: 'ᱢᱤᱫ ᱢᱟ�ହᱟᱸ ᱡᱟᱶᱟᱸ ᱢᱮᱱ ᱠᱮᱫᱟᱭ, ᱤᱧᱟᱜ ᱚᱠᱛᱚ ᱵᱟᱝ ᱠᱟᱴᱚᱜᱼᱟ᱾',
  l9: 'ᱱᱤᱢᱟ ᱱᱤᱨ ᱠᱟᱛᱮ ᱡᱟᱶᱟᱸ ᱟᱜ ᱪᱟᱯᱟᱞ ᱟᱜᱩ ᱠᱮᱫᱟᱭ᱾',
  l10: 'ᱚᱱᱟ ᱛᱟᱭᱚᱢ ᱩᱱᱠᱤᱱ ᱵᱟᱨᱭᱟᱸ ᱮᱱᱮᱡ ᱴᱟᱸᱰᱤ ᱥᱮᱫ ᱪᱟᱞᱟᱠ ᱮᱱᱟᱠᱤᱱ᱾',
}

export default function Classroom() {
  const lesson = lessons.lessons[0]
  const [i, setI] = useState(0)
  const [lang, setLang] = useState('sat')
  const [out, setOut] = useState({ text: '', audio: null, spoken: false })
  const [busy, setBusy] = useState(false)
  const [check, setCheck] = useState(null)
  const [answered, setAnswered] = useState({})
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState('')
  const [online, setOnline] = useState(navigator.onLine)
  const audioRef = useRef(null)

  const line = lesson.lines[i]
  const meta = useMemo(() => LANGUAGES.find((l) => l.code === lang), [lang])

  useEffect(() => {
    const on = () => setOnline(true)
    const off = () => setOnline(false)
    addEventListener('online', on)
    addEventListener('offline', off)
    return () => {
      removeEventListener('online', on)
      removeEventListener('offline', off)
    }
  }, [])

  /* Deliver the current line. In mock mode this is instant and local,
     which is also what a cache hit feels like once it is wired. */
  useEffect(() => {
    let cancelled = false
    setEditing(false)
    async function run() {
      setBusy(true)
      try {
        if (MOCK) {
          await new Promise((r) => setTimeout(r, 260))
          if (!cancelled) {
            setOut({ text: MOCK_SANTALI[line.id] ?? line.hi, audio: null, spoken: meta.speaks })
          }
        } else {
          const { deliver } = await import('../lib/bhashini.js')
          const r = await deliver(line.hi, { to: lang })
          if (!cancelled) setOut(r)
        }
      } catch (e) {
        if (!cancelled) setOut({ text: '', audio: null, spoken: false, error: String(e.message || e) })
      } finally {
        if (!cancelled) setBusy(false)
      }
    }
    run()
    return () => { cancelled = true }
  }, [i, lang, line, meta])

  function advance() {
    const due = lesson.checks.find((c) => c.after === line.id && !answered[c.after])
    if (due) { setCheck(due); return }
    setI((n) => Math.min(n + 1, lesson.lines.length - 1))
  }

  function answer(opt) {
    setAnswered((a) => ({ ...a, [check.after]: opt.correct }))
    setCheck(null)
    setI((n) => Math.min(n + 1, lesson.lines.length - 1))
  }

  const pct = Math.round(((i + 1) / lesson.lines.length) * 100)

  return (
    <div className="room">
      {/* ---- status strip -------------------------------------- */}
      <header className="room-top">
        <div className="room-lesson">
          <b>{lesson.title}</b>
          <span>NCERT Sarangi · Class {lesson.grade} · {lesson.unit}</span>
        </div>

        <div className="room-langs" role="group" aria-label="Language">
          {LANGUAGES.map((l) => (
            <button key={l.code}
              className={l.code === lang ? 'on' : ''}
              onClick={() => setLang(l.code)}>
              {l.nameEn}
              {!l.speaks && <i title="No voice for this language yet">text</i>}
            </button>
          ))}
        </div>

        <span className={online ? 'room-net' : 'room-net is-off'}>
          {online ? <Wifi size={15} /> : <WifiOff size={15} />}
          {online ? 'online' : 'offline · cached'}
        </span>
      </header>

      {/* ---- what the class sees ------------------------------- */}
      <main className="stage">
        <div className="stage-art" data-art={line.image} aria-hidden="true">
          <span>{line.image.replace(/-/g, ' ')}</span>
        </div>

        <p className="stage-hi">{line.hi}</p>

        {busy ? (
          <p className="stage-wait">translating…</p>
        ) : out.error ? (
          <p className="stage-err">{out.error}</p>
        ) : editing ? (
          <div className="stage-edit">
            <textarea value={draft} onChange={(e) => setDraft(e.target.value)} rows={2} />
            <div>
              <button className="ok" onClick={() => { setOut((o) => ({ ...o, text: draft })); setEditing(false) }}>
                <Check size={15} /> Save correction
              </button>
              <button onClick={() => setEditing(false)}><X size={15} /> Cancel</button>
            </div>
          </div>
        ) : (
          <p className={`stage-out ${lang === 'sat' ? 'ol' : ''}`}>{out.text}</p>
        )}

        <button className="stage-play"
          disabled={busy || !meta.speaks}
          onClick={() => audioRef.current?.play()}>
          <Volume2 size={22} />
          {meta.speaks ? 'Play in ' + meta.nameEn : 'No voice for ' + meta.nameEn + ' yet'}
        </button>
        {out.audio && <audio ref={audioRef} src={out.audio} autoPlay />}
      </main>

      {/* ---- comprehension check ------------------------------- */}
      {check && (
        <div className="checkwrap" role="dialog" aria-label="Comprehension check">
          <div className="checkcard">
            <span className="checkkicker">Ask the class</span>
            <p className="checkq">{check.hi}</p>
            <p className="checkq-en">{check.en}</p>
            <div className="checkopts">
              {check.options.map((o) => (
                <button key={o.en} onClick={() => answer(o)}>
                  <b>{o.hi}</b><span>{o.en}</span>
                </button>
              ))}
            </div>
            <p className="checknote">Tap what the class answered</p>
          </div>
        </div>
      )}

      {/* ---- teacher controls ---------------------------------- */}
      <footer className="room-bar">
        <div className="bar-prog"><span style={{ width: pct + '%' }} /></div>
        <div className="bar-row">
          <button onClick={() => setI((n) => Math.max(n - 1, 0))} disabled={i === 0}>
            <ArrowLeft size={18} /> Back
          </button>

          <button className="bar-mic" title="Speak the line instead of tapping it">
            <Mic size={18} /> Speak
          </button>

          <button onClick={() => { setDraft(out.text); setEditing(true) }} disabled={busy || editing}>
            <Pencil size={17} /> Wrong? Fix it
          </button>

          <span className="bar-count">{i + 1} / {lesson.lines.length}</span>

          <button className="bar-next" onClick={advance} disabled={i === lesson.lines.length - 1}>
            Next <ArrowRight size={18} />
          </button>
        </div>
      </footer>
    </div>
  )
}

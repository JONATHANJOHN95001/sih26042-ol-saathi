import { useEffect, useRef, useState } from 'react'
import { Mic, Volume2, WifiOff, ArrowLeftRight } from 'lucide-react'
import SplineStage from './SplineStage.jsx'
import './landing.css'

/*
 * Landing screen for the Vernacular Pedagogy tool (SIH26042).
 *
 * The one idea this screen has to land in three seconds: the exchange
 * goes BOTH ways. A teacher speaks Hindi and a child hears Santali —
 * and the child can answer back. Everything here serves that sentence.
 */

/* Rotating proof line. Hindi in, Santali out, then the reverse. */
const EXCHANGE = [
  {
    from: 'हिंदी',
    fromText: 'पानी जीवन के लिए बहुत ज़रूरी है।',
    to: 'ᱥᱟᱱᱛᱟᱲᱤ',
    toText: 'ᱫᱟᱜ ᱡᱤᱭᱚᱱ ᱞᱟᱹᱜᱤᱫ ᱡᱟᱹᱨᱩᱨ ᱠᱟᱱᱟ᱾',
    who: 'Teacher speaks',
  },
  {
    from: 'ᱥᱟᱱᱛᱟᱲᱤ',
    fromText: 'ᱤᱧ ᱵᱟᱰᱟᱭ ᱠᱮᱫᱟ᱾',
    to: 'हिंदी',
    toText: 'मैं समझ गया।',
    who: 'Child answers back',
  },
]

function Exchange() {
  const [i, setI] = useState(0)

  useEffect(() => {
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (reduce) return
    const t = setInterval(() => setI((n) => (n + 1) % EXCHANGE.length), 5200)
    return () => clearInterval(t)
  }, [])

  const ex = EXCHANGE[i]

  return (
    <div className="exchange" key={i}>
      <p className="exchange-who">
        <ArrowLeftRight size={13} aria-hidden="true" />
        {ex.who}
      </p>
      <div className="exchange-row">
        <span className="exchange-lang">{ex.from}</span>
        <p className="exchange-text">{ex.fromText}</p>
      </div>
      <div className="exchange-arrow" aria-hidden="true" />
      <div className="exchange-row is-out">
        <span className="exchange-lang">{ex.to}</span>
        <p className="exchange-text ol">{ex.toText}</p>
        <Volume2 size={15} className="exchange-audio" aria-hidden="true" />
      </div>
    </div>
  )
}

/** Cursor-follow glow, the effect Aceternity uses on its hero cards. */
function useSpotlight() {
  const ref = useRef(null)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
    let frame = null
    const move = (e) => {
      if (frame) return
      frame = requestAnimationFrame(() => {
        const r = el.getBoundingClientRect()
        el.style.setProperty('--mx', `${((e.clientX - r.left) / r.width) * 100}%`)
        el.style.setProperty('--my', `${((e.clientY - r.top) / r.height) * 100}%`)
        frame = null
      })
    }
    el.addEventListener('pointermove', move, { passive: true })
    return () => {
      el.removeEventListener('pointermove', move)
      if (frame) cancelAnimationFrame(frame)
    }
  }, [])
  return ref
}

export default function Landing({ onStart }) {
  const heroRef = useSpotlight()

  return (
    <main className="landing">
      <div className="landing-stage">
        <SplineStage />
      </div>

      <section className="hero" ref={heroRef}>
        <p className="eyebrow">
          Smart India Hackathon 2026 &middot; SIH26042 &middot; Team INNOV8
        </p>

        <h1 className="hero-title">
          She speaks Santali.
          <br />
          <span className="grad">Her lesson does too.</span>
        </h1>

        <p className="hero-sub">
          A teacher speaks Hindi. A child reads and hears it in Santali &mdash; and
          <span className="aur">answers back in her own language.</span> Built on
          Bhashini, the Government of India&rsquo;s own language platform, and it
          <span className="aur"> keeps working when the network does not.</span>
        </p>

        <Exchange />

        <div className="hero-actions">
          <button type="button" className="btn btn-primary" onClick={onStart}>
            <Mic size={17} aria-hidden="true" />
            Start a lesson
          </button>
          <a className="btn btn-ghost" href="#how">
            How it works
          </a>
        </div>

        <ul className="hero-facts">
          <li>
            <WifiOff size={14} aria-hidden="true" />
            Works with no connection
          </li>
          <li>
            <Volume2 size={14} aria-hidden="true" />
            Speaks Santali aloud
          </li>
          <li>
            <ArrowLeftRight size={14} aria-hidden="true" />
            Understands her reply
          </li>
        </ul>
      </section>
    </main>
  )
}

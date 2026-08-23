import { Suspense, lazy, useEffect, useState } from 'react'

/*
 * The 3D stage behind the landing hero.
 *
 * Spline is loaded lazily and guarded three ways, because a hero that
 * fails is worse than a hero that was never there — especially on a
 * projector, on venue wifi, in front of judges:
 *
 *   1. No scene URL configured  -> the CSS fallback renders instead
 *   2. Scene fails to load      -> the error boundary swaps it out
 *   3. Reduced motion, small
 *      screen, or a weak GPU    -> we never even fetch it
 *
 * Set the scene in .env as VITE_SPLINE_SCENE once you have published
 * one from spline.design (File -> Export -> Code -> React).
 */

const Spline = lazy(() => import('@splinetool/react-spline'))

const SCENE = import.meta.env.VITE_SPLINE_SCENE ?? ''

/** A gradient field that reads as depth. Costs nothing, never fails. */
function FallbackStage() {
  return (
    <div className="stage-fallback" aria-hidden="true">
      <span className="orb orb-a" />
      <span className="orb orb-b" />
      <span className="orb orb-c" />
      <span className="stage-grid" />
    </div>
  )
}

class SceneBoundary extends Error {}

/**
 * Cheap capability check. A 3D scene on an integrated GPU during a demo
 * is a real risk, so we opt out rather than gamble.
 */
function canRender3D() {
  if (typeof window === 'undefined') return false
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return false
  if (window.innerWidth < 820) return false
  if (navigator.connection?.saveData) return false
  if ((navigator.hardwareConcurrency ?? 4) < 4) return false
  try {
    const gl = document.createElement('canvas').getContext('webgl2')
    return Boolean(gl)
  } catch {
    return false
  }
}

export default function SplineStage() {
  const [mode, setMode] = useState('fallback')

  useEffect(() => {
    if (SCENE && canRender3D()) setMode('spline')
  }, [])

  if (mode !== 'spline') return <FallbackStage />

  return (
    <div className="stage-3d">
      <Suspense fallback={<FallbackStage />}>
        <Spline
          scene={SCENE}
          onError={() => setMode('fallback')}
          style={{ width: '100%', height: '100%' }}
        />
      </Suspense>
      {/* Spline paints its own watermark corner; this covers it and
          softens the seam into the page background. */}
      <span className="stage-veil" aria-hidden="true" />
    </div>
  )
}

export { FallbackStage, canRender3D, SceneBoundary }

import { useState, useEffect, useCallback } from 'react'
import {
  Users, MapPin, Zap, ChevronDown, ChevronUp,
  CheckCircle, AlertTriangle, Loader2, CheckCheck
} from 'lucide-react'
import * as store from '../lib/store.js'

const URGENCY_LABEL = ['', 'Low', 'Low-Med', 'Medium', 'High', 'Critical']

function urgencyStyle(u) {
  const colors = ['', 'var(--urg-1)', 'var(--urg-2)', 'var(--urg-3)', 'var(--urg-4)', 'var(--urg-5)']
  return { background: `${colors[u]}22`, color: colors[u], borderColor: `${colors[u]}44` }
}

function OccupancyColor(pct) {
  if (pct < 60) return 'var(--occ-low)'
  if (pct < 80) return 'var(--occ-mid)'
  if (pct < 95) return 'var(--occ-high)'
  return 'var(--occ-danger)'
}

function MatchPanel({ requestId, onAllocate }) {
  const [state, setState] = useState('idle') // idle | loading | loaded | error
  const [candidates, setCandidates] = useState([])
  const [allocating, setAllocating] = useState(null)
  const [done, setDone] = useState(null)
  const [err, setErr] = useState('')

  const load = useCallback(async () => {
    setState('loading')
    try {
      const result = await store.autoMatch(requestId)
      setCandidates(result.candidates)
      setState('loaded')
    } catch (e) {
      setErr(e.message)
      setState('error')
    }
  }, [requestId])

  useEffect(() => { load() }, [load])

  const confirm = async (campId) => {
    setAllocating(campId)
    try {
      await store.allocate(requestId, campId)
      setDone(campId)
      setTimeout(() => onAllocate(), 600)
    } catch (e) {
      setErr(e.message)
      setAllocating(null)
    }
  }

  if (state === 'loading') {
    return (
      <div className="match-panel">
        <div className="match-panel-header">Finding best camps…</div>
        <div className="loading-wrap" style={{ padding: 'var(--sp-6)' }}>
          <div className="spinner" />
        </div>
      </div>
    )
  }

  if (state === 'error') {
    return (
      <div className="match-panel">
        <div className="match-panel-header" style={{ color: 'var(--clr-danger)' }}>Error: {err}</div>
      </div>
    )
  }

  if (candidates.length === 0) {
    return (
      <div className="match-panel">
        <div className="match-panel-header">No Suitable Camps Found</div>
        <div style={{ padding: 'var(--sp-4)', fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
          No camp currently has enough free capacity for this family size.
        </div>
      </div>
    )
  }

  return (
    <div className="match-panel">
      <div className="match-panel-header">Top {candidates.length} Matches</div>
      {candidates.map((c, i) => {
        const pct = Math.round((c.camp.occupied / c.camp.capacity) * 100)
        const isAllocating = allocating === c.camp.id
        const isDone = done === c.camp.id
        return (
          <div className="match-row" key={c.camp.id}>
            <div className="match-rank">{i + 1}</div>
            <div className="match-info">
              <div className="match-camp-name">{c.camp.name}</div>
              <div className="match-camp-ward">{c.camp.ward}</div>
              <div className="match-explanation">{c.explanation}</div>
              <div style={{ marginTop: 'var(--sp-2)', display: 'flex', alignItems: 'center', gap: 'var(--sp-2)' }}>
                <div className="occ-bar-bg" style={{ width: 80, flex: 'none' }}>
                  <div
                    className="occ-bar-fill"
                    style={{ width: `${pct}%`, background: OccupancyColor(pct) }}
                  />
                </div>
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--text-muted)' }}>{pct}% full</span>
              </div>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 'var(--sp-2)', flexShrink: 0 }}>
              <span className="score-pill">Score {c.score}</span>
              <button
                className={`btn btn-sm ${isDone ? 'btn-success' : 'btn-primary'}`}
                onClick={() => !isDone && !allocating && confirm(c.camp.id)}
                disabled={!!allocating}
                aria-label={`Allocate to ${c.camp.name}`}
              >
                {isAllocating ? <Loader2 size={12} className="spin" /> : isDone ? <CheckCheck size={12} /> : <CheckCircle size={12} />}
                {isAllocating ? 'Saving…' : isDone ? 'Done!' : 'Confirm'}
              </button>
            </div>
          </div>
        )
      })}
    </div>
  )
}

function RequestCard({ request, onAllocate }) {
  const [expanded, setExpanded] = useState(false)

  const urgColor = ['', 'var(--urg-1)', 'var(--urg-2)', 'var(--urg-3)', 'var(--urg-4)', 'var(--urg-5)'][request.urgency]

  return (
    <article className="request-card" style={{ borderTop: `3px solid ${urgColor}` }}>
      <div className="req-header">
        <div>
          <div className="req-title">{request.familyName}</div>
          <div className="req-meta">
            <MapPin size={12} />
            {request.location}
          </div>
        </div>
        <span
          className="urgency-badge"
          style={urgencyStyle(request.urgency)}
        >
          <AlertTriangle size={11} />
          {URGENCY_LABEL[request.urgency]}
        </span>
      </div>

      <div className="req-body">
        <div className="req-detail-row">
          <Users size={14} style={{ color: 'var(--clr-brand-500)', flexShrink: 0 }} />
          <span><strong>{request.familySize}</strong> family members</span>
        </div>
        <div className="req-detail-row" style={{ flexWrap: 'wrap', gap: 'var(--sp-2)' }}>
          <span style={{ fontSize: 'var(--text-xs)', color: 'var(--text-muted)' }}>Needs:</span>
          {request.needs.map(n => (
            <span key={n} className={`need-tag ${n}`}>{n}</span>
          ))}
        </div>
      </div>

      <div className="req-footer">
        <button
          className="btn btn-ghost"
          style={{ width: '100%', justifyContent: 'center' }}
          onClick={() => setExpanded(e => !e)}
          aria-expanded={expanded}
          aria-controls={`match-panel-${request.id}`}
        >
          <Zap size={14} style={{ color: 'var(--clr-brand-500)' }} />
          Auto-match Camps
          {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
        </button>

        {expanded && (
          <div id={`match-panel-${request.id}`} style={{ marginTop: 'var(--sp-3)' }}>
            <MatchPanel requestId={request.id} onAllocate={onAllocate} />
          </div>
        )}
      </div>
    </article>
  )
}

export default function RequestsPage() {
  const [requests, setRequests] = useState([])
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    const data = await store.listRequests()
    // Sort by urgency descending
    const sorted = [...data].sort((a, b) => b.urgency - a.urgency)
    setRequests(sorted)
    setLoading(false)
  }, [])

  useEffect(() => { load() }, [load])

  return (
    <>
      <header className="topbar">
        <h1 className="topbar-title">Requests Board</h1>
        {!loading && requests.length > 0 && (
          <span className="topbar-badge">
            <AlertTriangle size={12} style={{ display: 'inline', verticalAlign: 'middle', marginRight: 4 }} />
            {requests.length} pending
          </span>
        )}
      </header>

      <main className="page-body">
        {loading ? (
          <div className="loading-wrap">
            <div className="spinner" />
            <span>Loading requests…</span>
          </div>
        ) : requests.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">
              <CheckCheck size={28} />
            </div>
            <div>
              <p style={{ fontWeight: 'var(--fw-semibold)', fontSize: 'var(--text-lg)' }}>All Clear</p>
              <p style={{ fontSize: 'var(--text-sm)', marginTop: 4 }}>All family requests have been allocated.</p>
            </div>
          </div>
        ) : (
          <>
            <div className="section-header">
              <p className="page-section-title" style={{ marginBottom: 0 }}>Pending Requests</p>
              <span style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>Sorted by urgency (highest first)</span>
            </div>
            <div className="requests-grid">
              {requests.map(r => (
                <RequestCard key={r.id} request={r} onAllocate={load} />
              ))}
            </div>
          </>
        )}
      </main>
    </>
  )
}

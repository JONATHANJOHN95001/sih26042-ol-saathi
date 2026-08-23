import { useState, useEffect, useCallback } from 'react'
import {
  Tent, Users, BedDouble, ShieldAlert, Utensils, Droplets, Heart
} from 'lucide-react'
import * as store from '../lib/store.js'

function occupancyColor(pct) {
  if (pct < 60) return 'var(--occ-low)'
  if (pct < 80) return 'var(--occ-mid)'
  if (pct < 95) return 'var(--occ-high)'
  return 'var(--occ-danger)'
}

function supplyColor(val) {
  if (val >= 70) return 'var(--occ-low)'
  if (val >= 40) return 'var(--occ-mid)'
  return 'var(--occ-danger)'
}

function StatusChip({ pct }) {
  if (pct >= 95) return <span className="status-chip" style={{ background: 'var(--clr-danger-bg)', color: 'var(--clr-danger)' }}>⚠ Critical</span>
  if (pct >= 80)  return <span className="status-chip" style={{ background: 'var(--clr-warning-bg)', color: 'var(--clr-warning)' }}>High</span>
  if (pct >= 60)  return <span className="status-chip" style={{ background: 'var(--clr-info-bg)', color: 'var(--clr-info)' }}>Moderate</span>
  return <span className="status-chip" style={{ background: 'var(--clr-success-bg)', color: 'var(--clr-success)' }}>Available</span>
}

function SupplyPips({ supplies }) {
  const items = [
    { key: 'food',    icon: <Utensils size={9} />,  label: 'F' },
    { key: 'water',   icon: <Droplets size={9} />,  label: 'W' },
    { key: 'medical', icon: <Heart size={9} />,     label: 'M' },
  ]
  return (
    <div className="supply-pips">
      {items.map(({ key, label }) => {
        const val = supplies[key]
        return (
          <div key={key} className="supply-pip" title={`${key}: ${val}%`}>
            <div className="pip-bar">
              <div className="pip-fill" style={{ height: `${val}%`, background: supplyColor(val) }} />
            </div>
            <span>{label}</span>
          </div>
        )
      })}
    </div>
  )
}

export default function CampsPage() {
  const [camps, setCamps] = useState([])
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    const data = await store.listCamps()
    setCamps(data)
    setLoading(false)
  }, [])

  useEffect(() => {
    load()
    // Poll every 3 s to pick up allocations made on the Requests page
    const id = setInterval(load, 3000)
    return () => clearInterval(id)
  }, [load])

  const totalCapacity = camps.reduce((s, c) => s + c.capacity, 0)
  const totalOccupied = camps.reduce((s, c) => s + c.occupied, 0)
  const freeBeds      = totalCapacity - totalOccupied
  const atRisk        = camps.filter(c => (c.occupied / c.capacity) >= 0.80).length

  return (
    <>
      <header className="topbar">
        <h1 className="topbar-title">Camps Dashboard</h1>
        {!loading && atRisk > 0 && (
          <span className="topbar-badge">
            {atRisk} camp{atRisk > 1 ? 's' : ''} near capacity
          </span>
        )}
        {!loading && atRisk === 0 && (
          <span className="topbar-badge info">All camps healthy</span>
        )}
      </header>

      <main className="page-body">
        {loading ? (
          <div className="loading-wrap">
            <div className="spinner" />
            <span>Loading camps…</span>
          </div>
        ) : (
          <>
            {/* Stat cards */}
            <div className="stat-grid">
              <div className="stat-card">
                <div className="stat-icon-wrap" style={{ background: 'var(--clr-brand-100)' }}>
                  <Tent size={20} style={{ color: 'var(--clr-brand-600)' }} />
                </div>
                <div className="stat-label">Total Camps</div>
                <div className="stat-value" style={{ color: 'var(--clr-brand-500)' }}>{camps.length}</div>
                <div className="stat-sub">Active relief stations</div>
              </div>

              <div className="stat-card">
                <div className="stat-icon-wrap" style={{ background: 'var(--clr-info-bg)' }}>
                  <Users size={20} style={{ color: 'var(--clr-info)' }} />
                </div>
                <div className="stat-label">Total Capacity</div>
                <div className="stat-value" style={{ color: 'var(--clr-info)' }}>{totalCapacity}</div>
                <div className="stat-sub">Across all camps</div>
              </div>

              <div className="stat-card">
                <div className="stat-icon-wrap" style={{ background: 'var(--clr-warning-bg)' }}>
                  <BedDouble size={20} style={{ color: 'var(--clr-warning)' }} />
                </div>
                <div className="stat-label">Occupied</div>
                <div className="stat-value" style={{ color: 'var(--clr-warning)' }}>{totalOccupied}</div>
                <div className="stat-sub">{Math.round((totalOccupied / totalCapacity) * 100)}% overall occupancy</div>
              </div>

              <div className="stat-card">
                <div className="stat-icon-wrap" style={{ background: 'var(--clr-success-bg)' }}>
                  <BedDouble size={20} style={{ color: 'var(--clr-success)' }} />
                </div>
                <div className="stat-label">Free Beds</div>
                <div className="stat-value" style={{ color: 'var(--clr-success)' }}>{freeBeds}</div>
                <div className="stat-sub">Available right now</div>
              </div>

              <div className="stat-card">
                <div className="stat-icon-wrap" style={{ background: atRisk > 0 ? 'var(--clr-danger-bg)' : 'var(--clr-success-bg)' }}>
                  <ShieldAlert size={20} style={{ color: atRisk > 0 ? 'var(--clr-danger)' : 'var(--clr-success)' }} />
                </div>
                <div className="stat-label">Camps at Risk</div>
                <div className="stat-value" style={{ color: atRisk > 0 ? 'var(--clr-danger)' : 'var(--clr-success)' }}>{atRisk}</div>
                <div className="stat-sub">&ge;80% occupancy</div>
              </div>
            </div>

            {/* Camps table */}
            <div className="card">
              <div style={{ padding: 'var(--sp-5) var(--sp-6)', borderBottom: '1px solid var(--border-subtle)', display: 'flex', alignItems: 'center', gap: 'var(--sp-3)' }}>
                <Tent size={18} style={{ color: 'var(--clr-brand-500)' }} />
                <span style={{ fontWeight: 'var(--fw-semibold)', fontSize: 'var(--text-base)' }}>All Camps</span>
              </div>
              <div className="camps-table-wrap">
                <table className="camps-table">
                  <thead>
                    <tr>
                      <th>Camp Name</th>
                      <th>Ward</th>
                      <th>Occupancy</th>
                      <th>Free Beds</th>
                      <th>Supplies (F/W/M)</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {camps.map(camp => {
                      const pct = Math.round((camp.occupied / camp.capacity) * 100)
                      const free = camp.capacity - camp.occupied
                      return (
                        <tr key={camp.id}>
                          <td style={{ fontWeight: 'var(--fw-medium)' }}>{camp.name}</td>
                          <td style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-xs)' }}>{camp.ward}</td>
                          <td>
                            <div className="occ-wrap">
                              <div className="occ-bar-bg">
                                <div
                                  className="occ-bar-fill"
                                  style={{ width: `${pct}%`, background: occupancyColor(pct) }}
                                />
                              </div>
                              <span className="occ-label">{pct}%</span>
                            </div>
                            <div style={{ fontSize: 'var(--text-xs)', color: 'var(--text-muted)', marginTop: 2 }}>
                              {camp.occupied}/{camp.capacity}
                            </div>
                          </td>
                          <td style={{ fontWeight: 'var(--fw-semibold)', color: free > 0 ? 'var(--clr-success)' : 'var(--clr-danger)' }}>
                            {free}
                          </td>
                          <td>
                            <SupplyPips supplies={camp.supplies} />
                          </td>
                          <td>
                            <StatusChip pct={pct} />
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </>
        )}
      </main>
    </>
  )
}

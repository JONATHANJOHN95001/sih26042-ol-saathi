import { BrowserRouter, Routes, Route, NavLink } from 'react-router-dom'
import { LayoutDashboard, Tent, Zap } from 'lucide-react'
import RequestsPage from './pages/RequestsPage.jsx'
import CampsPage from './pages/CampsPage.jsx'

export default function App() {
  return (
    <BrowserRouter>
      <div className="app-shell">
        <nav className="sidebar" aria-label="Main navigation">
          <div className="sidebar-brand">
            <div className="sidebar-brand-icon">
              <Zap size={20} />
            </div>
            <div>
              <div className="sidebar-brand-text">SetuRelief</div>
              <div className="sidebar-brand-sub">Disaster Coordinator</div>
            </div>
          </div>

          <NavLink
            to="/"
            end
            className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
          >
            <LayoutDashboard size={18} />
            Requests Board
          </NavLink>

          <NavLink
            to="/camps"
            className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
          >
            <Tent size={18} />
            Camps Dashboard
          </NavLink>
        </nav>

        <div className="main-content">
          <Routes>
            <Route path="/" element={<RequestsPage />} />
            <Route path="/camps" element={<CampsPage />} />
          </Routes>
        </div>
      </div>
    </BrowserRouter>
  )
}

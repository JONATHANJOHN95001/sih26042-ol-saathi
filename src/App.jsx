import { useState } from 'react'
import Landing from './components/Landing.jsx'
import Classroom from './screens/Classroom.jsx'

/* Two views for now: the pitch screen and the tool itself. Routing can
   come later; today this just lets the team see both. */
export default function App() {
  const [view, setView] = useState('landing')
  if (view === 'room') return <Classroom />
  return <Landing onStart={() => setView('room')} />
}

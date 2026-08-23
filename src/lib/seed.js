export const seedUsers = [
  { id: 'u1', name: 'Aisha Patel',   role: 'admin',   avatar: null },
  { id: 'u2', name: 'Rohan Mehta',   role: 'manager', avatar: null },
  { id: 'u3', name: 'Priya Sharma',  role: 'member',  avatar: null },
  { id: 'u4', name: 'Arjun Nair',    role: 'member',  avatar: null },
  { id: 'u5', name: 'Sneha Gupta',   role: 'member',  avatar: null },
]

export const seedProjects = [
  {
    id: 'p1',
    title: 'Disaster Response Dashboard',
    description: 'Real-time situational awareness for relief operations.',
    status: 'active',
    ownerId: 'u1',
    members: ['u1', 'u2', 'u3'],
    createdAt: '2026-08-20T09:00:00Z',
  },
  {
    id: 'p2',
    title: 'Water Quality Monitor',
    description: 'IoT-driven water testing with offline sync.',
    status: 'active',
    ownerId: 'u2',
    members: ['u2', 'u4', 'u5'],
    createdAt: '2026-08-20T10:30:00Z',
  },
  {
    id: 'p3',
    title: 'Smart Attendance System',
    description: 'Facial-recognition attendance for rural schools.',
    status: 'completed',
    ownerId: 'u1',
    members: ['u1', 'u3'],
    createdAt: '2026-08-19T14:00:00Z',
  },
]

export const seedTasks = [
  { id: 't1', projectId: 'p1', title: 'Design map component',      status: 'done',     assigneeId: 'u3', priority: 'high' },
  { id: 't2', projectId: 'p1', title: 'Integrate weather API',      status: 'in_progress', assigneeId: 'u2', priority: 'high' },
  { id: 't3', projectId: 'p1', title: 'Add incident reporting form',status: 'todo',     assigneeId: 'u1', priority: 'medium' },
  { id: 't4', projectId: 'p2', title: 'Set up sensor data pipeline',status: 'in_progress', assigneeId: 'u4', priority: 'high' },
  { id: 't5', projectId: 'p2', title: 'Build alerts UI',            status: 'todo',     assigneeId: 'u5', priority: 'medium' },
  { id: 't6', projectId: 'p3', title: 'Face detection module',      status: 'done',     assigneeId: 'u1', priority: 'high' },
  { id: 't7', projectId: 'p3', title: 'Offline data sync',          status: 'done',     assigneeId: 'u3', priority: 'medium' },
]

export const seedNotifications = [
  { id: 'n1', userId: 'u1', message: 'New task assigned: Add incident reporting form', read: false, createdAt: '2026-08-21T08:00:00Z' },
  { id: 'n2', userId: 'u2', message: 'Weather API integration started',                 read: true,  createdAt: '2026-08-21T07:30:00Z' },
  { id: 'n3', userId: 'u3', message: 'Project "Disaster Response Dashboard" updated',  read: false, createdAt: '2026-08-21T07:45:00Z' },
]

export const SEED_VERSION = 1

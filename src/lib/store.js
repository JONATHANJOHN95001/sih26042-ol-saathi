/**
 * store.js — async localStorage wrapper.
 *
 * Rules:
 *  - Every public method returns a Promise.
 *  - This is the ONLY module that reads/writes localStorage.
 *  - Data is JSON-serialised under a single namespace key.
 */

const NS = 'sih_data'
const VERSION_KEY = 'sih_seed_version'

let cache = null

/* ── Internal helpers ──────────────────────────────────── */

function readRaw() {
  try {
    return JSON.parse(localStorage.getItem(NS)) ?? {}
  } catch {
    return {}
  }
}

function writeRaw(data) {
  localStorage.setItem(NS, JSON.stringify(data))
}

function ensureCache() {
  if (cache === null) cache = readRaw()
  return cache
}

function flush() {
  writeRaw(ensureCache())
}

/* ── Public API ────────────────────────────────────────── */

/** Get all records for a collection. */
export async function getAll(collection) {
  const db = ensureCache()
  return db[collection] ?? []
}

/** Get a single record by id. Returns undefined when missing. */
export async function getById(collection, id) {
  const rows = await getAll(collection)
  return rows.find((r) => r.id === id)
}

/** Insert one or many records. Returns the inserted record(s). */
export async function insert(collection, records) {
  const db = ensureCache()
  const list = Array.isArray(records) ? records : [records]
  db[collection] = [...(db[collection] ?? []), ...list]
  flush()
  return list.length === 1 ? list[0] : list
}

/** Replace a record by id. Returns the updated record, or undefined. */
export async function update(collection, id, patch) {
  const db = ensureCache()
  const list = db[collection] ?? []
  const idx = list.findIndex((r) => r.id === id)
  if (idx === -1) return undefined
  list[idx] = { ...list[idx], ...patch, id }
  flush()
  return list[idx]
}

/** Delete a record by id. Returns true if deleted, false if not found. */
export async function remove(collection, id) {
  const db = ensureCache()
  const list = db[collection] ?? []
  const idx = list.findIndex((r) => r.id === id)
  if (idx === -1) return false
  list.splice(idx, 1)
  flush()
  return true
}

/** Replace an entire collection at once. */
export async function replaceAll(collection, records) {
  const db = ensureCache()
  db[collection] = [...records]
  flush()
  return records
}

/** Wipe every collection and reset the cache. */
export async function clearAll() {
  cache = {}
  writeRaw({})
  localStorage.removeItem(VERSION_KEY)
}

/** Check whether the database has been seeded yet. */
export async function isSeeded() {
  return localStorage.getItem(VERSION_KEY) !== null
}

/** Mark the database as seeded at a given version. */
export async function markSeeded(version = 1) {
  localStorage.setItem(VERSION_KEY, String(version))
}

/** Bulk-seed the database. Skips if already seeded at the same version. */
export async function seedIfEmpty(version, dataMap) {
  const current = localStorage.getItem(VERSION_KEY)
  if (current === String(version)) return false
  cache = {}
  for (const [collection, records] of Object.entries(dataMap)) {
    cache[collection] = [...records]
  }
  flush()
  markSeeded(version)
  return true
}

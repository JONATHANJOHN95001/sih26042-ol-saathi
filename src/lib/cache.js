/**
 * Translation and audio cache, in IndexedDB.
 *
 * This is the layer that makes the offline demo real. Everything the
 * app has ever fetched stays on the device, so a pre-warmed lesson runs
 * with the wifi switched off. It is also what keeps the app usable on a
 * slow connection, since a repeated line costs nothing the second time.
 *
 * Deliberately not Transformers.js. Running a translation model in the
 * browser is the flashier offline story and it is a few hundred MB and
 * a day of fighting WASM. Caching gets most of the benefit in an hour.
 */

const DB = 'almanac'
const STORE = 'kv'
let dbp = null

function open() {
  if (dbp) return dbp
  dbp = new Promise((resolve, reject) => {
    const req = indexedDB.open(DB, 1)
    req.onupgradeneeded = () => {
      if (!req.result.objectStoreNames.contains(STORE)) {
        req.result.createObjectStore(STORE)
      }
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
  return dbp
}

async function tx(mode, fn) {
  try {
    const db = await open()
    return await new Promise((resolve, reject) => {
      const t = db.transaction(STORE, mode)
      const req = fn(t.objectStore(STORE))
      req.onsuccess = () => resolve(req.result)
      req.onerror = () => reject(req.error)
    })
  } catch {
    // A blocked or private-mode IndexedDB must never take the app down.
    // Losing the cache costs speed, not function.
    return undefined
  }
}

export const cacheGet = (key) => tx('readonly', (s) => s.get(key))
export const cachePut = (key, val) => tx('readwrite', (s) => s.put(val, key))

/**
 * Fetch every line of a lesson ahead of time.
 *
 * Run this before presenting. It is the difference between a demo that
 * survives the wifi being switched off and one that does not, and the
 * first translation of a session is always the slow one.
 */
export async function prewarm(lesson, deliver, { to = 'sat', onProgress } = {}) {
  const done = []
  for (const [i, line] of lesson.lines.entries()) {
    try {
      await deliver(line.hi, { to })
      done.push(line.id)
    } catch (e) {
      console.warn('prewarm failed for', line.id, e)
    }
    onProgress?.({ done: i + 1, total: lesson.lines.length, id: line.id })
  }
  return done
}

/** Rough size of what we are holding, for the settings screen. */
export async function cacheSize() {
  if (!navigator.storage?.estimate) return null
  const { usage } = await navigator.storage.estimate()
  return usage
}

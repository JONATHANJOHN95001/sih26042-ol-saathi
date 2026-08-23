// src/lib/store.js
// ONLY module that touches localStorage.
// All methods are async and return Promises.

import { SEED_CAMPS, SEED_REQUESTS } from "./seed.js";
import { rankCamps } from "./match.js";

const KEYS = {
  camps: "setu:camps",
  requests: "setu:requests",
  allocated: "setu:allocated",
};

function read(key) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function write(key, value) {
  localStorage.setItem(key, JSON.stringify(value));
}

function ensureSeeded() {
  if (!read(KEYS.camps)) {
    write(KEYS.camps, SEED_CAMPS);
  }
  if (!read(KEYS.requests)) {
    write(KEYS.requests, SEED_REQUESTS);
  }
  if (!read(KEYS.allocated)) {
    write(KEYS.allocated, []);
  }
}

// Simulate async — keeps all callers honest about awaiting
const tick = () => new Promise((resolve) => setTimeout(resolve, 0));

export async function listCamps() {
  await tick();
  ensureSeeded();
  return read(KEYS.camps) ?? [];
}

export async function listRequests() {
  await tick();
  ensureSeeded();
  const all = read(KEYS.requests) ?? [];
  const allocated = read(KEYS.allocated) ?? [];
  // Return only pending (not yet allocated) requests
  return all.filter((r) => !allocated.includes(r.id));
}

/**
 * Allocate a family to a camp.
 * - Increments camp.occupied by request.familySize
 * - Marks request as allocated
 */
export async function allocate(requestId, campId) {
  await tick();
  ensureSeeded();

  const camps = read(KEYS.camps) ?? [];
  const requests = read(KEYS.requests) ?? [];
  const allocated = read(KEYS.allocated) ?? [];

  const request = requests.find((r) => r.id === requestId);
  if (!request) throw new Error(`Request ${requestId} not found`);
  if (allocated.includes(requestId)) throw new Error(`Request ${requestId} already allocated`);

  const campIndex = camps.findIndex((c) => c.id === campId);
  if (campIndex === -1) throw new Error(`Camp ${campId} not found`);

  const camp = camps[campIndex];
  const freeBeds = camp.capacity - camp.occupied;
  if (freeBeds < request.familySize) {
    throw new Error(`Camp ${camp.name} does not have enough free beds`);
  }

  camps[campIndex] = {
    ...camp,
    occupied: camp.occupied + request.familySize,
  };

  write(KEYS.camps, camps);
  write(KEYS.allocated, [...allocated, requestId]);

  return { camp: camps[campIndex], request };
}

/**
 * Auto-match: return top 3 ranked camps for a request (no allocation yet).
 * @returns {{ camp, score, explanation }[]}
 */
export async function autoMatch(requestId) {
  await tick();
  ensureSeeded();

  const camps = read(KEYS.camps) ?? [];
  const requests = read(KEYS.requests) ?? [];

  const request = requests.find((r) => r.id === requestId);
  if (!request) throw new Error(`Request ${requestId} not found`);

  const ranked = rankCamps(camps, request);
  return { request, candidates: ranked.slice(0, 3) };
}

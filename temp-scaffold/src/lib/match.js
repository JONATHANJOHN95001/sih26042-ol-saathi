// src/lib/match.js
// Pure matching logic — no storage access, no side effects

/**
 * Compute a numeric score for how well a camp fits a request.
 * Returns null if the camp cannot accommodate the request.
 * @param {object} camp
 * @param {object} request
 * @returns {{ score: number, explanation: string } | null}
 */
export function scoreMatch(camp, request) {
  const freeBeds = camp.capacity - camp.occupied;

  // Hard requirement: must have enough free beds
  if (freeBeds < request.familySize) return null;

  // Supply score: average of relevant supply levels (0–100 each)
  let supplyTotal = 0;
  let supplyCount = 0;
  for (const need of request.needs) {
    if (camp.supplies[need] !== undefined) {
      supplyTotal += camp.supplies[need];
      supplyCount += 1;
    }
  }
  const supplyScore = supplyCount > 0 ? supplyTotal / supplyCount : 50;

  // Capacity buffer score: reward camps with more headroom (0–100)
  const bufferScore = Math.min(100, (freeBeds / request.familySize) * 25);

  // Urgency weight: higher urgency → supply score matters more
  const urgencyWeight = request.urgency / 5; // 0.2–1.0

  const score = Math.round(
    supplyScore * 0.6 * urgencyWeight +
    supplyScore * 0.2 * (1 - urgencyWeight) +
    bufferScore * 0.2
  );

  // Human-readable explanation
  const supplyPct = Math.round(supplyScore);
  const supplyLabel = request.needs.join(" & ");
  let explanation;
  if (freeBeds > request.familySize * 3) {
    explanation = `${freeBeds} free beds, ${supplyPct}% ${supplyLabel} supply — ample capacity.`;
  } else if (freeBeds >= request.familySize * 2) {
    explanation = `${freeBeds} free beds, ${supplyPct}% ${supplyLabel} supply — comfortable fit.`;
  } else {
    explanation = `${freeBeds} free beds, ${supplyPct}% ${supplyLabel} supply — tight but feasible.`;
  }

  return { score, explanation };
}

/**
 * Rank all camps for a given request.
 * @param {object[]} camps
 * @param {object} request
 * @returns {{ camp: object, score: number, explanation: string }[]} sorted descending by score
 */
export function rankCamps(camps, request) {
  const candidates = [];

  for (const camp of camps) {
    const result = scoreMatch(camp, request);
    if (result !== null) {
      candidates.push({ camp, score: result.score, explanation: result.explanation });
    }
  }

  // Sort descending by score, then by urgency-adjusted supply if tied
  candidates.sort((a, b) => b.score - a.score);
  return candidates;
}

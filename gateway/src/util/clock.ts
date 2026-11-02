/**
 * Injectable clock for TTL and challenge expiry tests.
 */

export type Clock = () => number;

/** Unix seconds. */
export const systemClock: Clock = () => Math.floor(Date.now() / 1000);

let current: Clock = systemClock;

export function now(): number {
  return current();
}

/** Replace the clock (tests). Returns restore function. */
export function setClock(clock: Clock): () => void {
  const prev = current;
  current = clock;
  return () => {
    current = prev;
  };
}

export function resetClock(): void {
  current = systemClock;
}

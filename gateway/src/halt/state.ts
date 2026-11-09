/**
 * Client for the HaltState Durable Object (Stage 3.5).
 * Tests may set env.__haltActive to bypass the DO.
 */

import type { Env } from '../runtime';

export interface HaltStatus {
  active: boolean;
  reason?: string;
  at?: number;
}

/** Process-local fallback when HALT_STATE binding is absent (tests / single-isolate). */
let memoryHalt = false;

function getStub(env: Env): DurableObjectStub {
  const ns = env.HALT_STATE;
  if (!ns) {
    return {
      fetch: async (input: RequestInfo | URL) => {
        const url =
          typeof input === 'string'
            ? new URL(input)
            : input instanceof URL
              ? input
              : new URL((input as Request).url);
        const path = url.pathname;
        if (path === '/status' || path.endsWith('/status')) {
          const active =
            typeof env.__haltActive === 'boolean' ? env.__haltActive : memoryHalt;
          return new Response(JSON.stringify({ ok: true, active }), { status: 200 });
        }
        if (path === '/halt' || path.endsWith('/halt')) {
          memoryHalt = true;
          if (typeof env.__haltActive === 'boolean') env.__haltActive = true;
          return new Response(JSON.stringify({ ok: true, active: true }), {
            status: 200,
          });
        }
        if (path === '/resume' || path.endsWith('/resume')) {
          memoryHalt = false;
          if (typeof env.__haltActive === 'boolean') env.__haltActive = false;
          return new Response(JSON.stringify({ ok: true, active: false }), {
            status: 200,
          });
        }
        return new Response('nope', { status: 404 });
      },
    } as unknown as DurableObjectStub;
  }
  return ns.get(ns.idFromName('cbridge-halt-state'));
}

export async function isHalted(env: Env): Promise<boolean> {
  if (typeof env.__haltActive === 'boolean') return env.__haltActive;
  try {
    const stub = getStub(env);
    const res = await stub.fetch('https://halt/status');
    if (!res.ok) return false;
    const body = (await res.json()) as { active?: boolean };
    return body.active === true;
  } catch {
    return memoryHalt;
  }
}

export async function engageHalt(
  env: Env,
  opts: { reason?: string; requestId?: string; now?: number } = {}
): Promise<HaltStatus> {
  const stub = getStub(env);
  const res = await stub.fetch('https://halt/halt', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(opts),
  });
  if (!res.ok) throw new Error(`halt engage failed: ${res.status}`);
  if (typeof env.__haltActive === 'boolean') env.__haltActive = true;
  memoryHalt = true;
  return { active: true, reason: opts.reason, at: opts.now };
}

export async function clearHalt(
  env: Env,
  opts: { reason?: string; requestId?: string; now?: number } = {}
): Promise<HaltStatus> {
  const stub = getStub(env);
  const res = await stub.fetch('https://halt/resume', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(opts),
  });
  if (!res.ok) throw new Error(`halt clear failed: ${res.status}`);
  if (typeof env.__haltActive === 'boolean') env.__haltActive = false;
  memoryHalt = false;
  return { active: false, reason: opts.reason, at: opts.now };
}

/** Test helper. */
export function resetMemoryHalt(): void {
  memoryHalt = false;
}

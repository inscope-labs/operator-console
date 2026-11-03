/**
 * GET /health — contract 3.1.
 * Unauthenticated; optional bearer accepted, never gated.
 */

import { register, type RouteHandler } from './router';
import { CONTRACT_VERSION } from '../config/constants';
import { parseGatewayConfig } from '../config/env';
import { now as clockNow } from '../util/clock';

const json = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });

const getHealth: RouteHandler = async (_request, env) => {
  const parsed = parseGatewayConfig(env as unknown as Record<string, unknown>);
  const timestamp = clockNow();
  if (!parsed.ok) {
    return json({
      status: 'degraded',
      environment: null,
      version: CONTRACT_VERSION,
      timestamp,
      config: { valid: false, problems: parsed.problems },
    });
  }
  return json({
    status: 'ok',
    environment: parsed.config.environment,
    version: CONTRACT_VERSION,
    timestamp,
  });
};

export function registerHealthRoutes(): void {
  register('GET', '/health', getHealth, {
    requiresOperatorToken: false,
    requiresCapability: false,
  });
}

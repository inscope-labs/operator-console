/**
 * GET /health — unauthenticated (optional bearer accepted, never gated).
 * Contract 3.1 + GW-2: reports environment and contract version;
 * on config problems, reports problem classes only (no secret values).
 */

import { register, type RouteHandler } from './router';
import { CONTRACT_VERSION } from '../config/constants';
import { parseGatewayConfig } from '../config/env';

const json = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });

const getHealth: RouteHandler = async (_request, env) => {
  const parsed = parseGatewayConfig(env as unknown as Record<string, unknown>);
  if (!parsed.ok) {
    return json(
      {
        ok: false,
        contractVersion: CONTRACT_VERSION,
        config: {
          valid: false,
          problems: parsed.problems,
        },
      },
      200
    );
  }
  const { config } = parsed;
  return json({
    ok: true,
    contractVersion: CONTRACT_VERSION,
    environment: config.environment,
    attestationMode: config.attestationMode,
    config: {
      valid: true,
      bindings: {
        operatorToken: config.bindings.hasOperatorToken,
        repos: config.bindings.hasRepos,
        signingKeys: config.bindings.hasSigningKeys,
        githubApp: config.bindings.hasGithubApp,
      },
    },
  });
};

export function registerHealthRoutes(): void {
  register('GET', '/health', getHealth, {
    requiresOperatorToken: false,
    requiresCapability: false,
  });
}

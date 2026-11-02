/**
 * Path matching + auth middleware order.
 * Spec §9. Config validation runs first (GW-2): invalid config → 503 CONFIG_INVALID
 * for every path except GET /health.
 */

import type { Env } from '../index';
import { errorResponse, operatorAuthResponse } from '../errors/respond';
import { verifyOperatorToken } from '../auth/operator-token';
import { parseGatewayConfig } from '../config/env';

export type RouteHandler = (
  request: Request,
  env: Env,
  ctx: ExecutionContext,
  params: Record<string, string>
) => Promise<Response>;

interface Route {
  method: string;
  pattern: RegExp;
  paramNames: string[];
  handler: RouteHandler;
  requiresCapability: boolean;
  requiresOperatorToken: boolean;
}

const routes: Route[] = [];

export function register(
  method: string,
  path: string,
  handler: RouteHandler,
  opts: { requiresCapability?: boolean; requiresOperatorToken?: boolean } = {}
): void {
  const paramNames: string[] = [];
  const patternSource =
    '^' +
    path
      .replace(/:([A-Za-z_][A-Za-z0-9_]*)/g, (_m, name) => {
        paramNames.push(name);
        return '([^/]+)';
      })
      .replace(/\//g, '\\/') +
    '$';
  routes.push({
    method: method.toUpperCase(),
    pattern: new RegExp(patternSource),
    paramNames,
    handler,
    requiresCapability: opts.requiresCapability ?? false,
    requiresOperatorToken: opts.requiresOperatorToken ?? false,
  });
}

export function clearRoutes(): void {
  routes.length = 0;
}

export function listRoutes(): ReadonlyArray<{
  method: string;
  pattern: string;
  requiresOperatorToken: boolean;
  requiresCapability: boolean;
}> {
  return routes.map((r) => ({
    method: r.method,
    pattern: r.pattern.source,
    requiresOperatorToken: r.requiresOperatorToken,
    requiresCapability: r.requiresCapability,
  }));
}

export async function dispatch(
  request: Request,
  env: Env,
  ctx: ExecutionContext
): Promise<Response> {
  const url = new URL(request.url);
  const method = request.method.toUpperCase();
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  const isHealth = method === 'GET' && url.pathname === '/health';

  // GW-2: fail closed on invalid config except /health
  if (!isHealth) {
    const cfg = parseGatewayConfig(env as unknown as Record<string, unknown>);
    if (!cfg.ok) {
      return errorResponse(
        'CONFIG_INVALID',
        'Gateway configuration is invalid or incomplete',
        requestId
      );
    }
  }

  for (const route of routes) {
    if (route.method !== method) continue;
    const match = url.pathname.match(route.pattern);
    if (!match) continue;

    const params: Record<string, string> = {};
    route.paramNames.forEach((name, i) => {
      params[name] = decodeURIComponent(match[i + 1]);
    });

    if (route.requiresOperatorToken) {
      if (!verifyOperatorToken(request, env.OPERATOR_TOKEN ?? '')) {
        return operatorAuthResponse(requestId);
      }
    }

    try {
      const response = await route.handler(request, env, ctx, params);
      if (!response.headers.has('x-request-id')) {
        const headers = new Headers(response.headers);
        headers.set('x-request-id', requestId);
        return new Response(response.body, {
          status: response.status,
          statusText: response.statusText,
          headers,
        });
      }
      return response;
    } catch (e) {
      const message = e instanceof Error ? e.message : 'internal error';
      return errorResponse('NETWORK', message, requestId);
    }
  }

  return errorResponse(
    'NETWORK',
    `no route for ${method} ${url.pathname}`,
    requestId
  );
}

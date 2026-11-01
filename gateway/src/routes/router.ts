/**
 * Path matching + auth middleware order.
 * Spec §9 — authentication headers checked first; capability required on
 * mutating / privileged routes; /admin/* uses OPERATOR_TOKEN only.
 *
 * Fail closed: unknown routes and missing credentials yield structured errors.
 */

import type { Env } from '../index';
import { errorResponse, operatorAuthResponse } from '../errors/respond';
import { verifyOperatorToken } from '../auth/operator-token';

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
  /** When true, require a valid capability in the body. */
  requiresCapability: boolean;
  /** When true, require OPERATOR_TOKEN (operator surface). */
  requiresOperatorToken: boolean;
}

const routes: Route[] = [];

/**
 * Register a route. Patterns use :param syntax (e.g. /tasks/:id/publish).
 * Call from route modules at module load time (via register*Routes helpers).
 */
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

/** Test helper — clear the route table between unit tests. */
export function clearRoutes(): void {
  routes.length = 0;
}

/** Test helper — inspect registered routes. */
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

/**
 * Dispatch an incoming request.
 * Order: match → OPERATOR_TOKEN (if required) → handler.
 * Capability body checks remain in the handler until a shared body parser exists.
 */
export async function dispatch(
  request: Request,
  env: Env,
  ctx: ExecutionContext
): Promise<Response> {
  const url = new URL(request.url);
  const method = request.method.toUpperCase();
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();

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
      // Ensure request id is visible on success paths too.
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

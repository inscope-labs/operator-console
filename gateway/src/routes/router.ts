/**
 * Path matching + auth middleware order.
 * Spec §9 — authentication headers checked first; capability required on
 * mutating / privileged routes; /admin/* uses OPERATOR_TOKEN only.
 *
 * Fail closed: unknown routes and missing credentials yield structured errors.
 */

import type { Env } from '../index';
import { errorResponse, operatorAuthResponse } from '../errors/respond';

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
  /** When true, require OPERATOR_TOKEN (admin surface). */
  requiresOperatorToken: boolean;
}

const routes: Route[] = [];

/**
 * Register a route. Patterns use :param syntax (e.g. /tasks/:id/publish).
 * Call from route modules at module load time.
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

/**
 * Dispatch an incoming request.
 * Order: match → OPERATOR_TOKEN (if required) → capability (if required) → handler.
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

    // Admin surface — OPERATOR_TOKEN only
    if (route.requiresOperatorToken) {
      const auth = request.headers.get('authorization') ?? '';
      const token = auth.startsWith('Bearer ') ? auth.slice(7) : '';
      // TODO: constant-time compare against env.OPERATOR_TOKEN
      if (!token || token !== env.OPERATOR_TOKEN) {
        return operatorAuthResponse(requestId);
      }
    }

    // Confirmation-bearing routes — capability required
    if (route.requiresCapability) {
      // Capability is expected in the JSON body; extraction left to the handler
      // after body parse. Router only enforces presence of the header/body contract
      // at a higher level once body parsing is shared.
      // TODO: optional early reject if Content-Type is missing.
    }

    try {
      return await route.handler(request, env, ctx, params);
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

// Route modules register themselves by importing this module and calling register().
// Example (in routes/tasks.ts):
//   register('POST', '/tasks/:id/publish', publishHandler, { requiresCapability: true });

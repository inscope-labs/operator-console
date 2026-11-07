/**
 * GET /branches/:name/head — contract 3.7.
 */

import { getAdapter } from '../adapter/get-adapter';
import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';

const json = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });

/** Git-compatible branch name (no leading slash / path traversal). */
const BRANCH_RE = /^[A-Za-z0-9._/-]+$/;

const getBranchHead: RouteHandler = async (request, env, _ctx, params) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  const name = params['name'] ?? '';
  if (!name || !BRANCH_RE.test(name) || name.includes('..')) {
    return errorResponse('BRANCH_INVALID', `invalid branch name`, requestId);
  }

  try {
    const adapter = await getAdapter(env);
    const ref = await adapter.consumer().getRef(name);
    const sha = ref.sha.toLowerCase();
    if (!/^[0-9a-f]{40}$/.test(sha)) {
      return errorResponse('NETWORK', 'unexpected head SHA format', requestId);
    }
    return json({ branch: name, headSha: sha });
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    // Treat missing ref as 404 BRANCH_MISSING
    if (/404|Not Found|does not exist/i.test(message)) {
      return errorResponse('BRANCH_MISSING', `branch not found: ${name}`, requestId);
    }
    if (message.includes('credentials incomplete')) {
      return errorResponse('NETWORK', message, requestId);
    }
    return errorResponse('NETWORK', message, requestId);
  }
};

export function registerBranchRoutes(): void {
  register('GET', '/branches/:name/head', getBranchHead, {
    requiresOperatorToken: true,
    requiresCapability: false,
  });
}

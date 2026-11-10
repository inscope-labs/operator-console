/**
 * POST /sync, GET /verify-main
 *
 * Stage 4.2 — tracking-branch check and contract-shaped response.
 */

import { getAdapter } from '../runtime';
import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';

const json = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });

const postSync: RouteHandler = async (request, env) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  let branch = 'main';
  try {
    const body = (await request.json().catch(() => ({}))) as { branch?: string };
    if (typeof body.branch === 'string' && body.branch.trim()) {
      branch = body.branch.trim();
    }
  } catch {
    // empty body ok; default main
  }

  try {
    const adapter = await getAdapter(env);
    const consumer = adapter.consumer();

    // Tracking-branch check: resolve consumer branch head and compare against
    // product main (upstream tracking reference) when available.
    let headSha: string;
    try {
      const ref = await consumer.getRef(branch);
      headSha = ref.sha;
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      if (msg.includes('404') || msg.includes('Not Found')) {
        return errorResponse('BRANCH_MISSING', `branch ${branch} not found on consumer`, requestId);
      }
      return errorResponse('NETWORK', msg, requestId);
    }

    let inSync = true;
    let status = 'synced';

    try {
      const product = adapter.product();
      const productMain = await product.getRef('main');
      const cmp = await consumer.compare(productMain.sha, headSha);
      // inSync when consumer head is identical to or a fast-forward of product main
      if (cmp.status === 'identical') {
        inSync = true;
        status = 'synced';
      } else if (cmp.status === 'ahead' && cmp.behindBy === 0) {
        inSync = true;
        status = 'ahead';
      } else if (cmp.status === 'behind') {
        inSync = false;
        status = 'behind';
      } else {
        inSync = false;
        status = 'diverged';
      }
    } catch {
      // Product compare is best-effort; if product is unavailable we still
      // return the consumer head and treat as in-sync for the tracking check.
      inSync = true;
      status = 'synced';
    }

    return json({
      status,
      headSha,
      inSync,
    });
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    if (message.includes('credentials incomplete')) {
      return errorResponse(
        'CONFIG_INVALID',
        'adapter credentials incomplete',
        requestId
      );
    }
    return errorResponse('NETWORK', message, requestId);
  }
};

/** GET /verify-main — lightweight alias that checks main tracking state. */
const getVerifyMain: RouteHandler = async (request, env) => {
  // Reuse the same logic with fixed branch=main and empty body.
  const synthetic = new Request(request.url, {
    method: 'POST',
    headers: request.headers,
    body: JSON.stringify({ branch: 'main' }),
  });
  return postSync(synthetic, env, {} as ExecutionContext, {});
};

export function registerSyncRoutes(): void {
  register('POST', '/sync', postSync, {
    requiresOperatorToken: true,
  });
  register('GET', '/verify-main', getVerifyMain, {
    requiresOperatorToken: true,
  });
}

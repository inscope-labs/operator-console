/**
 * cbridge Worker entry point.
 *
 * Sole enforcement point for the operator console.
 * All routes, capability verification, guards and GitHub interaction
 * are reached through this handler.
 *
 * Full routing, middleware and Durable Object bindings are TODO.
 */

export interface Env {
  // Secrets / bindings (names only — values never in source)
  SIGNING_KEYS: string;          // JSON public keys
  OPERATOR_TOKEN: string;
  // GITHUB_TOKEN is obtained via InstallationTokenProvider, not a long-lived secret
  REPOS: string;                 // JSON map of RepoConfig

  // Durable Object bindings
  PLAN_DO: DurableObjectNamespace;
  NONCE_STORE: DurableObjectNamespace;
  ENROLMENT_REGISTRY: DurableObjectNamespace;
  AUDIT_STREAM: DurableObjectNamespace;
}

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    // TODO: route table, OPERATOR_TOKEN check, capability verification,
    //       guard runner, adapter construction, audit emission.
    // Fail closed on any ambiguous case.
    return new Response(
      JSON.stringify({
        error: 'NOT_IMPLEMENTED',
        message: 'cbridge scaffold — routes not yet wired',
        request_id: crypto.randomUUID(),
      }),
      {
        status: 501,
        headers: { 'content-type': 'application/json' },
      }
    );
  },
};

// Durable Object class exports will be added here once implemented.
// export { PlanDO } from './durable-objects/plan-do';
// export { NonceStore } from './durable-objects/nonce-store';
// ...

# Environments

Names, hostnames and where each environment is allowed to run. **No secrets belong in this file**: operator bearer tokens,
signing keys, GitHub App credentials and attestation roots live in the Worker's secrets and in the operator's own store.
Authority: `docs/adr/0010-environments-hostnames-and-workflow.md`, `docs/operator-guide.md` §2.1 and §5.

| Environment | Where it runs | Gateway base URL | Transport | Lab mode | Status |
|---|---|---|---|---|---|
| `test` | Local (a developer machine reached from the Android emulator), or the Cloudflare Worker `ops-test` once EXT-5 is done | `http://10.0.2.2:8787` (debug builds only, `BuildConfig.DEBUG_GATEWAY_BASE_URL`) | Cleartext allowed to `10.0.2.2`, `localhost`, `127.0.0.1` only (`app/src/debug/res/xml/network_security_config.xml`) | Allowed, and only against a gateway reporting `"environment": "test"` | Available for the PoC |
| `staging` | Cloudflare Workers, Gateway A, Worker `ops-stage` | Not yet recorded: fixed at first deployment (ADR 0010 decision 2) | TLS with SPKI pinning; pins are not captured yet (EXT-6, task 39) | Never | **Not deployed** |
| `production` | Cloudflare Workers, Gateway A, Worker `ops-production` | Not yet recorded: fixed at first deployment (ADR 0010 decision 2) | TLS with SPKI pinning; pins are not captured yet (EXT-6, task 39) | Never | **Not deployed** |

## Decided on 18 Nov 2026 (ADR 0011)

- Worker names: `ops-test`, `ops-stage`, `ops-production`. `gateway/wrangler.toml.template` still has `name = "cbridge"`, so the
  deploy must set the name per environment; not changed yet.
- Hostnames stay unrecorded until first deployment; SPKI pins are captured then (task 39, EXT-6).
- GitHub environments `staging` and `production`, with the operator as required reviewer on `production`, were requested now.
  They do **not** exist yet: the token Claude uses cannot create environments (403), so the operator creates them in repository
  settings. The `release` environment and the `v*` tag ruleset come before the first release.
- `applicationId` stays `com.inscopelabs.buildengine` (debug builds add `.debug`).
- The PoC runs on `test` only.

## Hostnames

The operator supplied `qnuralink.workers.dev` for Gateway A in staging and in production (ADR 0010). A Worker is served at
`<worker-name>.<account-subdomain>.workers.dev`, so the exact hostnames are written into this table when the first deployment
exists, in the same pull request that records the pins. Until then no hostname is baked into source, and the release posture
check (`scripts/release-check.py`) tracks the remaining `gateway.invalid` placeholders (tasks 17, 18, 39).

## Rules

1. **One environment per enrolment.** Device keys are namespaced by environment and host. An enrolment on `staging` cannot mint
   capabilities for `production`, and the same device key is never enrolled on both.
2. **The gateway checks the environment.** It rejects a request whose configured environment differs from the Worker's
   `ENVIRONMENT` value. The Worker's `ENVIRONMENT` is a deployment variable (`gateway/wrangler.toml.template`, EXT-5).
3. **Debug builds are not authority clients.** Operational approvals on `staging` or `production` come from release-signed APKs
   (operator guide §5.2). The PoC runs on the debug build against `test`.
4. **Confirmation tier by environment.** `hardware_confirm` is accepted everywhere. A production Worker may refuse
   `in_app_confirm` enrolments with `AUTH_TIER_NOT_ALLOWED` (ADR 0012).
5. **Changing the hostname later is a re-enrolment.** So is changing `applicationId` (EXT-9), so decide both before any
   enrolment you want to keep.

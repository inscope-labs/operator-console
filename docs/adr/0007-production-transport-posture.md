# ADR 0007 — Production Transport Posture: Pinning, Operator Bearer, Device-Signed Capability

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-11-02 |
| **Decision IDs** | C-1, C-1b |
| **Related** | ADR 0003, ADR 0006, `docs/gateway-contract.md`, build-engine ADR 0008 |

---

## Context
ADR 0006 deferred mTLS for the test application. The build plan (now `docs/V0.6.0-BUILD-PLAN.md`) asks for an explicit production posture. The gateway runs on `workers.dev`, which cannot terminate client-side mTLS certificates; terminating them needs a custom zone.

## Decision
1. **Production posture (C-1, option a):** HTTPS with certificate pinning (a primary and a backup pin), an operator bearer credential, and the device-signed `ctxop-v1` capability. mTLS stays deferred.
2. **Zone.** Whether test or production, `qnuralink.workers.dev` is the zone until further notice, as the operator instructed. The operator also instructed that side effects of this be mitigated: distinct Worker names and distinct hostnames per environment, distinct credentials per environment, no wildcard routes, and per-environment minting so a capability or link from one environment is not accepted by another.
3. **Pins (C-1b, option a).** A release build is refused unless a primary and a backup pin are present and a pin-rotation procedure is documented.

### What a pin is
A pin is the fingerprint of a server public key that the app has built in. The app refuses to talk to any server that presents a different key, which defeats forged certificates. The **primary** pin matches the key in use now. The **backup** pin matches a key you will move to later, so a planned or emergency rotation does not lock every installed app out. On `workers.dev` Cloudflare manages the certificates, so the keys are not the operator's to generate: the pin design (OC-2) must pin public keys, not whole certificates, and must choose keys that survive Cloudflare's own rotation. The operator asked for this explanation because the term was unfamiliar.

## Consequences
- ADR 0003 and ADR 0006 gain an "Amended by ADR 0007" line; their text is otherwise unchanged.
- OC-2 designs the pin set and the rotation procedure. Until it is done, release builds are refused.
- Moving to full mTLS later requires a custom zone and a new decision.

## Operator authorization
Recorded from the operator's completed decisions questionnaire, uploaded 2026-11-02. Approver: John Wendell, operator and lead architect, inscope-labs. Decisions closed as answered; Claude may open pull requests touching only decision records (`docs/**`) and does not merge, tag, release or push to `main`; the session token is used for this assignment only. Nothing on Cloudflare is created, changed or deployed and no secret is handled in this assignment.

# ADR 0002 — No repository credentials on the device

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-10-30 |
| **Related** | Design review §4, §9, §15; `docs/threat-model.md` |

---

## Context

The operator's original proposal for this console was: the app holds the operator's
GitHub credential in hardware-backed storage; the operator explicitly authorises a
Cloudflare session; the Worker injects the credential, performs the operation, and
discards it at session end. The stated principle was *"credentials held on-device,
Cloudflare purely ephemeral"*.

That premise is the part that does not survive review.

### Why it does not survive

**The credential is the one that reaches the crown jewel.** The build engine's entire
confidentiality model rests on one separation: *plan = private, product = public*. The
consumer repository holds the plan, the work orders, the submissions and the unresolved
framework decisions. The operator's repository credential is by construction the one
that can read and rewrite all of it.

**The build engine already solved this, and solved it the other way.** The write
gateway's own protocol states:

> The gateway holds a credential scoped to the **submissions repository only**. That
> credential has **no access to the private consumer repository**. A leak of the
> credential cannot reach the shipped product.

And the operator runbook for that gateway states the rule as a principle: the three
secrets it holds — `SIGNING_KEYS` (mint), `OPERATOR_TOKEN` (`/admin/*`) and
`GITHUB_TOKEN` (submissions repo only) — are such that **holding one does not imply
holding another**.

The proposal reverses that: a credential that can reach the private plan would sit
inside a Cloudflare isolate, on a phone, in transit, and in logs.

**"Ephemeral" is not enforceable as written.** Worker isolates are recycled without a
guaranteed teardown, and memory is not guaranteed to be zeroed. Workers Logs are enabled
by default with 3–7 day retention. A Durable Object's storage persists and is billed.
The design's own §1 claim that "Cloudflare stores no tokens" is false the moment anyone
logs.

What *is* enforceable is **bounded lifetime**: a credential whose issuer you control
expires on its own, whether or not you can erase it.

---

## Decision

**The Operator Console holds no GitHub credential of any kind. None.**

Concretely:

| Credential | Held by | Authorises | Lifetime |
|---|---|---|---|
| Device keypair (non-exportable, hardware-backed where available) | Console | Signing operator capabilities. Not accepted by GitHub at all. | Device lifetime; revocable by re-enrolment |
| Operator credential (gateway-scoped) | Console | The gateway's `/admin/*` surface: request a capability, revoke a task, read status. **Grants no repository access.** | Rotatable; revocable server-side at any time |
| Signing keys | Gateway | Minting and verifying capabilities. Holds no repository authority. | Rotatable per `kid` |
| GitHub credential (recommended: GitHub App installation token) | Gateway | Writing the minted branch and the consumer repository's engine branch. Scoped by ruleset so it cannot reach `main` or protected paths. | 1 hour, auto-expiring; revocable via `DELETE /installation/token` |

**Consequences of this decision:**

1. There is nothing repository-scoped on the device to exfiltrate.
2. There is no credential to inject over the network at session start, and therefore no
   injection step, and nothing to clear at session end.
3. The gateway's credential was never the operator's and was always short-lived by
   construction — so the "ephemerality" problem largely dissolves rather than being
   solved.
4. Losing the phone yields a capability for one branch of one task, bounded by a
   five-minute TTL, not a repository.

### The naming rule that follows

Do not reuse "operator token" and "GitHub token" for different things. The write
gateway already uses `OPERATOR_TOKEN` and `GITHUB_TOKEN` for two of the three secrets
above. The console's credential is named for what it is — an **`OPERATOR_CREDENTIAL`**
presented to the gateway's operator surface — and the gateway's stays **`GITHUB_TOKEN`**.
Ambiguity here becomes a credential-boundary defect later.

---

## Consequences

### Positive

- The console's compromise blast radius is bounded by the capability format, not by a
  token's scope.
- The device credential is *gateway*-scoped, so its compromise is a gateway-scope
  compromise, not a repository compromise.
- The audit trail and the credential lifecycle are both enforceable, because both are
  properties of credentials whose issuer we control.

### Negative

- The console needs a gateway credential and an enrolment registry — more moving parts
  than "put the PAT in the app".
- Every privileged operation now has a round trip: mint locally, present to the
  gateway, let the gateway act with its own credential. Latency and complexity.
- The gateway must hold and rotate a GitHub credential. That is a real operational
  responsibility, and it is the *correct* place for it.

### Neutral

- The device still holds a secret (the keypair and the operator credential). What
  changed is its *scope*, not its existence.

---

## Alternatives considered

| Alternative | Why not |
|---|---|
| Keep the GitHub PAT on the device, as originally proposed | The credential reaches the private plan. Everything else in the design is built around the assumption that it does not. |
| Inject the PAT into the Worker at session start, as originally proposed | Puts the reach-to-the-plan credential inside a third-party runtime, on a phone, in transit and in logs. Also not enforceable as "ephemeral" (§ above). |
| Hold the PAT on the device, but send it directly to GitHub rather than through the gateway | Moves the credential off the phone on every request, re-exposing it to interception. Also duplicates the gateway's write path and loses the single enforcement point. |
| Use a fine-grained PAT with no expiry | Defeats the bounding principle entirely. Revocation is manual, and "never expires" is the worst of both. |
| Use the operator's human GitHub account from the console | The whole point of the console is that it is *not* the operator's human account. The human account merges pull requests into `main`; the console may only stage. |

---

## Enforcement

This is not a style preference, so it is mechanically checkable:

- The app requests exactly two permissions: `INTERNET` and `USE_BIOMETRIC`. No storage,
  no location, no contacts.
- `data/local/OperatorCredentialStore.kt` holds a gateway-scoped credential only, in
  Keystore-backed encrypted storage.
- There is no code path that accepts a GitHub token. No class, no preference key, no
  request field.
- The build engine's own secret scanner treats `ghp_` and `github_pat_` as patterns to
  detect in submissions; the console's `util/Redactor` carries the capability
  signature and nonce in the same forbidden set.

---

## Revisit conditions

This decision should be reopened only if:

- The gateway credential model changes in a way that makes the console's own credential
  repository-scoped.
- The operator decides the console must act as the operator's human account (which would
  also change D-d and the ruleset bypass list).

# Architecture

How the Operator Console is put together, and where each package sits in the design
review.

The console is the **sole seat of operator authority** (`CNS-1:1`): it represents human
intent and nothing else. The gateway is the **sole enforcement point** (`CTR-1:1`). The
console is untrusted for enforcement (`CNS-1:5`) — every action it takes is an envelope
that the gateway alone validates.

**The Cloudflare gateway is a separate project.** It has its own repository, its own
`wrangler.toml` and its own fail-closed deploy gate. Nothing in this tree implements
enforcement, holds the GitHub credential, or decides whether a write is allowed.

---

## 1. The shape of the thing

```
┌──────────────────────────────┐
│  Operator Console (this repo)│   holds:
│  Android / Kotlin / Compose  │     · device keypair (Keystore / StrongBox)
│                              │     · operator credential → gateway /admin/*
│                              │     · NO GitHub credential of any kind
└──────────────┬───────────────┘
               │  ① operator authenticates locally
               │  ② console mints a scoped capability
               │     (biometric confirmation)
               │  ③ capability presented to the gateway
               ▼
┌──────────────────────────────┐
│  Cloudflare Gateway          │   holds:
│  (separate project)          │     · SIGNING_KEYS   → verify capabilities
│  sole enforcement point      │     · OPERATOR_TOKEN → admin surface
│  enforces guard_task()       │     · GITHUB_TOKEN   → scoped write, short TTL
│  writes only minted branches │   writes only minted branches
└──────────────┬───────────────┘
               │  GitHub Data API
               ▼
   consumer repo (private)  ──►  product repo (public), via PR only
```

The console's job is to make **one specific, scoped, expiring decision** on the
operator's behalf and to prove it to the gateway. Everything else it does is read-only.

---

## 2. Package → design review mapping

| Package | Review § | Why it lives there |
|---|---|---|
| `core.crypto` | §4, §13 | The device key never leaves the Keystore. Posture is reported honestly via `KeyInfo.isInsideSecureHardware` — never assumed. |
| `core.security.BiometricGate` | §5, §13 | The gate runs *before* minting. No capability exists until the operator confirms. |
| `core.security.BiometricPolicy` | §13 | `setAllowedAuthenticators` is explicit; the device credential must be named, not implied. |
| `core.security.DeviceAttestation` | §13 | Network posture. mTLS / attestation so a proxy cannot become a credential-capture point. |
| `core.security.CertificatePinning` | §13 | Pins the gateway host. |
| `core.security.DevicePosture` | §13 | What the device can honestly claim. |
| `core.security.SecureWindow` | §13 | `FLAG_SECURE` — the console is not observable in screenshots. |
| `core.network` | §7, §8 | The only code that speaks HTTP. Every request is audited gateway-side; nothing here logs a credential, a signature or a nonce. |
| `core.network.MtlsConfig` | §13, ADR 0003 | Mutually authenticated transport, not merely TLS. |
| `core.network.ErrorMapper` | §12, App. B.3 | The V5–V8 rejections map to four *distinct* states. A refused scope mismatch, a replayed capability, a drifted head and an active halt are not the same message. |
| `core.time.Clock` | §5 | Injectable, so capability TTL behaviour is testable without waiting five minutes. |
| `core.result.AppResult` | — | The result type shared across layers. |
| `capability.model` | §5 | The signed artefact and the closed operation enumeration. |
| `capability.codec` | §5 | The `ctxop-v1` wire format. Field order is part of the contract. |
| `capability.signing` | §5, D-a | The seam between the two signing directions. Model B is the default. |
| `capability.CapabilityMinter` | §5 | Builds and signs. The five-minute TTL lives here (`D-b`). |
| `capability.NonceSource` | §5 | Single-use nonce → replay protection. |
| `data.remote` | §12 | The gateway surface. The only component that speaks HTTP. |
| `data.local.OperatorCredentialStore` | §4 | Gateway-scoped credential only. **Never a GitHub token.** |
| `data.local.SelectionStore` | §1 | Repository and plan selection are not security state — plain storage is enough. |
| `data.local.DeviceEnrollmentStore` | §5, D-a | The enrolment registry Model B needs: `kid` ↔ key alias ↔ public key. |
| `data.repository` | §12 | Wire DTOs → domain models. |
| `domain.model` | §12 | `TaskBranch`, `OperationRequest` and `SecurityPosture` mirror the review's vocabulary. |
| `domain.usecase.read` | §16 phase 1 | Read-only commands. Half the operator surface, and the half that needs no write capability. |
| `domain.usecase.write` | §12 | One use case per privileged operation, each minting its own scoped capability. |
| `domain.usecase.enrollment` | §5, D-a | Key enrolment, rotation, and the honest posture report. |
| `domain.usecase.common.ConfirmAndMintCapabilityUseCase` | §5 | **The single path by which a capability comes into existence.** No use case may mint its own. |
| `domain.usecase.common.VerifyBranchHeadUseCase` | §5 | The head-drift precondition — a capability minted against a stale SHA must not authorise the write. |
| `ui.publish` | §12, App. B.3 | Branch, exact 40-character head SHA and guard summary, all visible before anything is signed. |
| `ui.decision` | §12, D-e | The highest-authority action. Its own confirmation and attribution. |
| `ui.components.SecurityPostureBadge` | §13 | Hardware-backed vs software-backed, stated honestly. |
| `ui.components.BranchShaRow` | §12 | The full SHA in monospace. An abbreviated SHA is not enough. |
| `util.Redactor` | §8 | The log redaction gate. Forbidden patterns are *replaced*, not filtered — mirroring the gateway's `src/audit/redaction.ts`. |
| `util.Logger` | §8 | Every line passes through the gate. |

---

## 3. Phase mapping

From the recommended sequence (§16):

| Phase | Packages in scope | Exit gate |
|---|---|---|
| **0** — decisions and configuration | — (no code) | D-a…D-g resolved; GitHub rulesets created; V1–V4 captured |
| **1** — read-only console | `domain.usecase.read`, `ui.dashboard`, `ui.plans`, `ui.taskdetail`, `data.remote` (read), `core.network` | Plan state visible from the phone with **zero** write capability |
| **2** — single-action approval | `core.crypto`, `core.security`, `capability.*`, `domain.usecase.common`, `domain.usecase.enrollment`, `ui.publish`, `ui.enrollment`, `data.local` | One branch publishable under a scoped, expiring capability; every action audited |
| **3** — plan store port | `domain.usecase.write` (status changes), `data.remote` (compare-and-swap) | Lost updates impossible |
| **4** — full surface | `domain.usecase.write` (upload, reconcile, halt/resume), `util` | Guard parity proven by executed tests against the same fixtures |
| **5** — retirement | — | The manual publish path is no longer the only way to publish |

This scaffold covers the **phase 1–2 shape**: read use cases, the capability machinery,
the enrolment surface and the publish confirmation all exist as placeholders.

---

## 4. Layering rules

The dependency direction is one-way, and it is enforced by package structure:

```
ui  ──►  domain  ──►  data  ──►  core
 │         │           │
 └─────────┴───────────┴──►  capability ──► core.crypto
```

- `ui` may call `domain` and read `domain.model`. It must not reach `data` directly.
- `domain` declares repository *interfaces*; `data` implements them. `domain` does not
  know that HTTP exists.
- `core` is infrastructure: crypto, security, network, time. It knows nothing about
  capabilities or repositories.
- `capability` depends on `core.crypto` (for the CSPRNG) and `core.time` (for the
  expiry). It does not depend on `data` or `ui`.

The one wrinkle: `capability/NonceSource.kt` declares the interface, while
`core/crypto/SecureRandomSource.kt` is the CSPRNG. The implementation that joins them
(`DeviceNonceSource`) lives in the `capability` package, so `core` never has to depend
upward on `capability`. If the teams grow and the packages become modules, this ordering
translates 1:1 without a re-architecture.

---

## 5. Deliberately absent

Their absence is a security property. Each one has a reason.

| Absent | Why |
|---|---|
| Any GitHub token, PAT, OAuth token or GitHub App credential | The consumer repository is the crown jewel. Nothing outside the gateway holds a credential that reaches it. (§4) |
| A `verify` screen or `verify.argv` execution path | Plan-supplied commands are a code-execution surface. They run on the operator's machine, not in the gateway and not from a phone. (§12.1) |
| `bootstrap` / `init` | Creates repositories and branches. Destructive, rare, needs a real git identity. Terminal-only. (§12.1) |
| A local git worktree, a shell bridge, `Runtime.exec` | The console is not a Termux replacement. It is an approval surface. |
| Durable audit storage on-device | The audit trail lives in the private consumer repository. (§8) |
| Persisted session tokens | The session is in-memory only. The five-minute capability TTL is the real bound. (§9) |
| A `git` dependency, a `subprocess`, a `java.util.Process` | Same reason as above. |

---

## 6. Module split, when it is needed

The package boundaries above map 1:1 onto Gradle modules without rewriting code:

```
:app                → MainActivity, NavHost, DI wiring
:core:crypto        → core/crypto
:core:security      → core/security
:core:network       → core/network
:capability         → capability/
:domain             → domain/
:data               → data/
:feature:read       → ui/dashboard, ui/plans, ui/taskdetail + read use cases
:feature:publish    → ui/publish + publish use case
:feature:enrollment → ui/enrollment + enrolment use cases
```

Keeping the packages clean now makes that split mechanical later.

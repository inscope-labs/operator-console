# ADR 0001 — Capability signing direction

| | |
|---|---|
| **Status** | Accepted, with Model A retained behind an interface |
| **Date** | 2026-10-30 |
| **Decision ID** | D-a |
| **Supersedes** | — |

---

## Context

The operator capability (§5 of the design review) is a signed, scoped, expiring artefact
that authorises exactly one privileged action. Something has to hold the key that
signs it, and something has to verify the result. There are two directions:

- **Model A — the gateway mints, the console holds the key.** The console obtains a
  capability for a pending action and presents it when the operator confirms.
- **Model B — the console mints, the gateway verifies.** The console signs the
  capability with its own device key after the biometric confirmation; the gateway
  verifies against the enrolled device public key.

The choice determines the device enrolment design, the confirmation flow, and the
key-rotation path.

Two properties are already fixed by the rest of the design and constrain the choice:

1. **No authorisation or enforcement decision may be inferred merely from
   authentication.** A session that was opened once must not authorise everything for
   its duration (`CTR-1:9`).
2. **The gateway must never widen its own authority** (`CTR-1:18`). It verifies and
   enforces; it does not mint operator authority.

Model A has one awkward property: the capability must exist *before* the operator
decides. Something has to create it in advance of the confirmation gesture, which means
either pre-issuing capabilities (an authority that exists without a decision behind it)
or having the gateway hold state for actions that may never happen.

---

## Decision

**We use Model B: the console mints, the gateway verifies.**

- The console holds a non-exportable device keypair in the Android Keystore.
- After the operator confirms with a biometric prompt, the console builds the
  `ctxop-v1` message and signs it with that key.
- The gateway verifies the signature against the public key enrolled for that device's
  key selector (`kid`).
- The capability is minted at the moment of the decision, so it is a **record of a
  human decision**, not a pre-issued permission.

Model A is **retained behind the `CapabilitySigner` interface**
(`capability/signing/CapabilitySigner.kt`) as `HmacCapabilitySigner`, so that D-a can be
reversed without re-architecting the capability machinery, the codec, or the use cases.
`DeviceKeyCapabilitySigner` is the default implementation.

---

## Consequences

### Positive

- **The artefact is created by the decision.** There is no window in which a valid
  capability exists for an action the operator has not approved.
- **The signature is bound to the operator's own device.** A stolen gateway credential
  cannot mint capabilities for the operator's branches — it can only verify them.
- **Rotation is natural.** A new key means a new `kid`; capabilities signed under the
  retired `kid` stop verifying.
- **It matches the confirmation flow exactly**, with no extra state to manage.

### Negative

- **The gateway needs a device enrolment registry.** It must hold a public key per
  enrolled device, and a revocation path for it. This is the main cost of Model B.
- **The device must hold a signing key**, where Model A would let it hold a bearer
  token. This is the *stronger* property, but it is a real implementation cost:
  Keystore, StrongBox, attestation, rotation.
- **Attestation becomes load-bearing.** Without it, "enrolled" means only "someone with
  network access asked". See ADR 0003.

### Neutral

- The capability format, the field rules and the TTL are identical under both models.
  Only the key custody differs.

---

## Alternatives considered

| Alternative | Why not |
|---|---|
| **Model A** — gateway mints, console holds the key | The capability must exist before the operator decides, which conflicts with a confirmation-driven flow. Pre-issued capabilities are an authority without a decision behind them, which is the thing the capability model exists to eliminate. |
| **A session-wide grant** — the operator authenticates once and the console may act freely for the session's duration | Explicitly rejected. This is precisely what `CTR-1:9` forbids: a broad grant inferred from a login state. It is also the shape of the original proposal, and the review's one substantive conflict with the framework. |
| **The gateway signs, the console re-signs** — a delegation chain | Two key custody paths, two failure modes, and a way for authority to be laundered. No benefit for the operator's use case. |
| **A third party signs** (a hardware token, an HSM) | Stronger in principle, but it moves the operator's decision off the phone and adds a device the operator has to carry and manage. The device keypair already delivers the property this project needs — bounded, scoped, expiring authority — at a cost the operator can bear. Revisit if the threat model changes. |

---

## Revisit conditions

This decision should be reopened if:

- The device enrolment registry proves operationally unworkable at scale.
- The threat model gains an adversary who can extract a non-exportable Keystore key.
- The operator decides that a separate physical token is acceptable.

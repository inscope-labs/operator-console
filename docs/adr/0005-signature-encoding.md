# ADR 0005 — Signature Encoding, Key Selectors, and Operation Scoping

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-10-30 |
| **Decision IDs** | D-T1, D-T1b, D-T2 |
| **Related** | `docs/capability-format.md`, `docs/gateway-contract.md`, Phase 2 |

---

## Context

The `ctxop-v1` capability specification (`docs/capability-format.md`) defines a 10-field LF-separated
message signed with an appended signature. Several implementation discrepancies must be resolved
for compatibility between Android's cryptographic APIs and Cloudflare Workers (WebCrypto):

1. **Algorithm Discrepancy (D-T1)**: The initial specification in `capability-format.md` described
   signing with HMAC-SHA256 using a shared secret. However, Model B (ADR 0001) mandates that the
   device signs with a private EC key inside the Android Keystore, which the gateway verifies against
   an enrolled public key.
2. **Signature Wire Format (D-T1b)**: The Android Keystore `java.security.Signature` engine with
   `SHA256withECDSA` produces signatures in ASN.1 DER encoding (which varies from 70 to 72 bytes).
   Cloudflare Workers' WebCrypto API (`crypto.subtle.verify`) requires raw IEEE P1363 signatures
   (exactly 64 bytes consisting of a 32-byte big-endian `r` concatenated with a 32-byte big-endian `s`).
3. **Branch Scoping for Non-Task Operations (D-T2)**: `capability-format.md` requires that `branch`
   matches `^task-[A-Za-z0-9._-]+$`. However, system-level operations such as `HALT`, `RESUME`, `SYNC`,
   and `DOCTOR` are engine-wide rather than task-scoped.

---

## Decision

### 1. Algorithm Selection via `kid` Registry (D-T1)
- The 10-field plaintext message structure remains completely identical regardless of algorithm.
- The cryptographic algorithm is **not** encoded in the wire payload. Instead, the algorithm is a
  property of the key selector (`kid`) stored in the gateway's key registry:
  - Production operator keys: Enrolled EC P-256 public keys verified with ECDSA-SHA256.
  - Lab test keys: Shared secret keys verified with HMAC-SHA256.
- The console provides two implementations of `CapabilitySigner`:
  - `DeviceKeyCapabilitySigner`: Uses the Android Keystore EC P-256 private key for genuine operator confirmations.
  - `HmacCapabilitySigner`: Uses HMAC-SHA256 with a runtime secret for Lab automation.

### 2. Mandatory Raw IEEE P1363 Signature Encoding (D-T1b)
- The wire contract mandates that all ECDSA signatures must be transmitted as raw 64-byte
  IEEE P1363 (`r || s`) encoded with **base64url without padding**.
- The Android application includes a tested converter (`core/crypto/EcdsaEncoding`) to parse
  ASN.1 DER from Android Keystore into raw 64-byte `r || s`, handling variable integer padding,
  leading zeroes, and negative sign markers.
- Conformance test vectors (`docs/fixtures/vectors/`) must test both DER and P1363 conversions.

### 3. Operation Scoping Rules (D-T2)
Scoping is strictly partitioned into two categories:

| Category | Operations | Branch Constraint | Target Verification |
|---|---|---|---|
| **Task-Scoped** | `PUBLISH`, `UPLOAD`, `RECONCILE`, `DECIDE`, `ORDER`, `SET_IN_PROGRESS`, `SET_DONE` | Must match `^task-[A-Za-z0-9._-]+$` | Must match the branch specified in the active work order. |
| **Engine-Scoped** | `HALT`, `RESUME`, `SYNC`, `DOCTOR`, `NEW_PLAN` | Must match configured engine branch (e.g. `engine` or `main`) | Verified against current head SHA of the engine branch. |

A capability minted for a task operation can never be used to authorize an engine operation, and an
engine-scoped capability cannot authorize a task publish or upload.

---

## Consequences

### Positive
- Ensures seamless interoperability between Android Keystore output and WebCrypto on Cloudflare Workers.
- Avoids leaking cryptographic algorithm details into the wire format.
- Resolves the structural contradiction between task-specific branch naming and repository-wide operations
  such as `halt` and `resume`.

### Negative
- Requires custom, rigorously tested ASN.1 DER ↔ IEEE P1363 transcoding logic in the Android client.
- The gateway must support both ECDSA and HMAC validation based on the `kid` lookup.

### Neutral
- Field ordering, LF delimiters, and the 10-field count remain invariant across all operations.

---

## Revisit Conditions

This decision should be reopened if:
- Cloudflare Workers WebCrypto adds native support for ASN.1 DER ECDSA verification.
- The gateway transitions entirely to Ed25519 (EdDSA), which natively uses fixed 64-byte signatures.

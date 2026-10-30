# ADR 0006 — Transport Security: mTLS and Play Integrity Deferred

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-10-30 |
| **Decision IDs** | D-T3, D-T4 |
| **Supersedes** | Partially modifies ADR 0003 for the test application |
| **Related** | `docs/adr/0003-mtls-to-gateway.md`, `docs/gateway-contract.md`, Phase 4 |

---

## Context

ADR 0003 specified mutual TLS (mTLS) with client certificates bound to the device's Keystore key,
along with Google Play Integrity attestation for all device enrolment requests.

While evaluating implementation for the functional Lab test console, two fundamental engineering
blockers were identified:

1. **Keystore Authentication vs TLS Handshakes (D-T3)**:
   A device key configured with `setUserAuthenticationRequired(true)` requires a user biometric
   or device credential challenge each time the key is accessed. If the TLS client engine (OkHttp /
   Conscrypt) uses this key for the client certificate handshake, every new HTTP connection,
   socket pool reuse, or TLS renegotiation would trigger a biometric prompt or fail silently in
   the background.
2. **Gateway Infrastructure Constraints (D-T3)**:
   Cloudflare Workers running on default `*.workers.dev` endpoints do not terminate client-side mTLS
   certificates. Terminating mTLS on Cloudflare requires Enterprise zone configurations or API Shield
   tunnels.
3. **Play Integrity Dependencies (D-T4)**:
   Google Play Integrity attestation requires a published application on Google Play, linked Google
   Cloud project credentials, and Google Play Services present on the device. Test environments,
   custom ROMs, emulator builds, and pre-release test devices lack active Play Integrity verdicts.

---

## Decision

### 1. Defer mTLS for the Test Application (D-T3)
- Transport security relies on **standard HTTPS (TLS 1.3)** with host verification.
- Client identity and authority are established through:
  1. The `Authorization: Bearer <operator_credential>` HTTP header for administrative gateway access.
  2. The signed `ctxop-v1` capability payload for privileged operations.
- `MtlsConfig` remains in the codebase as an unused stub, documented as deferred.

### 2. Device Enrolment Relies on Keystore Key Attestation Chain (D-T4)
- Device enrolment (`/enroll`) submits the EC public key together with the **Android Keystore Key
  Attestation certificate chain** (`DeviceAttestation.keyAttestationChain`).
- The gateway verifies the hardware attestation extension in the root X.509 certificate to confirm
  that the private key was generated within a hardware-backed Secure Element or TEE (StrongBox/TEE).
- **Play Integrity attestation (`DeviceAttestation.requestAttestation`) is deferred**. It remains an
  interface method with a stubbed implementation and is not invoked during the enrolment flow.

---

## Consequences

### Positive
- Enables the test console to talk to standard Cloudflare Worker deployments on `*.workers.dev`
  without complex TLS enterprise infrastructure.
- Allows connection pooling and background network operations without erratic biometric prompts
  during TLS handshakes.
- Enables development and testing on diverse Android hardware without Google Play Console linkage.

### Negative
- A network-level attacker who compromises an active operator credential could submit requests
  to read endpoints without presenting a client certificate. However, they cannot forge a valid
  capability without the physical device Keystore private key.
- Device posture verification relies solely on the Keystore attestation certificate chain rather
  than a full Play Integrity attestation verdict.

### Neutral
- Can be upgraded to full mTLS in production if custom enterprise domains and dedicated TLS client
  keys (independent of biometric-locked signing keys) are provisioned.

---

## Revisit Conditions

This decision should be revisited when:
- The gateway is deployed to an enterprise Cloudflare zone with custom mTLS client certificate validation.
- The project transitions to production distribution requiring full Google Play Integrity policy compliance.

# ADR 0003 — Mutually authenticated transport to the gateway

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-10-30 |
| **Related** | Design review §13, §15 (R10); `docs/device-enrollment.md`; `docs/threat-model.md` |

---

## Context

The Operator Console talks to exactly one server: the Cloudflare gateway. Everything
the console can do — reading plan state, minting a capability, presenting it — travels
over that one connection.

Plain TLS gives confidentiality and server authentication. It gives the client no
identity at all, and it gives nothing that would stop a proxy or a mis-pointed DNS from
sitting in the middle.

That matters more in this design than it would in the original proposal, for a specific
reason. In the original proposal, the thing travelling to the gateway was a **GitHub
credential** — the crown jewel — so a proxy was catastrophic. In the recommended
architecture the device holds no repository credential, but the thing travelling to the
gateway is now the **capability itself**: the artefact that carries the operator's
decision, signed by the operator's device key, naming one branch and one head SHA.

A capability is scoped and expires in five minutes — but it is still a signed statement
of human intent, and a MITM that captures it can replay it against the gateway within
that window (the nonce is single-use, so a *replay* is caught, but a *modification* of
an in-flight capability is not — the signature covers it, so the gateway would reject a
modified one, but the capture is still a disclosure of the operator's intent and of the
signed scope).

Two more factors push in the same direction:

- The **operator credential** travels on this connection. It is gateway-scoped rather
  than repository-scoped, but it is still a bearer credential.
- The **device enrolment** flow must prove that the key being enrolled is
  hardware-backed and belongs to a genuine device — otherwise "enrolled" means only
  "someone with network access asked".

---

## Decision

**The channel to the gateway is mutually authenticated, not merely TLS.**

Three components, all required:

### 1. Client certificate (mTLS)

The console presents a client certificate to the gateway, and the gateway requires one.
The client certificate is generated from, or bound to, the device's hardware-backed key
material, and is issued by the enrolment authority as part of device enrolment.

The private key for this certificate is non-exportable and lives in the Keystore.
Because the same key material backs the capability signature, **one compromise breaks
both** — which is a feature: the attack surface is a single key, and it is revocable in
one place.

The client certificate is also what makes the transport mutually authenticated: the
gateway can refuse a request from a device it has not enrolled, and the console can
refuse to talk to a gateway that does not present the expected certificate.

### 2. Certificate pinning

The gateway's host is pinned, with a pin set that includes a backup pin. A pinned host
means a proxy needs either a compromised CA that the pin set does not cover, or the
private key behind a pin.

Pinning is configured in two places, and both must be set:

| Where | What |
|---|---|
| `res/xml/network_security_config.xml` | The platform-level pin set for the gateway host. Cleartext is refused outright. |
| `core/security/CertificatePinning.kt` → `core/network/NetworkModule.kt` | The OkHttp `CertificatePinner` on the Ktor client, so the pin is enforced on the engine the app actually uses. |

> The scaffold ships with an **empty** pin set and a placeholder gateway host
> (`gateway.invalid`, an RFC 2606 reserved name). A scaffold must not fail a build over
> a missing deployment secret, and a pin set that is wrong is worse than no pin set.
> Both are deployment inputs.

### 3. Device attestation

The console presents an attestation statement with the enrolment request and with
gateway requests that carry a capability:

- **Key attestation** — the Android Keystore attestation extension, producing a
  certificate chain that binds the public key to the device's hardware.
- **Play Integrity** — the app-integrity verdict, for device and app legitimacy.

The gateway verifies the attestation before it issues a `kid` and before it honours a
capability from an enrolled device. An attestation failure is a refusal, not a warning.

---

## Consequences

### Positive

- A proxy or a mis-pointed DNS cannot become a credential-capture point: it has no
  client certificate and cannot produce a valid attestation.
- The gateway can attribute every privileged action to an enrolled device, not merely
  to a token.
- Enrolment becomes meaningful — "this device holds a hardware-backed key" is a claim
  the gateway can verify rather than take on trust.
- The transport posture is consistent with the capability model: the artefact and the
  channel that carries it are both device-bound.

### Negative

- **Certificate lifecycle is real work.** Client certificates need issuance, expiry,
  renewal and revocation. A certificate that outlives its device is a liability.
- **Attestation adds a dependency on Google Play services**, and Play Integrity is not
  available on every device — including some enterprise-managed ones. The console must
  degrade honestly: if attestation is unavailable, say so, and let the operator decide
  whether to enrol. It must not silently skip the check and report success.
- **Debugging is harder.** A mis-pinned certificate fails in ways that look like
  network errors. The pin set and the certificate rotation need to be documented
  before this reaches a second device.
- The mTLS socket factory must be installed on the OkHttp engine that Ktor uses, which
  means the transport configuration lives in two places (the platform config and the
  client).

### Neutral

- The gateway must also expose its own certificate to a client that pins it. A pinned
  client cannot follow a redirect to an unpinned host, which is the point, but it does
  mean the deployment's hostnames are fixed once pins are cut.

---

## Alternatives considered

| Alternative | Why not |
|---|---|
| **TLS only** | The client is anonymous. A proxy can capture the capability and the operator credential. Insufficient given that the capability *is* the operator's decision. |
| **Bearer token only, no client certificate** | A bearer token is replayable by whoever holds it. The capability model is specifically about making authority non-transferable and non-replayable; the transport should not undo that. |
| **Pinning alone** | Prevents a transparent proxy but does not authenticate the client, and does not support enrolment attestation. |
| **Attestation alone, no client certificate** | Attestation proves app and device integrity but is a request/response assertion, not a channel binding. It does not authenticate the transport. |
| **Long-lived client certificate with no rotation** | A permanently valid device credential is the exact shape of problem ADR 0002 removed elsewhere. Certificates must be short-lived and revocable. |
| **Move enforcement onto the device to avoid the network hop** | The whole architecture puts enforcement in one place (`CTR-1:1`). Duplicating it on the client would create two enforcement points that can disagree. |

---

## Enforcement

| Control | Where |
|---|---|
| Cleartext refused | `res/xml/network_security_config.xml`, `android:usesCleartextTraffic="false"` |
| Pin set | `res/xml/network_security_config.xml` + OkHttp `CertificatePinner` in `core/network/NetworkModule.kt` |
| Client certificate | `core/network/MtlsConfig.kt`, installed on the OkHttp engine |
| Attestation | `core/security/DeviceAttestation.kt`, verified by the gateway |
| Platform-level posture | `core/security/CertificatePinning.kt`, surfaced by `SecurityPostureBadge` |

The console **reports** its transport posture honestly, in the same way it reports
hardware backing: if pinning is not configured, or attestation is unavailable, the
badge says so.

---

## Revisit conditions

This decision should be reopened if:

- The operator decides the gateway's host is reached only through a private network or
  an mTLS-terminating proxy, which would change where the client certificate lives.
- Device attestation proves unavailable on the operator's actual devices, in which case
  the *fallback* posture must be decided explicitly rather than left implicit.
- The gateway is deployed behind a Cloudflare Access policy, which would add a fourth
  authentication factor to the channel and may change the certificate design.

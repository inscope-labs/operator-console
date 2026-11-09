# Certificate Pinning and Pin Rotation Procedure

| | |
|---|---|
| **Status** | Active |
| **Stage** | Stage 2.3 (`T2-302`, `T2-304`) |
| **Decisions** | ADR 0003, ADR 0007, ADR 0010, BD-2.1, BD-2.2 |
| **Applies to** | Android Operator Console (`app/**`) |

---

## 1. Context and Architectural Model

The Operator Console communicates with Gateway A hosted on Cloudflare Workers (`workers.dev`). Per **ADR 0007**, transport security uses HTTPS with **certificate pinning**, an **operator bearer credential**, and a **device-signed `ctxop-v1` capability**.

### What an SPKI Pin Is

A certificate pin is the cryptographic fingerprint of a server's public key (the Subject Public Key Info, or SPKI). Specifically, it is the Base64-encoded SHA-256 digest of the DER-encoded `SubjectPublicKeyInfo` structure:
$$\text{Pin} = \text{Base64}\Big(\text{SHA-256}\big(\text{SPKI}_{\text{DER}}\big)\Big)$$
Standard wire notation prefixes this with `sha256/`:
```text
sha256/<base64-encoded-digest>
```

### Why Pin Intermediate and Root CAs (BD-2.1)

On Cloudflare Workers (`*.workers.dev`), Cloudflare manages TLS termination and automatically provisions certificates via Let's Encrypt, Google Trust Services, or DigiCert. Because Cloudflare may rotate leaf certificates at any time without notice:
- **Pinning the leaf certificate is strictly prohibited** (it will inevitably lock all installed console instances out when Cloudflare rotates the certificate).
- **Pinning intermediate and root CAs** provides the required protection: any certificate issued by an approved CA chain is accepted, while rogue certificates issued by any of the ~150 other public CAs in the Android system trust store are refused.

Per **BD-2.1**, the console pins at least two distinct CA chains:
1. **Primary Chain**: Google Trust Services (GTS)
2. **Backup Chain**: DigiCert Global Root & Let's Encrypt ISRG

---

## 2. Active Pin Set and Configuration

The pin set is configured in `com.inscopelabs.buildengine.core.security.CertificatePinning` and enforced via OkHttp's `CertificatePinner` and an interceptor on the transport engine.

### Current Pins for `*.workers.dev` and `qnuralink.workers.dev`

| Chain | Certificate | Role | SPKI SHA-256 Pin |
|---|---|---|---|
| **Google Trust Services** | GTS Root R1 | Primary Root | `sha256/hxqRlPTuQZQXLjt3o0+/JZaMcj79nlSx4vdwNY+wizg=` |
| **Google Trust Services** | GTS CA 1P5 / 1O1 | Primary Intermediate | `sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hE2gw9ahYRbxI=` |
| **DigiCert** | DigiCert Global Root G2 | Backup Root | `sha256/i7WTqTvh0OioIruIfFR4kRnetJ3GMTVI2fQvfEQPpZ8=` |
| **Let's Encrypt** | ISRG Root X1 | Backup Root | `sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=` |

- **Expiration Date**: `2028-01-01T00:00:00Z`
- **Lifetime Compliance (BD-2.2)**: > 400 days remaining from November 2026 baseline (exceeds the 180-day release threshold).

---

## 3. Security Rules and Fail-Closed Posture

### BD-2.2 Expiration Rule
1. **Release Build Refusal**: Any release build is refused if the pin set expiry is earlier than `build_date + 180 days`.
2. **CI / Operational Warning**: An alert is raised when remaining pin set lifetime drops to $\le 60$ days.
3. **Runtime Fail-Closed**: When `clock.now() >= expiration`, the client throws `PinSetExpiredException`. The connection is refused immediately before and during TLS negotiation.

### Empty Pins Refusal
In release builds (`!BuildConfig.DEBUG`), connecting to an unpinned remote host immediately throws `PinSetEmptyException`.
Local loopback hosts (`10.0.2.2`, `localhost`, `127.0.0.1`) are permitted without pins only in debug builds.

### Dual-Chain Match Requirement
To allow a connection, at least one certificate in the server's presented X.509 certificate chain must match either the primary or backup pins. If neither matches, `CertificatePinMismatchException` is thrown.

---

## 4. Operator Pin Capture Procedure (`T2-301`)

To capture or verify the SPKI pin of a live gateway host (e.g. deployed staging or production Worker):

### Step 1: Connect and Download Certificate Chain
```bash
HOST="staging.qnuralink.workers.dev"

# Download the full certificate chain presented by the host
openssl s_client -servername "${HOST}" -connect "${HOST}:443" -showcerts </dev/null 2>/dev/null > /tmp/chain.pem
```

### Step 2: Split and Compute SPKI Fingerprints
Split `/tmp/chain.pem` into individual certificate blocks (`cert-01.pem`, `cert-02.pem`, etc.) and compute the SPKI SHA-256 pin for each:

```bash
# Compute pin for a single certificate file:
openssl x509 -in cert.pem -pubkey -noout | \
  openssl pkey -pubin -outform der | \
  openssl dgst -sha256 -binary | \
  openssl enc -base64
```

### Automated One-Liner
To inspect all certificates in the server chain in one pass:
```bash
HOST="staging.qnuralink.workers.dev"

openssl s_client -servername "${HOST}" -connect "${HOST}:443" -showcerts </dev/null 2>/dev/null | \
  awk '/BEGIN CERTIFICATE/{n++} {print > "/tmp/cert_" n ".crt"}'

for cert in /tmp/cert_*.crt; do
  echo "Certificate: $cert"
  openssl x509 -in "$cert" -noout -subject -issuer
  echo -n "SPKI Pin: sha256/"
  openssl x509 -in "$cert" -pubkey -noout | \
    openssl pkey -pubin -outform der | \
    openssl dgst -sha256 -binary | \
    openssl enc -base64
  echo "---"
done
```

---

## 5. Pin Rotation Workflows

### Scenario A: Planned Rotation (Ahead of Expiry or CA Migration)

```text
[Current State]
  Primary: CA-1 (GTS)
  Backup:  CA-2 (DigiCert)

[Step 1: 180 Days Before Expiration]
  Add new Backup CA-3 to configuration:
  Primary: CA-1 (GTS)
  Backup:  CA-2 (DigiCert), CA-3 (New CA)

[Step 2: Deploy Updated App / Config]
  Release vNext with updated pin set. Both CA-1 and CA-3 are trusted.

[Step 3: Gateway CA Switchover]
  Cloudflare / gateway TLS certificate switched to CA-3.
  Older app installs on CA-2/CA-1 failover or update; new installs succeed instantly.

[Step 4: Deprecate Retired CA]
  Promote CA-3 to Primary; retire CA-1.
```

### Scenario B: Emergency Rotation (Key Compromise / CA Revocation)
1. If the primary CA key is compromised or untrusted by Google/Mozilla, Cloudflare shifts issuance to the secondary root (e.g. DigiCert or Let's Encrypt).
2. Because DigiCert (`sha256/i7WTqTvh0OioIruIfFR4kRnetJ3GMTVI2fQvfEQPpZ8=`) and Let's Encrypt (`sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=`) are pre-pinned as backup pins in the app, the console **continues to connect without an emergency app release**.

### Scenario C: Dynamic Pin Updates (No App Update)
Where operational configuration allows (e.g. managed configuration or enterprise distribution), `CertificatePinning.setPinConfig(config)` can be called dynamically at runtime to register new pins without requiring an APK reinstallation.

---

## 6. Rotation Rehearsal Checklist

Execute this rehearsal in staging before each major release or at least once every 180 days.

| # | Step | Action | Expected Result | Pass/Fail |
|---|---|---|---|---|
| **1** | **Pin Inspection** | Run capture one-liner against staging gateway. | Output matches at least one pin in `CertificatePinning.DEFAULT_WORKERS_DEV_CONFIG`. | [ ] |
| **2** | **Lifetime Check** | Execute `CertificatePinning.validatePinSet(...)` with `requiredRemainingDays = 180`. | Returns `PinValidationResult.Valid`. Days remaining > 180. | [ ] |
| **3** | **Backup Match Test** | Point console to test TLS server presenting only the backup certificate chain (DigiCert / Let's Encrypt). | Connection succeeds cleanly; no warning or pinning exception. | [ ] |
| **4** | **Mismatch Failure Test** | Point console to test server presenting a certificate from an untrusted CA (e.g. self-signed or unpinned commercial CA). | Request fails immediately with `CertificatePinMismatchException`. Activity log records `UNREACHABLE`. | [ ] |
| **5** | **Expiry Rehearsal** | In test, set simulated clock to `2027-01-02` (past pin set expiration). | Request fails closed with `PinSetExpiredException`. | [ ] |
| **6** | **Release Mode Empty Pin Test** | In test with `isReleaseMode = true`, attempt connection to unpinned host `foreign.example.com`. | Request is refused before socket connection with `PinSetEmptyException`. | [ ] |
| **7** | **Automated Suite Verification** | Run `gradle :app:testDebugUnitTest`. | 100% of JVM tests in `CertificatePinningTest` pass. | [ ] |
| **8** | **Operator Sign-off** | Record test results in stage evidence report. | Documented in `agent-reports/` and signed by operator. | [ ] |

# gateway-repair-ga — Stages 4–5 (GW-4 registry DO, GW-5 attestation)

Timestamp: 2026-11-02T22-58-45Z
Branch: feat/gateway-repair-ga-stage4-5
Base: main @ 0e62c5e (post stages 2–3)

## GW-4 EnrolmentRegistry
- Challenges: create/consume; single-use; TTL 300s; consumed on first attestation attempt path
- Enrolment records: kid k<N> monotonic, alias, publicKey base64url, authTier, status, enrolledAt, leafCertSha256
- rotate: retire old + activate new in one storage batch; alias carried from old when omitted
- Internal routes only: /challenge/create, /challenge/consume, /enroll, /rotate, /lookup/:kid, /active
- Never stores full attestation chain
- Exported from index.ts; wrangler migration v2-enrolment-registry

## GW-5 Attestation
- src/attestation/der.ts: DER reader/encoder with high-tag-number (>30) context tags
- src/attestation/verify.ts: chain length, validity, chain signatures (ECDSA/RSA via WebCrypto),
  strict root SHA-256 allow-list, KeyDescription OID 1.3.6.1.4.1.11129.2.1.17,
  challenge match, security level (strict: TEE/StrongBox), leaf key match,
  tier derivation (tag 503 → in_app_confirm), ALLOWED_AUTH_TIERS enforcement
- tests/helpers/x509.ts synthetic KeyDescription builders
- Unit tests: high-tag 503, KeyDescription parse, short-chain fail-closed, toBase64Url

## Unverified
- Full end-to-end signed X.509 chain tests (minimal synthetic chain signature path is partial;
  production Android chains should be validated in device integration tests)
- tsc / vitest not run in-agent (no npm install)

## Stop-and-ask
- Whether to invest in fully self-signed multi-cert X.509 generation for richer unit coverage next
- Stage 6 (public enroll routes + device key registry) not started

## Files
- gateway/src/durable-objects/enrolment-registry.ts
- gateway/src/attestation/der.ts, verify.ts
- gateway/src/index.ts, wrangler.toml.template
- gateway/tests/helpers/x509.ts
- gateway/tests/unit/attestation/verify.test.ts
- gateway/tests/unit/durable-objects/enrolment-registry.test.ts
- agent-reports/2026-11-02T22-58-45Z-gateway-repair-ga.md

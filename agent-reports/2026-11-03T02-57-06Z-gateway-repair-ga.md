# gateway-repair-ga — Stage 5 remediation

Timestamp: 2026-11-03T02-57-06Z
Branch: feat/gateway-stage5-remediation
Base: main @ 31b6f4d

## R1 Typecheck
- tests/helpers/x509.ts: encodeUtc used in validity encoding; exportSpki casts exportKey result to ArrayBuffer
- tsc/vitest: **unverified in-agent** (no npm install per rules)

## R2 Verifier correctness
- Parent key type/curve from parent SPKI AlgorithmIdentifier + named curve OID (P-256 / P-384)
- Hash/algorithm from child signatureAlgorithm OID; RSA import uses matching hash; unknown OIDs rejected
- ECDSA DER signature converted to fixed-width r||s via ecdsaDerToRaw
- Issuer basicConstraints CA:TRUE required; leaf must not be CA; keyCertSign checked when keyUsage present; pathLen enforced; child issuer TLV == parent subject TLV
- Tier only from KeyDescription softwareEnforced / teeEnforced lists: tag 503 → in_app_confirm; hardware_confirm requires tag 504 in teeEnforced
- Fail closed on any exception

## R3 Tests
Attestation (signed chains via tests/helpers/x509.ts):
- valid strict + lenient
- wrong challenge
- single self-signed
- leaf/key mismatch
- tier mismatch
- untrusted root strict
- AUTH_TIER_NOT_ALLOWED
- non-CA issuer (forged path)
- EC leaf under RSA parent
- P-384
- hardware_confirm without 504
- expired / not-yet-valid
- leaf is CA

Registry (in-memory storage mock):
- challenge single use
- TTL 300s
- consume on first attempt
- monotonic kids
- rotate retires/activates
- failed rotate leaves old active

Challenge replay/expired at registry layer; verifier checks challenge bytes match.

## Lenient × production
Production refusal of lenient mode remains in parseGatewayConfig (GW-2). Verifier still accepts mode=lenient when invoked directly (unit tests).

## Open questions
1. PathLen interpretation (CAs between issuer and leaf) — confirm against Android chain depths in device tests.
2. keyUsage bit index for keyCertSign — verified against common encodings; confirm with real OEM intermediates.
3. Full Android factory root fingerprints remain operator-supplied via ATTESTATION_ROOT_SHA256.

## Files
- gateway/src/attestation/verify.ts
- gateway/tests/helpers/x509.ts
- gateway/tests/unit/attestation/verify.test.ts
- gateway/tests/unit/durable-objects/enrolment-registry.test.ts
- agent-reports/2026-11-03T02-57-06Z-gateway-repair-ga.md

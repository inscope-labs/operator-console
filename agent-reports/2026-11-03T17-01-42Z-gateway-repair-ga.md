# gateway-repair-ga — Stage 7 (GW-6 device key registry)

Timestamp: 2026-11-03T17-01-42Z
Branch: feat/gateway-stage7-device-keys
Base: main @ 2f31ec4 (PRs 19+20 merged)

## Implemented
- auth/device.ts: resolveDeviceKey / createResolveKey
  - SIGNING_KEYS JSON → HMAC (utf8/base64url secret) or ECDSA publicKey
  - EnrolmentRegistry lookup: active only; retired → null (UNKNOWN_KID)
  - Algorithm is property of the kid
- routes/tasks.ts: publish resolveKey = createResolveKey(env)
- capability/verify.ts: accept DER or IEEE P1363 ECDSA signatures (ecdsaDerToRaw)

## Tests
- unit/auth/device.test.ts: HMAC lab kid, registry active ECDSA, retired refused,
  end-to-end verifyCapability with registry-resolved key

## Unverified
- tsc / vitest in-agent
- Loading docs/fixtures/vectors in gateway tests (repo-root path; existing Android tests own them — not duplicated)

## Out of scope
GW-7 conformance suites (stage 8), halt/sync routes, transaction layer

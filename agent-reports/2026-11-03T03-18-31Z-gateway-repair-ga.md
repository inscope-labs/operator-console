# gateway-repair-ga — Stage 6 (GW-3 enrollment + branch head routes)

Timestamp: 2026-11-03T03-18-31Z
Branch: feat/gateway-stage6-routes (from stage5 remediation tip)

## Routes
- POST /enroll/challenge — operator bearer; body {}; returns { challenge, expiresAt }; stores in EnrolmentRegistry
- POST /enroll — alias, publicKey, attestationChain, challenge, authTier; consume challenge; verifyAttestation; enroll; response includes optional authTier
- POST /enroll/rotate — oldKid, newPublicKey, attestationChain, challenge, authTier; alias carried by registry; response authTier + rotatedAt
- GET /branches/:name/head — operator bearer; branch name validation; adapter getRef; { branch, headSha }
- GET /health — contract 3.1 shape (status, environment, version, timestamp); config problems still reported when degraded

## Client
- enrolment/registry-client.ts — internal DO fetch helper

## Tests
- Route registration + challenge happy path with mocked DO
- Branch path rejection for traversal-ish names
- Full enroll with real attestation chain remains integration-level (uses live DO + verify)

## Unverified
- tsc / vitest in-agent
- End-to-end enroll against synthetic chain in unit tests (challenge mock only)

## Stop-and-ask
- Contract error body uses `code` while gateway catalogue uses `error` — kept existing gateway shape for consistency with prior stages
- Whether GET /branches should allow `main` (yes — BRANCH_RE allows it)

## Out of scope
GW-6 device resolveKey wiring, conformance suites, halt/sync/doctor

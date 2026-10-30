# Agent Report: Stage 1.2 — Decision Records

- **Stage**: Phase 1, Stage 1.2
- **Timestamp (UTC)**: 2026-10-30T19:09:00Z
- **Slug**: stage-1-2-decision-records

---

## Tasks Completed

- **T1-201 (ADR 0004: Lab Mode)**: Created `docs/adr/0004-lab-mode.md` specifying:
  - Purpose of the Lab test suite.
  - Strict environment guard calling `GET /health` (`environment == "test"` required; immediate refusal on mismatch).
  - Use of `HmacCapabilitySigner` with no biometric prompts exclusively in Lab Mode.
  - Mandatory persistent UI warning banner: `LAB — not an operator approval`.
  - In-memory execution without durable on-device audit/test storage, exportable via `ACTION_SEND`.
  - Settings toggle for Lab mode that defaults to disabled and is never persisted across sessions.
- **T1-202 (ADR 0005: Signature Encoding & Scoping)**: Created `docs/adr/0005-signature-encoding.md` resolving:
  - **D-T1**: Algorithmic independence of the 10-field wire format; verification algorithm determined by `kid` in gateway registry (ECDSA P-256 for real operator approvals, HMAC-SHA256 for Lab).
  - **D-T1b**: Requirement for raw 64-byte IEEE P1363 (`r || s`) base64url-nopad signature encoding on the wire, converted from Android's ASN.1 DER Keystore output.
  - **D-T2**: Strict bifurcation between task-scoped operations (`PUBLISH`, `UPLOAD`, etc. requiring `^task-`) and engine-scoped operations (`HALT`, `RESUME`, etc. scoped to configured engine branch).
- **T1-203 (ADR 0006: mTLS & Play Integrity Deferred)**: Created `docs/adr/0006-mtls-deferred.md` detailing:
  - **D-T3**: Deferral of client-cert mTLS due to `setUserAuthenticationRequired(true)` handshake blocking and `workers.dev` endpoint limitations; transport protected by TLS 1.3, Bearer operator token, and signed capability.
  - **D-T4**: Enrolment relies on Android Keystore key attestation certificate chain; Play Integrity (`requestAttestation`) deferred and stubbed behind an interface.
  - Checked `README.md` "Deliberately absent" section; no wording updates required as neither deferred component was listed there.

---

## Files Changed

- `docs/adr/0004-lab-mode.md` (created)
- `docs/adr/0005-signature-encoding.md` (created)
- `docs/adr/0006-mtls-deferred.md` (created)

---

## Tests Added

- None (Phase 1 Stage 1.2 is architectural decision documentation).

---

## Skipped Items and Rationale

- None skipped.

---

## Stop and Ask Items

- Ready to proceed to **Phase 1, Stage 1.3 — Vectors** (`docs/fixtures/vectors/README.md`) or Phase 2.

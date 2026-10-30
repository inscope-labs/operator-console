# Agent Report: Stage 2.2 — Codec and Model

- **Stage**: Phase 2, Stage 2.2
- **Timestamp (UTC)**: 2026-10-30T22:16:00Z
- **Slug**: stage-2-2-codec-and-model

---

## Tasks Completed

- **T2-201 (`capability/model/*`)**:
  - `OperatorCapability`: Implemented strict validation in constructor:
    - `kid`: Enforced `^[A-Za-z0-9_]{1,16}$` selector constraint.
    - `exp`: Enforced `exp > 0`.
    - `order`, `plan`, `task`: Enforced non-blank, non-empty, and newline/CR-free strings.
    - `branch`: Enforced branch constraint per `OperationType` according to D-T2 and ADR 0005 (task-scoped operations require `^task-[A-Za-z0-9._-]+$`; engine-scoped operations require valid branch names `^[A-Za-z0-9._/-]+$`).
    - `head`: Enforced exact 40-character lowercase hexadecimal git SHA (`^[0-9a-f]{40}$`).
    - `nonce`: Enforced non-blank charset validation (`^[A-Za-z0-9._~+/-]+$`) without newlines/CR.
  - `OperationType`: Added `isTaskScoped` property distinguishing task-scoped ops (`PUBLISH`, `UPLOAD`, `RECONCILE`, `DECIDE`, `ORDER`, `SET_IN_PROGRESS`, `SET_DONE`) from engine-scoped ops (`HALT`, `RESUME`, `SYNC`, `DOCTOR`, `NEW_PLAN`).
  - `CapabilityScope`: Enforced consistent branch, head SHA, and nonce invariants.
  - Removed all `SCAFFOLD PLACEHOLDER` markers and `TODO`s.
- **T2-202 (`capability/codec/OperatorCapabilityCodec.kt`)**:
  - `encode(capability: OperatorCapability)`: Implemented strict 10-line LF-separated serialization (`ctxop-v1`, `kid`, `exp`, `order`, `plan`, `task`, `op`, `branch`, `head`, `nonce`) with no trailing LF/CR.
  - `encode(capability: OperatorCapability, signature: ByteArray)`: Implemented wire serialization appending base64url-nopad signature on the 11th line.
  - `decode(message: String)`: Implemented strict, fail-closed deserialization rejecting:
    - Extra fields (>10 lines).
    - Missing fields (<10 lines).
    - Carriage return characters (`\r`).
    - Trailing LF and leading LF.
    - Unrecognized domain separators (only `ctxop-v1` permitted).
    - Unrecognized operation literals.
    - Invalid field values (malformed kid, negative exp, non-task branch for task ops, non-40-hex head SHA, malformed nonces).
  - Validation helpers: `isValidBranch`, `isValidHead`, `isValidKid`.
  - Removed all `SCAFFOLD PLACEHOLDER` markers and `TODO`s.

---

## Files Changed

- `app/src/main/kotlin/com/inscopelabs/buildengine/capability/model/OperationType.kt` (updated)
- `app/src/main/kotlin/com/inscopelabs/buildengine/capability/model/OperatorCapability.kt` (updated)
- `app/src/main/kotlin/com/inscopelabs/buildengine/capability/model/CapabilityScope.kt` (updated)
- `app/src/main/kotlin/com/inscopelabs/buildengine/capability/codec/OperatorCapabilityCodec.kt` (updated)
- `app/src/main/kotlin/com/inscopelabs/buildengine/core/crypto/SecureRandomSource.kt` (added `NONCE_BYTES` constant for `DeviceNonceSourceTest`)
- `app/src/main/kotlin/com/inscopelabs/buildengine/capability/NonceSource.kt` (retained `NonceSource` interface)

---

## Tests Added / Updated

- `app/src/test/kotlin/com/inscopelabs/buildengine/capability/codec/OperatorCapabilityCodecTest.kt` (updated with 18 comprehensive tests covering roundtrips, field formats, signature serialization, and strict rejections)

**All 48 JVM unit tests in the project pass with 0 failures and 0 errors.**

---

## Skipped Items and Rationale

- None skipped.

---

## Stop and Ask Items

- Ready to proceed to **Phase 2, Stage 2.3 — Signers and minter** (`HmacCapabilitySigner`, `SignedCapability`, `CapabilityMinter`, ECDSA P-256 DER ↔ IEEE P1363 converters, `DeviceKeyCapabilitySigner`, and test suites `CapabilitySignerTest` and `CapabilityMinterTest`).

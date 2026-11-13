# ADR 0013 — Operation Idempotency Key and Lookup

| | |
|---|---|
| **Status** | Accepted (pending merge of this PR) |
| **Date** | 2026-11-13 |
| **Decision IDs** | BD-1.7 (and the key-origin question found while preparing Stage 3.2) |
| **Related** | ADR 0009 (journal), ADR 0007 (capability single use), `docs/gateway-contract.md` §1.6, §3.19, §4.3 |

---

## Context
ADR 0009 and BD-3.2 require the journal to store **no capability, signature or nonce**, and recovery after a lost
response depends on `GET /operations/{id}` (BD-1.7). Contract v1 did not say what `{id}` is. Its example showed the
same value as the capability nonce, which the journal must not hold, and a fresh capability (and so a fresh nonce) is
minted on every tap, which would defeat T3-204 (idempotent repeated taps).

## Decision
1. The lookup key is a **client-generated idempotency key**, separate from the nonce: 128 random bits as 32 lowercase hex
   characters, journaled by the console before the send and reused for repeated taps on the same intent.
2. It travels in a new body field `idempotencyKey` on every write request. It is outside the signed capability, so the
   capability format and conformance vectors do not change.
3. The gateway writes the record (`pending`) before processing and updates it with every outcome, refusals included.
   Same key and same request fingerprint returns the recorded response without re-executing; same key and a different
   fingerprint is `IDEMPOTENCY_KEY_REUSED` (409).
4. `GET /operations/{id}` returns the recorded status. `failed` and `refused` carry an error `code`. Mapping to console
   outcomes is in contract §4.3. `404 OPERATION_NOT_FOUND` maps to `RecoveryRequired`, never to an automatic resend.
5. Records are scoped to the creating operator credential and kept at least 7 days.

## Consequences
- Contract revision 1.1 and the request schemas change (new required field). Gateway A must implement the field, the
  write-ahead record and the lookup route; its plan must add that work (gateway agent). Until then, console Stage 3.2
  is built and tested against fakes only, and Gate 3 and Stage 7.3 cannot pass against a real gateway.
- Stage 3.2 additionally needs the client-side lookup (DTO, endpoint constant, API method), which its **Files** line must
  authorize.
- Rejected: using the nonce or a hash of it as the key (breaks BD-3.2 and T3-204); a gateway-issued key (a lost response
  never delivers it).

## Operator authorization
Option approved by the operator (John Wendell) on 2026-11-13 ("approve default"). Claude opens this docs-only pull
request and does not merge. The session token is used for this assignment only; no secret is stored and nothing on
Cloudflare is touched.

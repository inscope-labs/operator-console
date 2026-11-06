# ADR 0002 — Fail-closed stub refusal (`NOT_IMPLEMENTED`)

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-11-06 |
| **Decision IDs** | GATEWAY-A-COMPLETION-PLAN Stage 1.1 (T1-103), R-02, R-03, R-04, R-06 |
| **Related** | `docs/gateway-contract.md` §3.8, §4; `gateway/docs/error-catalogue.md`; ADR 0001 (signing model) |

---

## Context

`POST /tasks/{id}/publish` is specified in the contract (§3.8), but the publish
transaction does not exist in this build. The route nevertheless ran the whole
capability gate and guard set and then answered:

```
HTTP 200
{ "ok": true, "phase": 1, "message": "capability + nonce + core guards passed — publish transaction not yet implemented" }
```

A client that trusted the status code — as the console's HTTP client does — would
report a publication that never happened. That is a false success report for a
consequential operation, which R-03 and RELEASE-PLAN G-08 both forbid. The
contradiction between a `200` and the word "not yet implemented" is the defect.

## Decision

1. **A stub refuses; it never acknowledges.** An operation that is in the
   contract but not implemented answers **HTTP 501** with the catalogue code
   `NOT_IMPLEMENTED`. It never answers 2xx and never claims that the operation
   was accepted, performed or queued.
2. **The refusal is fail-closed and side-effect free.** The stub refuses *before*
   it interprets the request:
   - the request body is not parsed, so no payload is interpreted;
   - the capability is not verified and its nonce is **not** consumed, so an
     operator does not spend a single-use, biometric-confirmed capability on a
     call that cannot run;
   - no GitHub adapter is constructed and no repository state is read or written.
   The operator-token and configuration gates still run — the router rejects both
   before the handler is reached — so the stub is not an open surface.
3. **The code is part of the public catalogue.** `NOT_IMPLEMENTED` is added to
   `src/errors/catalogue.ts` (HTTP 501) and to `docs/error-catalogue.md`. Adding
   a code is backwards-compatible; changing the meaning of one requires an ADR.
4. **The message carries no success wording and no caller-controlled input.**
   The refusal text is stable so that clients and tests can match on it.

## Consequences

- The console receives a non-2xx response, records the call as `REFUSED` and
  maps it to a failure state; it can no longer display a publication that did
  not happen.
- The publish capability the operator minted remains unspent and usable once the
  transaction lands (Stage 3.2), so no re-confirmation is forced by this stub.
- `POST /tasks/{id}/publish` is deliberately **not** part of the contract's
  success surface yet. Stage 3.2 replaces this stub with the real transaction
  (capability gate → ordered guards → GitHub mutation → audit record) and
  removes the 501. The catalogue entry stays: it is the code other unimplemented
  operations will use.
- The other unimplemented surfaces (`sync`, `doctor`, `admin`, `decisions`) are
  not yet routed at all, so they currently answer `502 NETWORK` from the router's
  "no route" fallthrough. Converting them to `NOT_IMPLEMENTED` belongs to
  Stages 3.6, 4.2 and 4.3, which own those routes.

## Alternatives rejected

- **Keep `200` with `accepted: false`.** Rejected: the status code would still
  say the operation was taken, and clients keying on the status would keep
  reporting success. R-03 requires distinct terminal states, not a success
  status with a negative flag.
- **Verify the capability first, then refuse.** Rejected: it spends the
  capability's nonce and adds crypto work to a route that cannot act. The
  refusal does not depend on the capability being valid.
- **Return `400`/`503`.** Rejected: those codes describe a malformed request or
  an unavailable dependency. The request may be perfectly valid — the operation
  simply does not exist in this build, which is what 501 means.

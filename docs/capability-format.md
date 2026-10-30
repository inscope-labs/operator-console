# The `ctxop-v1` operator capability

The signed, scoped, expiring artefact that carries one operator decision from the console
to the gateway.

The capability replaces the session model. Under a session model, a grant made once
authorises everything for its duration, and the console is the party that would have to
be trusted not to widen it. Under the capability model, the grant is a checkable
artefact:

> *the operator approved this publish, of this branch, at this SHA, within the last five
> minutes, once.*

Every one of those five qualifiers is checkable without trusting the console.

---

## 1. Shape

Ten LF-separated fields, signed, with the signature appended.

```
ctxop-v1 \n kid \n exp \n order \n plan \n task \n op \n branch \n head \n nonce
       │      │      │       │       │      │     │       │        │      └─ one-time value
       │      │      │       │       │      │     │       │        └──────── expected branch head SHA (40 hex)
       │      │      │       │       │      │     │       └───────────────── the minted branch: task-<id>
       │      │      │       │       │      │     └───────────────────────── operation: publish | upload | reconcile | …
       │      │      │       │       │      └──────────────────────────────── task id
       │      │      │       │       └──────────────────────────────────────── plan id
       │      │      │       └────────────────────────────────────────────────── work order id
       │      │      └──────────────────────────────────────────────────────────── expiry (unix seconds)
       │      └─────────────────────────────────────────────────────────────────── key id (selector, not a secret)
       └────────────────────────────────────────────────────────────────────────── domain separator
```

The signature:

```
sig = base64url_nopad( HMAC-SHA256( secret, message_utf8 ) )
```

The construction is the same one the build engine's write gateway already specifies as
`ctxwrite-v1`; the operator case adds the two fields it needs — **operation** and
**target branch**.

---

## 2. Field rules

| Field | Constraint | Rationale |
|---|---|---|
| `ctxop-v1` | Exact literal. | Domain separator. A capability from one protocol must never be replayable under another. |
| `kid` | `^[A-Za-z0-9_]{1,16}$`; **selector only, never a secret** | Enables rotation: mint under `k2` while `k1` remains valid until retired. A gateway must reject an unknown `kid` *before doing any other work*. |
| `exp` | Unix seconds; inclusive boundary; the gateway enforces a maximum TTL | Stops a stolen signing key from minting a year-long grant. Suggested operator TTL: **5 minutes** — long enough for a confirmation gesture, short enough that a captured capability is near-useless. |
| `order` | Work order id | Binds the capability to the order it was minted for. |
| `plan` | Plan id | The plan of record the operation applies to. |
| `task` | Task id | The task the operation applies to. |
| `op` | Closed enumeration; unknown values refused | Authorisation must never be inferred from an unrecognised request. |
| `branch` | Must match `^task-[A-Za-z0-9._-]+$`; must equal the branch named in the work order | The field that carries the branch-scope constraint. A capability for `task-A` must not authorise a write to `task-B`, and never to `main`. |
| `head` | 40 hex characters; the expected branch head | Makes the operation race-safe. If the branch moved after the capability was minted, the write fails and must be re-minted. |
| `nonce` | Random, single-use, stored by the gateway until `exp` | Replay protection. The gateway must refuse a nonce it has already seen, and must store it before performing the operation. |

> **`op`, `branch`, `head` and `nonce` are all bound into the signature.** A signature
> that cannot be stretched after minting is what makes scope real. The scope *is* the
> constraint, so the scope is what the signature covers.

---

## 3. The operation enumeration

`op` is a closed set, defined in `capability/model/OperationType.kt`:

```
PUBLISH  UPLOAD  RECONCILE  DECIDE  HALT  RESUME
SYNC     DOCTOR  ORDER     SET_IN_PROGRESS  SET_DONE  NEW_PLAN
```

Two properties matter:

- **Unknown values are refused.** A request carrying an operation the gateway does not
  recognise is an error, not a default.
- **Each operation carries its own scope.** `halt` affects every plan in the repository
  and therefore cannot be authorised by a task-scoped capability. It needs an explicitly,
  separately-scoped capability. The console must not offer a `halt` that reuses the
  scope of the publish the operator was just looking at.

---

## 4. Worked example

A publish capability for task `10008` in plan `atlas`, minted under key selector `k2`,
expiring five minutes after it was issued:

```
ctxop-v1
k2
1761840000
order-7731
atlas
10008
PUBLISH
task-10008-refactor-parser
9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d
7f3c9b1e5d2a7046
```

…followed by the signature, base64url-encoded without padding, on its own line.

Field by field:

| Field | Value | Why it is there |
|---|---|---|
| `kid` | `k2` | The gateway selects the verification key by selector. `k2` is not a secret and carries no authority on its own. |
| `exp` | `1761840000` | The gateway refuses an expired capability, and refuses one whose TTL exceeds the maximum it enforces. |
| `order` / `plan` / `task` | `order-7731` / `atlas` / `10008` | Bind the capability to the work it was minted for. |
| `op` | `PUBLISH` | The only action this capability can authorise. |
| `branch` | `task-10008-refactor-parser` | Matches `^task-[A-Za-z0-9._-]+$` and equals the branch named in the work order. |
| `head` | `9f2c…a7e9d` | The exact 40-character SHA the operator reviewed. A capability for a different SHA is a different capability. |
| `nonce` | `7f3c9b1e5d2a7046` | Single-use. The gateway stores it until `exp` and refuses a second presentation. |

---

## 5. Encoding rules

| Rule | Detail |
|---|---|
| Field separator | Exactly one LF (`0x0A`). No trailing LF, no CR. |
| Order | Fixed. `kid, exp, order, plan, task, op, branch, head, nonce`. The order is part of the contract — it is why the codec's model classes are kept by the R8 rules in `proguard-rules.pro`. |
| Signature encoding | base64url, **no padding**. |
| Signed bytes | The UTF-8 bytes of the LF-joined message, exactly as transmitted. |
| Unknown fields | Rejected. A message with an extra field is not a valid capability. |

---

## 6. Lifecycle

```
  operator opens the publish screen
            │
            ▼
  console reads the live branch head ──▶ VerifyBranchHeadUseCase
            │                                    │
            │              (head drifted) ───────┤──▶ refuse, ask the operator to re-review
            ▼                                    │
  operator reads the summary:                  ▼
    operation, branch,                 operator confirms
    exact head SHA, TTL ─────────────▶  BiometricPrompt (CryptoObject-bound)
                                                 │
                                                 ▼
                                    CapabilityMinter builds the message,
                                    signs it with the device key
                                                 │
                                                 ▼
                                    console presents the capability
                                                 │
                                                 ▼
                                   gateway: verify signature
                                            │  ├─ unknown kid ──▶ refuse
                                            │  ├─ expired ──────▶ refuse
                                            │  ├─ nonce seen ───▶ refuse
                                            │  ├─ head drifted ─▶ refuse
                                            │  ├─ branch ≠ scope▶▶ refuse
                                            │  └─ scope ≠ op ───▶ refuse
                                            ▼
                                    run G1–G13, write, record the audit entry
```

**No capability exists before the operator decides.** That is the whole point of Model B
— see `adr/0001-capability-signing-direction.md`.

---

## 7. What the gateway must check

In order, and each check before the work it guards:

1. `kid` is known → otherwise refuse immediately, before any other work.
2. The signature verifies under the key named by `kid`.
3. `exp` is in the future and the TTL is within the gateway's maximum.
4. `nonce` has not been seen. **Store it before performing the operation**, not after.
5. `op` is in the closed enumeration.
6. `branch` matches `^task-[A-Za-z0-9._-]+$` and equals the branch in the work order.
7. `head` equals the current head of that branch.
8. The guard set G1–G13 passes.
9. A halt marker is not active.

Then: write, and append one audit record to the private consumer repository. The
signature and the nonce are **not** recorded.

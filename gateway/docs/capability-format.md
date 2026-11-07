# The `ctxop-v1` Capability Format

**Domain Separator:** `ctxop-v1`  
**Max TTL:** 300 seconds (5 minutes)  
**Clock Skew Tolerance:** 10 seconds  

The operator capability is a signed, scoped, single-use credential minted by the Operator Console following an operator confirmation gesture. It proves that the operator explicitly approved a specific operation on a specific branch at a specific git commit SHA.

---

## 1. Wire Framing

The wire message consists of exactly ten LF (`0x0A`) separated fields in fixed order, followed by an LF and the unpadded base64url-encoded signature:

```
ctxop-v1\n<kid>\n<exp>\n<order>\n<plan>\n<task>\n<op>\n<branch>\n<head>\n<nonce>\n<sig>
```

### Constraints:
1. **No CR (`\r`)**: Presence of CR causes immediate rejection (`BAD_ENCODING`).
2. **Exact Field Count**: Must contain exactly 10 message lines before the signature (`BAD_FIELD_COUNT`).
3. **No Trailing LF**: Message must terminate cleanly.
4. **Signature**: Appended as the 11th line; base64url encoded without padding.

---

## 2. Field Definitions

| Index | Field | Constraints | Description |
|---|---|---|---|
| 0 | `version` | Must equal `ctxop-v1` | Protocol domain separator |
| 1 | `kid` | `^[A-Za-z0-9_]{1,16}$` | Key selector identifier in Device Registry |
| 2 | `exp` | Decimal integer epoch seconds | Expiration timestamp; `exp - now <= 300` |
| 3 | `order` | Non-empty string | Work order identifier |
| 4 | `plan` | Non-empty string | Plan identifier (e.g. `atlas`) |
| 5 | `task` | Non-empty string | Task identifier (e.g. `10008`) |
| 6 | `op` | Member of `OperationType` closed set | Operation type (case-insensitive on wire) |
| 7 | `branch` | Scope-dependent | Task-scoped: `^task-[A-Za-z0-9._-]+$`; Engine-scoped: `engine` |
| 8 | `head` | 40 lowercase hex characters | Expected commit SHA of branch tip |
| 9 | `nonce` | 16–64 hex characters | Cryptographically random single-use nonce |

---

## 3. Supported Operation Types

The closed operation set comprises 13 distinct operations:

### Task-Scoped Operations (Must target `^task-[A-Za-z0-9._-]+$`):
- `publish`: Publish verified task branch to upstream staging
- `upload`: Upload task submission archive bundle
- `reconcile`: Merge/reconcile reviewed task branch
- `decide`: Submit operator architectural decision
- `order`: Issue new task work order
- `set_in_progress`: Transition task state to in_progress
- `set_done`: Transition task state to completed
- `set`: General status mutation with compare-and-swap

### Engine / Repository-Scoped Operations (Must target `engine`):
- `halt`: Global emergency stop on engine processing
- `resume`: Resume engine processing from halt state
- `sync`: Synchronize repository branch tracking
- `doctor`: Execute platform and repository diagnostic suite
- `new` / `new_plan`: Initialize a new plan definition

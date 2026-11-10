# Gateway admin surface (Stage 4.3)

All paths require **Operator Bearer** (`OPERATOR_TOKEN`). There is **no**
unauthenticated admin surface.

| Method | Path | Purpose | Audit |
|--------|------|---------|-------|
| `GET` | `/admin/enrolments` | List active device enrolments (`kid`, `alias`, `authTier`, `status`, `enrolledAt`). Public keys are **not** returned. | No (read-only) |
| `POST` | `/admin/revoke` | Retire a device key (BD-1.10). Body: `{ kid, reason?, confirmation }` where `confirmation` must equal `REVOKE <kid>`. | Yes — `auditRef` on success |

Unknown `/admin/*` paths are not registered (router returns not found / no match behaviour).

## Security notes

- Revocation is irreversible for that `kid` (status → `retired`).
- Confirmation string prevents accidental revokes.
- Secrets, PEMs, and raw public keys are omitted from list responses.

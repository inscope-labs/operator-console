# Gateway A JSON Schemas (Stage 1.3 freeze)

Machine-readable request/response shapes for Gateway A endpoints listed in
`docs/gateway-contract.md` and the Stage 1.3 exit criteria.

| Schema file | Covers |
|-------------|--------|
| `error.json` | Error body `{ error, message, request_id }` |
| `health.json` | `GET /health` |
| `plans.json` | `GET /plans`, plan status / validate / render-check |
| `tasks.json` | `GET /tasks/next`, publish / upload / reconcile request & success |
| `branches.json` | `GET /branches/{name}/head` |
| `enrol.json` | enrol challenge, enrol, rotate |
| `halt-resume.json` | `POST /halt`, `POST /resume` |
| `sync-doctor.json` | `POST /sync`, `GET /doctor` |
| `decide.json` | Decide (may remain 501 for 0.6.0) |

Conformance vectors under `docs/fixtures/vectors/` and client DTOs should
reference these schemas. Schemas use draft 2020-12 and allow
`additionalProperties` on success bodies so clients ignore unknown fields
(contract §1.5).

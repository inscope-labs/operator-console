# Gateway Credential Scope & Blast Radius

**Status:** Authoritative (T1-205, BD-0.6)  
**Component:** Operator Gateway A (Cloudflare Worker)  
**Credential Model:** GitHub App Installation (Least Privilege)

---

## 1. Overview & Threat Model Alignment

Under the operator capability architecture (Model B), **the Android Operator Console holds zero repository credentials**. The gateway is the sole entity that interacts with the GitHub API on behalf of the operator. 

To prevent systemic repository compromise, Gateway A never uses personal access tokens (PATs) or broad OAuth bearer tokens in production. It authenticates as a dedicated **GitHub App installation** scoped per environment (`test`, `staging`, `production`) with permissions restricted strictly to the repositories configured in the validated `REPOS` configuration map.

---

## 2. GitHub App Permissions Matrix

The GitHub App requires only the minimum permissions necessary to evaluate git guards and execute branch compare-and-swap mutations:

| Permission Category | Level | Justification |
|---|---|---|
| **Repository Contents** | `Read & Write` | Required to read branch heads, unified diffs, commit ancestry, tree metadata, and write validated merge/publish commits |
| **Pull Requests** | `Read & Write` | Required for `POST /tasks/{id}/reconcile` to query and reconcile PR status |
| **Repository Metadata** | `Read-only` | Required on gateway startup to enforce Guard G1 (verifying that all consumer repositories are private) |
| **Administration** | `No Access` | Explicitly forbidden; gateway cannot modify repository settings, collaborators, or webhooks |
| **Workflows** | `No Access` | Explicitly forbidden; protected by G8 (gateway refuses to touch `.github/workflows/`) |
| **Secrets** | `No Access` | Explicitly forbidden; gateway has no access to GitHub Actions secrets or variables |

---

## 3. Storage & Secret Handling

The GitHub App credentials consist of:
1. `GITHUB_APP_ID`: Numeric App ID (environment variable).
2. `GITHUB_APP_PRIVATE_KEY`: PEM-encoded RSA private key (Cloudflare Worker Secret).
3. `GITHUB_APP_INSTALLATION_ID`: Numeric installation ID corresponding to the specific organization or account installation (Worker Secret).

### Security Invariants:
- **No Secret Leaks**: The private key and short-lived installation access tokens MUST NEVER appear in log events, error response payloads, or audit records (`verify-log-hygiene` CI gate).
- **Ephemeral Tokens**: Installation access tokens are cached in memory for a maximum of 50 minutes (GitHub token TTL is 60 minutes) and renewed automatically by the adapter.
- **Fail-Closed on Unavailability**: If token generation fails, the gateway returns HTTP 503 `TOKEN_UNAVAILABLE`.

---

## 4. Blast Radius & Repository Isolation

1. **Strictly Limited to `REPOS`**:
   The gateway only acts on repositories explicitly enumerated in the validated `REPOS` JSON configuration string. Any request specifying an unknown repository is rejected with `UNKNOWN_REPO` (HTTP 400).
2. **Per-Environment Installations**:
   - `test`: Installed only on the test consumer repository (`task-fx-*`).
   - `staging`: Installed only on staging pilot repositories.
   - `production`: Installed only on approved production repositories.
3. **Guard Boundary (G1–G15)**:
   Even with write access, the gateway cannot push to `main` directly. All mutating updates are constrained to task branches matching `^task-[A-Za-z0-9._-]+$`, require a fresh biometric-confirmed capability (`ctxop-v1`), and pass the entire guard pipeline.

# Architecture mapping

Maps packages under `src/` to sections of the cbridge Cloudflare Gateway specification and the 30 October 2026 design review.

| Package | Spec reference | Notes |
|---------|----------------|-------|
| capability/ | §8 | ctxop-v1, Model B preferred (D-a) |
| adapter/ | §11 | MultiRepoGitHubAdapter |
| guards/ | §10 | G1–G16 |
| routes/ | §9 | Read-only, confirmation-bearing, /admin/* |
| durable-objects/ | §12 | Plan DO, nonce store, enrolment, audit |
| audit/ | §13, §14 | Records + redaction |
| errors/ | §15 | Stable catalogue |

Open decisions D-a … D-g remain unresolved at scaffold time.

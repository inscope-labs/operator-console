# Pre-execution agent onboarding

**Read this before converting any file.** You are being asked to turn one named file into
*pre-execution boilerplate*: a compile-safe, fail-closed placeholder. You **prepare**; you do **not
complete**. The authoritative repository agent (AI Studio) completes each file during its own stage.
Rule numbers `G-nn` are in `RELEASE-PLAN.md` Part A.3.

## Sources of truth, in order
1. `RELEASE-PLAN.md`: the stage's tasks (`T…`) for your file.
2. `docs/gateway-contract.md` and the published schemas: the only authority for endpoints, fields and
   error codes.
3. `docs/PRE-EXECUTION-FILE-MAP.md`: the approved list of files, with stage and tasks. **A file that is
   not in the map is not yours to create.**
4. Existing code, for conventions only.

If these are silent on something you need, **stop and report** (G-07). Do not guess.

## Rules
1. **One file per assignment.** Touch only the path you were given (G-02). Never touch `gateway/**`
   (G-13), tests, resources, build files, or protected paths (`.github/workflows/*.yml`,
   `proguard-rules.pro`, `AndroidManifest.xml`, `app/build.gradle.kts`, `version.properties`,
   `build-logs/**`, `gradle/libs.versions.toml`, `network_security_config.xml`).
2. **Two marker lines, exactly.** In the file's KDoc: `SCAFFOLD PLACEHOLDER — placeholder only,
   implementation pending.` and `TODO(Stage X.Y / T…): what the stage must do`. The completing agent
   removes both; the task is not done while either remains (G-09).
3. **Fail closed** (G-05, G-08). A body is `TODO("SCAFFOLD: …")`. A screen shows "Not implemented" and
   offers no action. Nothing returns success, empty or a default, and nothing reports a sent request
   as done.
4. **No logic and no inventions.** Use only names the plan or the contract gives. A signature you had to
   choose is labelled "The signature below is a proposal" in the KDoc.
5. **Compile with what is on `main` today.** No new dependency, resource or permission. If the file needs
   one (Room, WorkManager, …), **stop**: the dependency PR comes first.
6. **Not wired.** No navigation entry, DI binding, manifest entry or caller.
7. **Never write the text `.mint(`** and never import signers or crypto (`ArchitectureTest`).
8. **Match conventions.** The package equals the directory. States use `data object`. A ViewModel is a
   `@HiltViewModel` exposing `StateFlow<UiState<…>>`. A screen is a `@Composable` taking a `Modifier`.
9. **Delivery.** A branch and a pull request, never a push to `main`; start from current `main`; one writer
   per branch (G-02). The PR lists the file, your assumptions and every proposed signature.
10. **Report.** `agent-reports/<UTC-ISO>-pre-exec-<slug>.md`: file, markers, assumptions, anything you
    stopped on.

## What "done" means for you
The file compiles in CI, carries both markers, and is the only change. It stays **scaffold**: never call
it implemented, tested or safe to use.

## Stop and report if
the file is not in the map; the contract is silent; it needs a new dependency; it would have to touch
another file; or you cannot tell what the stage task asks.

## What happens next
AI Studio treats your file as an **untrusted proposal**. It reads the stage's tasks, implements the file,
removes both markers, adds the tests, and closes the stage only when CI is green on the pushed SHA (G-04).

## How to assign a file
> Convert `<path>` per `docs/PRE-EXECUTION-ONBOARDING.md`. Stage `<X.Y>`, tasks `<T…>`.

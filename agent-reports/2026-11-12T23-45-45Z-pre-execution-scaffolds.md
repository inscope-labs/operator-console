# Pre-execution scaffolds (15 files) and onboarding

- **Author:** Claude, at the operator's request. **Branch:** `scaffold/pre-execution-15-files`.
- **Base:** `main` at `9adbfe2`. **Stage:** none; this is preparation, not a stage.
- **Added:** 15 scaffold Kotlin files (`docs/PRE-EXECUTION-FILE-MAP.md`), `docs/PRE-EXECUTION-ONBOARDING.md`
  `docs/PRE-EXECUTION-FILE-MAP.md`.
- **Not added:** the four Room/WorkManager files (dependencies absent from `gradle/libs.versions.toml`); any
  test, resource, workflow or build change.
- **Every file** carries `SCAFFOLD PLACEHOLDER` and `TODO(...)`, uses only types already on `main`
  (`UiState`, Hilt, Compose), is not wired anywhere, and contains no `.mint(`.
- **Proposed signatures** (to be confirmed by the stage): `ScenarioRunner.run`, `ScenarioCatalogue.all`,
  `ContractCheck.run`.
- **Verified here:** the markers, and the absence of `.mint(`. **Not verified here:** compilation of the Compose
  and Hilt files; CI (`android-ci`) is the check. Do not merge before it is green.

# Device test checklist (PoC smoke run)

The steps an operator runs on an emulator or a physical device against a `test` gateway (see `docs/ENVIRONMENTS.md`).
It is the device half of the PoC evidence: each step ends in something you can see or capture. Tick a box only for a step you
ran. A skipped step stays unticked, with the reason beside it.

**Never** paste the operator bearer token, a private key, a capability or a nonce into a chat, an issue or a log. Enter the
credential in Settings on the device (operator guide §5.2).

## Run record

| Field | Value |
|---|---|
| Date and operator | |
| Build | `main` SHA and the `android-ci` run that built the APK |
| Device | emulator or model, Android version |
| Gateway | `test`, how it was started, its `GET /health` output |
| Confirmation tier shown | `hardware_confirm` or `in_app_confirm` |

## 1. Install and first launch

- [ ] The debug APK installs; the app launches.
- [ ] The shell banner reads **Not configured**, and the dashboard and plan lists stay on **Idle**. No network request is made yet
      (operator connectivity, "First launch").

## 2. Connect

- [ ] In **Settings**, the gateway base URL is set (the emulator default is `http://10.0.2.2:8787`) and the operator credential
      is entered on the device.
- [ ] **Test connection** succeeds and the banner reads **Connected (test)**.
- [ ] With the gateway stopped, **Test connection** reports **Unreachable** and not a generic error. Start it again.

## 3. Enrol

- [ ] Enrolment completes against the `test` gateway.
- [ ] The `SecurityPostureBadge` shows the true key backing (StrongBox, hardware or software) and the accepted tier. A software
      key is reported as software.
- [ ] Enrolling the same device on a different environment is refused or requires a fresh enrolment (ENVIRONMENTS rule 1).

## 4. Read

- [ ] The plan list and plan detail load from the gateway.
- [ ] Each ready task shows an `order` value. A task without one is not listed as ready (contract §3.6).
- [ ] Plan detail shows the informational card for work-order creation, with no create button (ADR 0014 D5).

## 5. Status transition (`SET_IN_PROGRESS`)

- [ ] The single entry point for **in progress** appears on a pending task.
- [ ] The confirmation shows the operation, plan, task and branch, and the confirmation gesture is required.
- [ ] The result is shown only after the status is read back from the gateway.
- [ ] No control offers **done** or **completed** (ADR 0016 D-1).

## 6. Publish

- [ ] `PublishConfirmScreen` shows the full 40-character SHA, the task, the branch and the plan. In `in_app_confirm` you type the
      first 7 characters, and the capability still binds to the full SHA.
- [ ] A stale head (move the branch head, then confirm) is refused as **stale**, not retried.
- [ ] A successful publish returns an `auditRef`, and the live branch state is checked before success is reported.

## 7. Uncertain outcome and recovery

- [ ] Stop the gateway during a publish. The result is **uncertain completion**, with no automatic retry.
- [ ] Start the gateway and reopen the app. The operation is resolved through `GET /operations/{id}`, and no second send occurs.

## 8. Halt and audit (if the gateway supports them in this run)

- [ ] **Halt** blocks a new publish with a policy refusal; **Resume** restores it.
- [ ] The audit view lists the run's operations and shows no token or capability.

## 9. Evidence to attach

- [ ] The run record above, completed.
- [ ] The gateway's audit references for steps 5 and 6.
- [ ] Settings diagnostics lines for steps 2, 6 and 7 (they contain no tokens or capabilities).
- [ ] The result recorded in `docs/release/PHASE-3-EVIDENCE.md` (PoC task 61) and the tracker rows for EXT-7 and task 61.

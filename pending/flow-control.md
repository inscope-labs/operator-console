The repository is already a HITL enforcement surface built around capability minting, per-action confirmation, and gateway-only enforcement. Integrating the six-assistant flow-control SOP does not require rebuilding that foundation. It requires adding queue, capacity, and flow-state primitives to the console’s domain model, gateway contract, and UI. The enforcement boundary stays exactly where it is today.

---

1. What the console already provides (the HITL baseline)

SOP mechanism Console primitive already present Where
Human confirms consequential actions one at a time Biometric-bound capability minting; no batch approvals 
Spec / merge gate Decision screen + publish/reconcile write flows 
Halt and resume Engine-scoped HALT / RESUME capabilities 
Review agent submissions UPLOAD, RECONCILE, PUBLISH endpoints 
Read readiness / blockers GET /plans/{id}/status, GET /tasks/next 
Audit trail with attribution Decision records carry rationale, scope, evidence; gateway writes audit 
Credential minimalism No GitHub token on device; gateway holds narrow credential 

Intent: keep the console as the authority surface, not the execution engine. The SOP’s “operator manages flow, agents manage implementation” is already the console’s architecture.

---

2. Gap analysis: what the SOP needs that the console lacks

SOP requirement Current console state Gap
WIP limits on review queue No queue column model; GET /tasks/next returns a flat ready list  Need per-project queue depth and hard caps
Pull-based promotion Write actions are operator-initiated, but no scheduler promotes work when capacity opens Need queue state machine and promotion rules
Idle-time instrumentation No metric for “agent waiting on human decision” Need latency timestamps from block to resolution
Multi-project fairness Plan selection exists, but no per-project concurrency cap Need project-level slot accounting
Fallback / stalled-agent handling Halt/resume is global, not per-agent Need per-agent state and fallback chain semantics
Structured handoff artifact REPORT.md lives in submissions, but console has no first-class blocked-task decision object Need BlockedWorkItem model with options
Bottleneck metrics Status shows blocked tasks, but no cycle-time or queue-age metrics  Need flow metrics aggregation

The console already blocks and halts; it does not yet measure and cap flow.

---

3. Integration blueprint

3.1 Domain model additions (domain/model/)

Add these to the existing Kotlin domain layer alongside Plan, Task, WorkOrder, and OperationRequest :

```kotlin
// FlowControl.kt
data class WipLimit(
    val scope: WipScope,          // GLOBAL, PROJECT, STAGE
    val maxOpenItems: Int,
    val currentOpenItems: Int,
)

data class QueueEntry(
    val taskId: String,
    val projectId: String,
    val queueState: QueueState,    // BACKLOG, EXECUTING, REVIEW, BLOCKED, DONE
    val enteredAt: Instant,
    val lastHumanActionAt: Instant?,
)

data class AgentSlot(
    val agentId: String,
    val projectId: String,
    val state: AgentState,         // IDLE, RUNNING, WAITING_FOR_HUMAN, FAILED
    val currentTaskId: String?,
    val idleSince: Instant?,
)

data class BlockedWorkItem(
    val taskId: String,
    val reason: BlockReason,       // SPEC_CONFLICT, OPEN_ITEM, MISSING_INPUT, AGENT_LIMIT
    val options: List<ResolutionOption>,
    val age: Duration,
)
```

Intent: make flow control a first-class domain concept, not a derived view. The existing TaskRepository and PlanRepository  remain read sources; the new FlowRepository owns queue and capacity state.

3.2 Gateway contract extensions (docs/gateway-contract.md)

The gateway is the sole enforcement point . WIP limits and queue promotion must be enforced there, not in the app. Add these endpoints alongside the existing plan/status surface :

New endpoint Purpose Capability required
GET /flow/state?project= Return queue depths, WIP limits, idle agents, blocked items No (Bearer)
POST /flow/queue/{taskId}/promote Promote a task from BACKLOG to EXECUTING when capacity opens Yes (ORDER scope)
POST /flow/queue/{taskId}/hold Move a task to BLOCKED with reason and options Yes (DECIDE scope)
POST /flow/wip Set a WIP limit for a scope (operator policy change) Yes (DECIDE scope)
GET /flow/metrics?project=&window= Return cycle time, queue age, human-latency histogram No (Bearer)

Enforcement rule: the gateway must reject a promote if the target queue column is at its WIP limit, exactly as it rejects a stale head or replayed nonce today . The app never decides capacity locally.

3.3 Android UI additions (ui/flow/)

Add these screens next to the existing dashboard/, plans/, and decision/ packages :

Screen Purpose SOP mapping
Flow Board Column view: Backlog → Executing → Review → Blocked → Done, with per-column WIP counters and cap indicators Visual management + WIP limits
Queue Control List of queued tasks with “promote” action enabled only when capacity allows Pull-based scheduling
Agent Slots Six agent cards showing state, current task, idle time, and fallback target Multi-project fairness + downtime visibility
Blocked Work Each blocked item with reason, age, options, and a “decide” action that opens the existing DecisionScreen  Structured handoff + decision adjudication
Flow Metrics Cycle time, queue age, human-response latency, per-project throughput Bottleneck detection

The Flow Board should reuse the existing ConfirmationScaffold and CapabilitySummaryCard  for every promotion or hold action. No new approval path is introduced.

3.4 State and audit model

The existing decision record already captures attribution, rationale, scope, and evidence . Extend it with:

· flowEvent — PROMOTED, HELD, WIP_LIMIT_HIT, FALLBACK_TRIGGERED
· queueAgeAtAction — how long the item waited
· wipAtAction — the queue depth at the moment of the action

This keeps the audit trail complete for flow decisions, not just for publish/merge decisions.

3.5 Policy engine (gateway-side)

Add a small policy module next to the existing guard set :

```
wip_policy:
  global_max_open_reviews: 6
  per_project_max_executing: 2
  per_project_max_reviews: 3
  human_latency_sla_minutes: 30

fallback_policy:
  on_agent_limit: reassign_to_next_idle_agent
  on_provider_5xx: cooldown_then_retry
  on_stalled_decision: escalate_to_operator_queue
```

The gateway evaluates this policy on every promote and hold request. The app displays the result; it never computes it.

---

4. Phased roadmap (mapped to RELEASE-PLAN.md)

The release plan already runs stages with agent execution and operator gates . Insert the flow-control work as stages after the write layer is scaffolded but before the release gate:

Phase Deliverable Depends on
F1 Domain models: WipLimit, QueueEntry, AgentSlot, BlockedWorkItem Existing domain layer 
F2 Gateway contract additions: /flow/state, /flow/queue/..., /flow/wip, /flow/metrics Existing gateway endpoints 
F3 Gateway policy engine + WIP rejection codes (WIP_LIMIT_HIT, QUEUE_FULL) Guard set 
F4 Android Flow Board and Queue Control screens ui/dashboard/ patterns 
F5 Agent Slots + Blocked Work screens DecisionScreen reuse 
F6 Flow Metrics + idle-time instrumentation Status endpoints 
F7 Conformance vectors for WIP rejection and promotion race conditions Existing conformance vectors 

Each phase follows the repo’s rule: one stage per AI Studio run, operator authorizes the stage, CI is the verifier .

---

5. Security invariants that must not break

Any integration PR must satisfy the existing prohibitions:

1. No GitHub credential on device. The flow-control state comes from Gateway A; the app remains credential-minimal .
2. No local queue computation as enforcement. The gateway must reject over-capacity promotions; the app only displays and requests .
3. No batch approvals. Each promotion, hold, or WIP change requires its own capability and confirmation .
4. No durable queue state on device. Like audit storage, queue state lives in the gateway/consumer repository .
5. No new write path outside the capability model. POST /flow/queue/{id}/promote uses the existing ORDER or DECIDE operation types already in the closed enum .

---

6. Success criteria

The integration is complete when the operator can answer these questions from the console alone, without opening a terminal or asking an agent:

· “What is the queue depth per project, and which column is at its cap?” → Flow Board.
· “Which agent has been idle longest, and what decision is it waiting on?” → Agent Slots + Blocked Work.
· “What is the median time from block to human decision this week?” → Flow Metrics.
· “Can I promote this task right now without violating a WIP limit?” → Queue Control (gateway-enforced).
· “What flow decision was made, by whom, with what rationale, and at what queue depth?” → Extended audit record.

When those five questions have first-class UI and gateway-backed answers, the six-assistant SOP is fully integrated into the operator console.

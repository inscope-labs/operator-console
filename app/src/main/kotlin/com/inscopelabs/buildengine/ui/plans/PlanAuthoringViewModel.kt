package com.inscopelabs.buildengine.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.domain.authoring.PlanDraftAllocator
import com.inscopelabs.buildengine.domain.model.DraftValidationReport
import com.inscopelabs.buildengine.domain.model.PlanDependency
import com.inscopelabs.buildengine.domain.model.PlanDraft
import com.inscopelabs.buildengine.domain.model.PlanDraftSummary
import com.inscopelabs.buildengine.domain.model.PlanPhase
import com.inscopelabs.buildengine.domain.model.PlanStage
import com.inscopelabs.buildengine.domain.model.PlanTaskDraft
import com.inscopelabs.buildengine.domain.model.PlanTaskExecutor
import com.inscopelabs.buildengine.domain.model.PlanTaskStatus
import com.inscopelabs.buildengine.domain.model.PlanVerify
import com.inscopelabs.buildengine.domain.model.PlanVerifyMethod
import com.inscopelabs.buildengine.domain.model.PlanCoverageExemption
import com.inscopelabs.buildengine.domain.usecase.authoring.CreatePlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.DeletePlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.ListPlanDraftsUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.LoadPlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.PlanPreview
import com.inscopelabs.buildengine.domain.usecase.authoring.PlanSubmissionOutcome
import com.inscopelabs.buildengine.domain.usecase.authoring.PreviewPlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.SavePlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.SubmitPlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.ValidatePlanDraftUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The sections of the authoring editor. */
enum class PlanEditorSection(val label: String) {
    DETAILS("Details"),
    PHASES("Phases & Stages"),
    TASKS("Tasks"),
    CONSTRAINTS("Constraints"),
    COVERAGE("Coverage"),
    REVIEW("Review"),
}

/**
 * The authoring editor's state.
 *
 * The draft is the document being edited; everything else is what the editor has derived from it
 * — the validation report, the preview, the submission answer. Nothing here is a plan: a draft
 * is not executable and is not accepted until a repository change carrying it has been merged.
 */
data class PlanEditorState(
    val draft: PlanDraft? = null,
    val section: PlanEditorSection = PlanEditorSection.DETAILS,
    val isDirty: Boolean = false,
    val isLoading: Boolean = false,
    val validation: DraftValidationReport? = null,
    val preview: PlanPreview? = null,
    val submission: PlanSubmissionOutcome? = null,
    val message: String? = null,
    val error: String? = null,
) {
    val hasDraft: Boolean get() = draft != null
}

/**
 * Plan Management Authoring — the editor's state holder (PMA Stage 1).
 *
 * The editor owns a draft and nothing else. It never talks to the gateway: a draft is local
 * authoring state, and the gateway is not where a draft lives. The one thing it deliberately
 * cannot do is submit — see [SubmitPlanDraftUseCase] for why, and for what the operator is told
 * when they ask.
 */
@HiltViewModel
class PlanAuthoringViewModel @Inject constructor(
    private val createPlanDraftUseCase: CreatePlanDraftUseCase,
    private val savePlanDraftUseCase: SavePlanDraftUseCase,
    private val loadPlanDraftUseCase: LoadPlanDraftUseCase,
    private val listPlanDraftsUseCase: ListPlanDraftsUseCase,
    private val deletePlanDraftUseCase: DeletePlanDraftUseCase,
    private val validatePlanDraftUseCase: ValidatePlanDraftUseCase,
    private val previewPlanDraftUseCase: PreviewPlanDraftUseCase,
    private val submitPlanDraftUseCase: SubmitPlanDraftUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(PlanEditorState())
    val state: StateFlow<PlanEditorState> = _state.asStateFlow()

    private val _drafts = MutableStateFlow<List<PlanDraftSummary>>(emptyList())
    val drafts: StateFlow<List<PlanDraftSummary>> = _drafts.asStateFlow()

    /**
     * The draft that was just created and is waiting to be opened.
     *
     * The draft list creates a draft when the operator asks for one and then navigates to it, so
     * the list — not the editor — decides when a draft comes into existence. The id is consumed
     * once it has been acted on, so a recomposition does not navigate twice.
     */
    private val _pendingOpenDraftId = MutableStateFlow<String?>(null)
    val pendingOpenDraftId: StateFlow<String?> = _pendingOpenDraftId.asStateFlow()

    init {
        refreshDrafts()
    }

    /** Reloads the list of drafts on the device. */
    fun refreshDrafts() {
        viewModelScope.launch {
            _drafts.value = listPlanDraftsUseCase()
        }
    }

    /** Creates a new draft and opens it. */
    fun createDraft(planId: String = "", title: String = "") {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val draft = createPlanDraftUseCase(planId, title)
                _state.value = PlanEditorState(
                    draft = draft,
                    section = PlanEditorSection.DETAILS,
                    isDirty = false,
                    message = "Draft created. It is not a plan until it is submitted and merged.",
                )
                _pendingOpenDraftId.value = draft.draftId
                refreshDrafts()
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = error.message ?: "Could not create the draft.",
                )
            }
        }
    }

    /** Opens an existing draft. */
    fun openDraft(draftId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val draft = loadPlanDraftUseCase(draftId)
            if (draft == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "That draft is no longer on this device.",
                )
                return@launch
            }
            _state.value = PlanEditorState(draft = draft, isLoading = false)
        }
    }

    /** Switches the visible section. */
    fun selectSection(section: PlanEditorSection) {
        _state.value = _state.value.copy(section = section)
    }

    // -- details --

    fun updatePlanId(planId: String) = mutate { it.copy(planId = planId.trim()) }

    fun updateTitle(title: String) = mutate { it.copy(title = title) }

    // -- phases --

    fun addPhase() = mutate { draft ->
        val nextId = (draft.phases.mapNotNull { it.id.toIntOrNull() }.maxOrNull() ?: 0) + 1
        draft.copy(phases = draft.phases + PlanPhase(id = nextId.toString(), name = "Phase $nextId"))
    }

    fun updatePhase(index: Int, name: String, goal: String) = mutate { draft ->
        draft.copy(phases = draft.phases.mapIndexed { i, phase ->
            if (i == index) phase.copy(name = name, goal = goal) else phase
        })
    }

    fun removePhase(index: Int) = mutate { draft ->
        val removed = draft.phases.getOrNull(index) ?: return@mutate draft
        draft.copy(
            phases = draft.phases.filterIndexed { i, _ -> i != index },
            // A stage whose phase is gone would be a stage the canonical checker rejects.
            stages = draft.stages.filterNot { it.phaseId == removed.id },
        )
    }

    // -- stages --

    fun addStage(phaseId: String) = mutate { draft ->
        val phase = draft.phases.firstOrNull { it.id == phaseId } ?: draft.phases.firstOrNull()
            ?: return@mutate draft
        val ordinal = draft.stages.count { it.phaseId == phase.id } + 1
        val stage = PlanStage(id = "${phase.id}-$ordinal", name = "Stage $ordinal")
        draft.copy(stages = draft.stages + stage)
    }

    fun updateStage(index: Int, name: String, goal: String, gate: Boolean) = mutate { draft ->
        draft.copy(stages = draft.stages.mapIndexed { i, stage ->
            if (i == index) stage.copy(name = name, goal = goal, gate = gate) else stage
        })
    }

    fun removeStage(index: Int) = mutate { draft ->
        val removed = draft.stages.getOrNull(index) ?: return@mutate draft
        draft.copy(
            stages = draft.stages.filterIndexed { i, _ -> i != index },
            tasks = draft.tasks.map { task ->
                if (task.stageId == removed.id) task.copy(stageId = "") else task
            },
        )
    }

    // -- tasks --

    fun addTask() = mutate { draft ->
        val stage = draft.stages.firstOrNull() ?: return@mutate draft
        draft.copy(
            tasks = draft.tasks + PlanTaskDraft(
                localId = UUID.randomUUID().toString(),
                title = "",
                stageId = stage.id,
                ordinal = PlanDraftAllocator.nextFreeOrdinal(draft, stage.id),
            ),
        )
    }

    fun updateTask(localId: String, transform: (PlanTaskDraft) -> PlanTaskDraft) = mutate { draft ->
        draft.copy(tasks = draft.tasks.map { task -> if (task.localId == localId) transform(task) else task })
    }

    fun removeTask(localId: String) = mutate { draft ->
        val remaining = draft.tasks.filterNot { it.localId == localId }
        draft.copy(
            tasks = remaining.map { task ->
                // A dependency on a task that no longer exists would not resolve.
                task.copy(blockedBy = task.blockedBy.filterNot { it.ref == localId })
            },
        )
    }

    fun moveTask(from: Int, to: Int) = mutate { draft ->
        if (from !in draft.tasks.indices || to !in draft.tasks.indices) return@mutate draft
        val reordered = draft.tasks.toMutableList()
        val moved = reordered.removeAt(from)
        reordered.add(to, moved)
        draft.copy(tasks = reordered)
    }

    fun addDependency(taskLocalId: String, dependency: PlanDependency) = updateTask(taskLocalId) { task ->
        task.copy(blockedBy = task.blockedBy + dependency)
    }

    fun removeDependency(taskLocalId: String, index: Int) = updateTask(taskLocalId) { task ->
        task.copy(blockedBy = task.blockedBy.filterIndexed { i, _ -> i != index })
    }

    fun setTaskExecutor(localId: String, executor: PlanTaskExecutor) =
        updateTask(localId) { it.copy(executor = executor) }

    fun setTaskStatus(localId: String, status: PlanTaskStatus) =
        updateTask(localId) { it.copy(status = status) }

    fun setTaskStage(localId: String, stageId: String) = mutate { draft ->
        draft.copy(
            tasks = draft.tasks.map { task ->
                if (task.localId != localId) return@map task
                val taken = draft.tasks
                    .filter { it.stageId == stageId && it.localId != localId }
                    .any { it.ordinal == task.ordinal }
                val ordinal = if (taken) {
                    PlanDraftAllocator.nextFreeOrdinal(
                        draft.copy(tasks = draft.tasks.map { if (it.localId == localId) it.copy(stageId = stageId) else it }),
                        stageId,
                    )
                } else {
                    task.ordinal
                }
                task.copy(stageId = stageId, ordinal = ordinal)
            },
        )
    }

    fun setTaskVerify(localId: String, method: PlanVerifyMethod, argv: List<String>) =
        updateTask(localId) { it.copy(verify = PlanVerify(method = method, argv = argv)) }

    // -- constraints --

    fun updateDeliverableRoots(roots: List<String>) = mutate { it.copy(deliverableRoots = roots) }

    fun updateDeliverablePatterns(patterns: List<String>) = mutate { it.copy(deliverablePatterns = patterns) }

    fun updateOrderRules(rules: List<String>) = mutate { it.copy(orderRules = rules) }

    fun updateMaxOpenOrders(limit: Int?) = mutate { it.copy(maxOpenOrders = limit) }

    // -- coverage --

    fun addCoverageExemption() = mutate { draft ->
        draft.copy(
            coverageExemptions = draft.coverageExemptions + PlanCoverageExemption(id = "", reason = ""),
        )
    }

    fun updateCoverageExemption(index: Int, id: String, reason: String) = mutate { draft ->
        draft.copy(coverageExemptions = draft.coverageExemptions.mapIndexed { i, exemption ->
            if (i == index) exemption.copy(id = id, reason = reason) else exemption
        })
    }

    fun removeCoverageExemption(index: Int) = mutate { draft ->
        draft.copy(coverageExemptions = draft.coverageExemptions.filterIndexed { i, _ -> i != index })
    }

    // -- lifecycle --

    /** Saves the draft. */
    fun save() {
        val draft = _state.value.draft ?: return
        viewModelScope.launch {
            try {
                val saved = savePlanDraftUseCase(draft)
                _state.value = _state.value.copy(
                    draft = saved,
                    isDirty = false,
                    message = "Draft saved on this device. It has not been submitted.",
                )
                refreshDrafts()
            } catch (error: Exception) {
                _state.value = _state.value.copy(error = error.message ?: "Could not save the draft.")
            }
        }
    }

    /** Runs the canonical validation against the current draft. */
    fun validate() {
        val draft = _state.value.draft ?: return
        val report = validatePlanDraftUseCase(draft)
        _state.value = _state.value.copy(
            validation = report,
            section = PlanEditorSection.REVIEW,
        )
    }

    /** Builds the preview of the files a submission would create. */
    fun preview() {
        val draft = _state.value.draft ?: return
        _state.value = _state.value.copy(preview = previewPlanDraftUseCase(draft), section = PlanEditorSection.REVIEW)
    }

    /** Asks whether the draft can be submitted, and reports why it cannot. */
    fun requestSubmission() {
        val draft = _state.value.draft ?: return
        val outcome = submitPlanDraftUseCase(draft)
        _state.value = _state.value.copy(submission = outcome, section = PlanEditorSection.REVIEW)
    }

    /** Discards the draft. It is local, so discarding is total. */
    fun discard() {
        val draft = _state.value.draft ?: return
        viewModelScope.launch {
            deletePlanDraftUseCase(draft.draftId)
            _state.value = PlanEditorState(message = "Draft discarded.")
            refreshDrafts()
        }
    }

    /** Consumes the pending "open this draft" signal once it has been acted on. */
    fun consumePendingOpen() {
        _pendingOpenDraftId.value = null
    }

    /** Discards a draft by id, without opening it. */
    fun discardById(draftId: String) {
        viewModelScope.launch {
            deletePlanDraftUseCase(draftId)
            refreshDrafts()
        }
    }

    /** Clears the transient message. */
    fun clearMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }

    private fun mutate(transform: (PlanDraft) -> PlanDraft) {
        val draft = _state.value.draft ?: return
        _state.value = _state.value.copy(
            draft = transform(draft),
            isDirty = true,
            validation = null,
            preview = null,
            submission = null,
        )
    }
}

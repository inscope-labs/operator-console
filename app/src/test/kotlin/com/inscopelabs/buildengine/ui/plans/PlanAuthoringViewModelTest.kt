package com.inscopelabs.buildengine.ui.plans

import com.inscopelabs.buildengine.domain.authoring.InMemoryPlanDraftRepository
import com.inscopelabs.buildengine.domain.authoring.PlanDraftAllocator
import com.inscopelabs.buildengine.domain.authoring.PlanDraftFixtures
import com.inscopelabs.buildengine.domain.usecase.authoring.CreatePlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.DeletePlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.ListPlanDraftsUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.LoadPlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.PreviewPlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.SavePlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.SubmitPlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.ValidatePlanDraftUseCase
import com.inscopelabs.buildengine.domain.validation.PlanDraftValidator
import com.inscopelabs.buildengine.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Editor state tests for the plan authoring screen.
 *
 * The editor's job is to hold a draft, show the operator what is wrong with it, and never let a
 * draft look like a plan.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanAuthoringViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: InMemoryPlanDraftRepository
    private lateinit var viewModel: PlanAuthoringViewModel

    @Before
    fun setUp() {
        repository = InMemoryPlanDraftRepository()
        val validator = PlanDraftValidator()
        viewModel = PlanAuthoringViewModel(
            createPlanDraftUseCase = CreatePlanDraftUseCase(repository),
            savePlanDraftUseCase = SavePlanDraftUseCase(repository),
            loadPlanDraftUseCase = LoadPlanDraftUseCase(repository),
            listPlanDraftsUseCase = ListPlanDraftsUseCase(repository),
            deletePlanDraftUseCase = DeletePlanDraftUseCase(repository),
            validatePlanDraftUseCase = ValidatePlanDraftUseCase(validator),
            previewPlanDraftUseCase = PreviewPlanDraftUseCase(),
            submitPlanDraftUseCase = SubmitPlanDraftUseCase(validator),
        )
    }

    @Test
    fun `a created draft is opened and marked as waiting to be opened`() = runTest {
        viewModel.createDraft(title = "My Plan")

        val state = viewModel.state.value
        assertNotNull(state.draft)
        assertEquals("my-plan", state.draft?.planId)
        assertEquals(state.draft?.draftId, viewModel.pendingOpenDraftId.value)
        assertFalse(state.isDirty)
    }

    @Test
    fun `the pending open signal is consumed once acted on`() = runTest {
        viewModel.createDraft(title = "My Plan")
        viewModel.consumePendingOpen()

        assertNull(viewModel.pendingOpenDraftId.value)
    }

    @Test
    fun `editing marks the draft as having unsaved changes`() = runTest {
        viewModel.createDraft(title = "My Plan")
        viewModel.updateTitle("A better title")

        assertTrue(viewModel.state.value.isDirty)
        assertEquals("A better title", viewModel.state.value.draft?.title)
    }

    @Test
    fun `a phase is given the next free id`() = runTest {
        viewModel.createDraft(title = "My Plan")
        viewModel.addPhase()
        viewModel.addPhase()

        val phases = viewModel.state.value.draft?.phases.orEmpty()
        assertEquals(2, phases.size)
        assertEquals("1", phases[0].id)
        assertEquals("2", phases[1].id)
    }

    @Test
    fun `a stage is given a PHASE-STAGE id`() = runTest {
        viewModel.createDraft(title = "My Plan")
        viewModel.addPhase()
        viewModel.addStage("1")

        val stage = viewModel.state.value.draft?.stages?.single()
        assertEquals("1-1", stage?.id)
    }

    @Test
    fun `removing a phase removes the stages that belonged to it`() = runTest {
        viewModel.createDraft(title = "My Plan")
        viewModel.addPhase()
        viewModel.addStage("1")
        viewModel.removePhase(0)

        assertTrue(viewModel.state.value.draft?.stages.orEmpty().isEmpty())
    }

    @Test
    fun `a task is given the next free position in its stage`() = runTest {
        viewModel.createDraft(title = "My Plan")
        viewModel.addPhase()
        viewModel.addStage("1")
        viewModel.addTask()
        viewModel.addTask()

        val tasks = viewModel.state.value.draft?.tasks.orEmpty()
        assertEquals(2, tasks.size)
        assertEquals(0, tasks[0].ordinal)
        assertEquals(1, tasks[1].ordinal)
    }

    @Test
    fun `a task's identifier is derived from its stage and position`() = runTest {
        viewModel.createDraft(title = "My Plan")
        viewModel.addPhase()
        viewModel.addStage("1")
        viewModel.addTask()

        val draft = viewModel.state.value.draft!!
        assertEquals("<NNNNN>-1-100-00", PlanDraftAllocator.taskId(draft, draft.tasks.single()))
    }

    @Test
    fun `moving a task does not change its identifier`() = runTest {
        viewModel.createDraft(title = "My Plan")
        viewModel.addPhase()
        viewModel.addStage("1")
        viewModel.addTask()
        viewModel.addTask()

        val draft = viewModel.state.value.draft!!
        val before = PlanDraftAllocator.taskId(draft, draft.tasks[0])
        viewModel.moveTask(0, 1)

        val moved = viewModel.state.value.draft!!
        assertEquals(before, PlanDraftAllocator.taskId(moved, moved.tasks.last()))
    }

    @Test
    fun `removing a task removes the dependencies that named it`() = runTest {
        viewModel.createDraft(title = "My Plan")
        viewModel.addPhase()
        viewModel.addStage("1")
        viewModel.addTask()
        viewModel.addTask()

        val tasks = viewModel.state.value.draft!!.tasks
        viewModel.addDependency(tasks[1].localId, com.inscopelabs.buildengine.domain.model.PlanDependency(
            type = com.inscopelabs.buildengine.domain.model.PlanDependencyType.TASK,
            ref = tasks[0].localId,
        ))
        viewModel.removeTask(tasks[0].localId)

        val remaining = viewModel.state.value.draft!!.tasks.single()
        assertTrue(remaining.blockedBy.isEmpty())
    }

    @Test
    fun `saving clears the unsaved flag and bumps the revision`() = runTest {
        viewModel.createDraft(title = "My Plan")
        val revision = viewModel.state.value.draft!!.revision
        viewModel.updateTitle("Changed")
        viewModel.save()

        assertFalse(viewModel.state.value.isDirty)
        assertEquals(revision + 1, viewModel.state.value.draft?.revision)
    }

    @Test
    fun `validating reports the errors and moves to the review section`() = runTest {
        viewModel.createDraft(title = "My Plan")
        viewModel.updateTitle("")
        viewModel.validate()

        val state = viewModel.state.value
        assertEquals(PlanEditorSection.REVIEW, state.section)
        assertNotNull(state.validation)
        assertTrue(state.validation!!.errors.any { it.field == "plan.title" })
    }

    @Test
    fun `the preview is the set of files a submission would create`() = runTest {
        viewModel.createDraft(title = "My Plan")
        viewModel.preview()

        val preview = viewModel.state.value.preview
        assertNotNull(preview)
        assertTrue(preview!!.authoredFiles.any { it.path == "plans/my-plan/plan.json" })
    }

    @Test
    fun `asking to submit reports that the repository write path is missing`() = runTest {
        viewModel.createDraft(title = "My Plan")
        viewModel.requestSubmission()

        val outcome = viewModel.state.value.submission
        assertNotNull(outcome)
        assertFalse(outcome!!.isSubmittable)
        assertTrue(outcome.blockers.any { it.contains("plan-authoring") })
    }

    @Test
    fun `discarding removes the draft from the device`() = runTest {
        viewModel.createDraft(title = "My Plan")
        val draftId = viewModel.state.value.draft!!.draftId
        viewModel.discard()

        assertNull(repository.loadDraft(draftId))
        assertNull(viewModel.state.value.draft)
    }

    @Test
    fun `the draft list is refreshed after a draft is created`() = runTest {
        viewModel.createDraft(title = "My Plan")

        assertEquals(1, viewModel.drafts.value.size)
    }

    @Test
    fun `a draft can be reopened by its id`() = runTest {
        val draft = PlanDraftFixtures.draft()
        repository.saveDraft(draft)

        viewModel.openDraft(draft.draftId)

        assertEquals(draft.planId, viewModel.state.value.draft?.planId)
    }
}

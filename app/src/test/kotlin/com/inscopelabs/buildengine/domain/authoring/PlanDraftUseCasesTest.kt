package com.inscopelabs.buildengine.domain.authoring

import com.inscopelabs.buildengine.domain.usecase.authoring.CreatePlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.DeletePlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.ListPlanDraftsUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.LoadPlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.PreviewPlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.SavePlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.SubmitPlanDraftUseCase
import com.inscopelabs.buildengine.domain.usecase.authoring.ValidatePlanDraftUseCase
import com.inscopelabs.buildengine.domain.validation.PlanDraftValidator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the plan authoring use cases.
 *
 * The behaviour that matters: a draft is created, saved, reopened and discarded on the device,
 * and a draft is never submitted to a repository that cannot receive it.
 */
class PlanDraftUseCasesTest {

    private val repository = InMemoryPlanDraftRepository()
    private val createUseCase = CreatePlanDraftUseCase(repository)
    private val saveUseCase = SavePlanDraftUseCase(repository)
    private val loadUseCase = LoadPlanDraftUseCase(repository)
    private val listUseCase = ListPlanDraftsUseCase(repository)
    private val deleteUseCase = DeletePlanDraftUseCase(repository)
    private val validateUseCase = ValidatePlanDraftUseCase(PlanDraftValidator())
    private val previewUseCase = PreviewPlanDraftUseCase()
    private val submitUseCase = SubmitPlanDraftUseCase(PlanDraftValidator())

    @Test
    fun `a new draft gets a plan id derived from its title`() = runTest {
        val draft = createUseCase(title = "My New Plan")

        assertEquals("my-new-plan", draft.planId)
        assertEquals("My New Plan", draft.title)
        assertEquals(1, repository.size())
    }

    @Test
    fun `a new draft does not allocate a plan number`() = runTest {
        val draft = createUseCase(title = "My New Plan")

        assertEquals("", draft.planNumber)
        assertFalse(draft.isPlanNumberReserved)
    }

    @Test
    fun `a requested plan id is kept when it is legal`() = runTest {
        val draft = createUseCase(requestedPlanId = "explicit-plan", title = "Ignored")

        assertEquals("explicit-plan", draft.planId)
    }

    @Test
    fun `a plan id already used on this device is made unique`() = runTest {
        createUseCase(requestedPlanId = "shared", title = "One")
        val second = createUseCase(requestedPlanId = "shared", title = "Two")

        assertEquals("shared-2", second.planId)
    }

    @Test
    fun `saving bumps the revision`() = runTest {
        val draft = createUseCase(title = "My Plan")

        val saved = saveUseCase(draft)

        assertEquals(draft.revision + 1, saved.revision)
        assertEquals(saved.revision, loadUseCase(draft.draftId)?.revision)
    }

    @Test
    fun `a draft that is not on the device reads back as null`() = runTest {
        assertNull(loadUseCase("nothing-here"))
    }

    @Test
    fun `a draft can be reopened after being saved`() = runTest {
        val draft = createUseCase(title = "My Plan")
        saveUseCase(draft)

        val reopened = loadUseCase(draft.draftId)

        assertNotNull(reopened)
        assertEquals(draft.planId, reopened?.planId)
    }

    @Test
    fun `discarding a draft removes it`() = runTest {
        val draft = createUseCase(title = "My Plan")

        assertTrue(deleteUseCase(draft.draftId))
        assertFalse(deleteUseCase(draft.draftId))
        assertNull(loadUseCase(draft.draftId))
    }

    @Test
    fun `the draft list is most recently updated first`() = runTest {
        repository.saveDraft(PlanDraftFixtures.draft(draftId = "older", updatedAtEpochMs = 10L))
        repository.saveDraft(PlanDraftFixtures.draft(draftId = "newer", updatedAtEpochMs = 20L))

        val listed = listUseCase()

        assertEquals(2, listed.size)
        assertEquals("newer", listed.first().draftId)
    }

    @Test
    fun `the preview is the set of files a submission would create`() = runTest {
        val draft = PlanDraftFixtures.draft()

        val preview = previewUseCase(draft)

        assertEquals("my-plan", preview.planId)
        assertEquals(1, preview.taskCount)
        assertTrue(preview.authoredFiles.any { it.path == "plans/my-plan/plan.json" })
        assertTrue(preview.files.any { it.kind == PlannedFileKind.GENERATED })
    }

    @Test
    fun `a draft with errors is not submittable`() = runTest {
        val draft = PlanDraftFixtures.draft(title = "")

        val outcome = submitUseCase(draft)

        assertFalse(outcome.isSubmittable)
        assertTrue(outcome.blockers.any { it.contains("validation error") })
    }

    @Test
    fun `an unreserved plan number blocks submission`() = runTest {
        val draft = PlanDraftFixtures.draft(planNumber = "")

        val outcome = submitUseCase(draft)

        assertFalse(outcome.isSubmittable)
        assertTrue(outcome.blockers.any { it.contains("plan number is not reserved") })
    }

    @Test
    fun `submission is blocked because the repository write path does not exist`() = runTest {
        val draft = PlanDraftFixtures.draft()

        val outcome = submitUseCase(draft)

        assertFalse(outcome.isSubmittable)
        assertTrue(outcome.blockers.any { it.contains("plan-authoring") })
        assertTrue(outcome.blockers.any { it.contains("Build Engine") })
    }

    @Test
    fun `a valid draft reports the missing write path rather than a validation failure`() = runTest {
        val draft = PlanDraftFixtures.draft()

        val outcome = submitUseCase(draft)

        assertTrue(outcome.blockers.none { it.contains("validation error") })
        assertTrue(outcome.message.contains("not available yet"))
    }

    @Test
    fun `the validation use case reports the canonical validator as unavailable`() = runTest {
        val report = validateUseCase(PlanDraftFixtures.draft())

        assertFalse(report.canonicalServerValidationAvailable)
        assertTrue(report.isValid)
    }
}

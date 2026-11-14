package com.inscopelabs.buildengine.domain.usecase

import com.inscopelabs.buildengine.domain.usecase.write.CreateWorkOrderResult
import com.inscopelabs.buildengine.domain.usecase.write.CreateWorkOrderUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 3.4 (T3-404 / PR3-027 / BD-1.3 Option B): work-order creation is deferred to engine tooling.
 */
class CreateWorkOrderUseCaseTest {

    @Test
    fun `invoke returns UnsupportedByContract citing BD-1-3 and PR3-027`() = runTest {
        val result = CreateWorkOrderUseCase()(plan = "atlas", taskIds = listOf("10008"))
        assertTrue(result is CreateWorkOrderResult.UnsupportedByContract)
        val reason = (result as CreateWorkOrderResult.UnsupportedByContract).reason
        assertTrue(reason.contains("BD-1.3"))
        assertTrue(reason.contains("PR3-027"))
    }
}

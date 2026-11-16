package com.inscopelabs.buildengine.domain.usecase

import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.domain.model.TaskStatus
import com.inscopelabs.buildengine.domain.usecase.write.TaskFieldPublishOrderResolver
import com.inscopelabs.buildengine.domain.usecase.write.UnresolvedPublishOrderResolver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PR3-021: order resolution from contract revision 1.2 task field (ADR 0014 D1).
 */
class PublishOrderResolverTest {

    private fun task(order: String) = Task(
        id = "10008",
        planId = "atlas",
        title = "Refactor parser",
        status = TaskStatus.PENDING,
        branch = "task-10008-refactor-parser",
        headSha = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
        order = order,
    )

    @Test
    fun `TaskFieldPublishOrderResolver returns non-blank order`() = runTest {
        val resolver = TaskFieldPublishOrderResolver()
        assertEquals("order-7731", resolver.resolve(task("order-7731")))
    }

    @Test
    fun `TaskFieldPublishOrderResolver returns null for blank order`() = runTest {
        val resolver = TaskFieldPublishOrderResolver()
        assertNull(resolver.resolve(task("")))
        assertNull(resolver.resolve(task("   ")))
    }

    @Test
    fun `UnresolvedPublishOrderResolver always returns null`() = runTest {
        val resolver = UnresolvedPublishOrderResolver()
        assertNull(resolver.resolve(task("order-7731")))
    }
}

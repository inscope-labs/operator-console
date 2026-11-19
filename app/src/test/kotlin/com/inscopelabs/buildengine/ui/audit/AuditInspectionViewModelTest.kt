package com.inscopelabs.buildengine.ui.audit

import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.GatewayHttpException
import com.inscopelabs.buildengine.core.network.dto.AuditEntryDto
import com.inscopelabs.buildengine.testutil.FakeGatewayApi
import com.inscopelabs.buildengine.testutil.MainDispatcherRule
import com.inscopelabs.buildengine.ui.common.UiState
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [AuditInspectionViewModel] (PoC Task 22 / T4-401).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuditInspectionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial state is Idle and makes no network requests`() {
        var listCalls = 0
        val gatewayApi = FakeGatewayApi().apply {
            listAuditResult = {
                listCalls++
                emptyList()
            }
        }
        val vm = AuditInspectionViewModel(gatewayApi, ErrorMapper())
        assertEquals(UiState.Idle, vm.uiState.value)
        assertEquals(0, listCalls)
    }

    @Test
    fun `load fetches GET audit and redacts sensitive substrings before exposing state`() = runTest {
        val gatewayApi = FakeGatewayApi().apply {
            listAuditResult = {
                listOf(
                    AuditEntryDto(
                        auditRef = "audit-20261118-001",
                        timestamp = "2026-11-18T12:00:00Z",
                        operation = "PUBLISH",
                        plan = "atlas",
                        task = "10008",
                        branch = "task-10008-parser Authorization: Bearer secretToken999",
                        headSha = "a".repeat(40),
                        kid = "k2",
                        authTier = "hardware_confirm",
                    ),
                )
            }
        }
        val vm = AuditInspectionViewModel(gatewayApi, ErrorMapper())
        vm.load()

        val state = vm.uiState.value as UiState.Success
        assertEquals(1, state.data.records.size)
        val rec = state.data.records.single()
        assertEquals("audit-20261118-001", rec.auditRef)
        assertEquals("PUBLISH", rec.operation)
        assertEquals("k2", rec.kid)
        assertFalse(rec.branch.contains("secretToken999"))
        assertTrue(rec.branch.contains("[REDACTED]"))
    }

    @Test
    fun `resolve queries gateway directly by ref rather than searching loaded list`() = runTest {
        var resolvedRequestedRef: String? = null
        val gatewayApi = FakeGatewayApi().apply {
            listAuditResult = {
                listOf(
                    AuditEntryDto(
                        auditRef = "audit-local-only",
                        timestamp = "2026-11-18T11:00:00Z",
                        operation = "HALT",
                    ),
                )
            }
            resolveAuditResult = { ref ->
                resolvedRequestedRef = ref
                AuditEntryDto(
                    auditRef = ref,
                    timestamp = "2026-11-18T12:05:00Z",
                    operation = "RESUME",
                    plan = "atlas",
                    task = null,
                    branch = "engine",
                    headSha = "b".repeat(40),
                    kid = "k3",
                    authTier = "in_app_confirm",
                )
            }
        }

        val vm = AuditInspectionViewModel(gatewayApi, ErrorMapper())
        vm.load()
        vm.resolve("  audit-remote-999  ")

        assertEquals("audit-remote-999", resolvedRequestedRef)
        val state = vm.uiState.value as UiState.Success
        assertNotNull(state.data.resolved)
        assertEquals("audit-remote-999", state.data.resolved?.auditRef)
        assertEquals("RESUME", state.data.resolved?.operation)
        assertEquals("", state.data.resolved?.task)
        assertNull(state.data.resolveError)
        assertFalse(state.data.isResolving)
    }

    @Test
    fun `resolve 404 clears stale resolved record preserves journal list and sets resolveError`() = runTest {
        var shouldFail = false
        val gatewayApi = FakeGatewayApi().apply {
            listAuditResult = {
                listOf(
                    AuditEntryDto(
                        auditRef = "audit-1",
                        timestamp = "2026-11-18T11:00:00Z",
                        operation = "PUBLISH",
                    ),
                )
            }
            resolveAuditResult = { ref ->
                if (shouldFail) {
                    throw GatewayHttpException(
                        statusCode = 404,
                        body = """{"code":"NOT_FOUND","message":"audit ref not found: $ref"}""",
                        message = "not found",
                    )
                }
                AuditEntryDto(auditRef = ref, timestamp = "2026-11-18T11:00:00Z", operation = "PUBLISH")
            }
        }

        val vm = AuditInspectionViewModel(gatewayApi, ErrorMapper())
        vm.load()
        vm.resolve("audit-1")
        assertNotNull((vm.uiState.value as UiState.Success).data.resolved)

        shouldFail = true
        vm.resolve("audit-missing")

        val state = vm.uiState.value as UiState.Success
        assertEquals(1, state.data.records.size)
        assertNull(state.data.resolved)
        assertNotNull(state.data.resolveError)
        assertTrue(state.data.resolveError!!.contains("audit-missing"))
        assertFalse(state.data.isResolving)
    }

    @Test
    fun `load failure transitions to Error state with mapped message`() = runTest {
        val gatewayApi = FakeGatewayApi().apply {
            listAuditResult = { throw IOException("connection refused") }
        }
        val vm = AuditInspectionViewModel(gatewayApi, ErrorMapper())
        vm.load()

        val state = vm.uiState.value as UiState.Error
        assertTrue(state.message.contains("connection refused"))
    }
}

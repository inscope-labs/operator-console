package com.inscopelabs.buildengine.domain.usecase

import com.inscopelabs.buildengine.domain.model.WorkOrder
import com.inscopelabs.buildengine.domain.usecase.write.CreateWorkOrderResult
import com.inscopelabs.buildengine.domain.usecase.write.CreateWorkOrderUseCase
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 3.4 (T3-404 / PR3-027 / BD-1.3 Option B, ADR 0014 D5): work-order creation is deferred
 * to engine tooling.
 *
 * The register's acceptance criterion is "No scaffolded work-order action is represented as
 * operational". These tests pin that down structurally rather than by convention:
 *
 * - the use case is not referenced by any production source outside its own file;
 * - it is not provided by the Hilt graph, so nothing can inject it;
 * - it carries no dependency that could mint, journal or send anything;
 * - the gateway surface exposes no work-order creation endpoint (I-10);
 * - the documented alternative is actually shown to the operator, and it carries no action.
 */
class WorkOrderDeferralTest {

    // -- The deferral itself ---------------------------------------------------------------

    @Test
    fun `the canonical deferral reason names BD-1-3 option B and PR3-027`() {
        val reason = WorkOrder.DEFERRED_REASON

        assertTrue("the reason must name the operator decision", reason.contains("BD-1.3"))
        assertTrue("the reason must name the register item", reason.contains("PR3-027"))
        assertTrue(
            "the reason must say work orders are created by engine tooling",
            reason.contains("engine tooling"),
        )
    }

    @Test
    fun `the canonical deferral reason cites the contract clause that forbids the endpoint`() {
        val reason = WorkOrder.DEFERRED_REASON

        assertTrue(
            "the reason must state that the contract defines no request, response or schema",
            reason.contains("no section 3 request or response and no schema"),
        )
        assertTrue(
            "the reason must cite the contract revision that settles the deferral",
            reason.contains("revision 1.2"),
        )
        assertTrue(
            "the reason must state that the console never invents an identifier",
            reason.contains("never derives or invents one"),
        )
    }

    @Test
    fun `the use case refuses with exactly the canonical reason`() = runTest {
        val result = CreateWorkOrderUseCase()(plan = "atlas", taskIds = listOf("10008"))

        assertTrue(result is CreateWorkOrderResult.UnsupportedByContract)
        assertEquals(
            "the use case and the operator-facing text must not drift apart",
            WorkOrder.DEFERRED_REASON,
            (result as CreateWorkOrderResult.UnsupportedByContract).reason,
        )
    }

    @Test
    fun `the use case has no dependency it could mint journal or send with`() {
        val constructors = CreateWorkOrderUseCase::class.java.declaredConstructors

        assertEquals(1, constructors.size)
        assertEquals(
            "the deferral use case must take no dependency, so it cannot reach the gateway",
            0,
            constructors.single().parameterCount,
        )
    }

    // -- The use case is not on an operational path -----------------------------------------

    @Test
    fun `no production source outside the use case file references the use case`() {
        val useCaseFile = "domain/usecase/write/CreateWorkOrderUseCase.kt"
        val offenders = mutableListOf<String>()

        mainKotlinFiles().forEach { (relativePath, content) ->
            if (relativePath.endsWith(useCaseFile)) return@forEach
            if (content.contains("CreateWorkOrderUseCase")) {
                offenders += relativePath
            }
        }

        assertTrue(
            "CreateWorkOrderUseCase must not be reachable from any production source " +
                "(no ViewModel, screen or repository may inject it): $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the use case is not provided by the Hilt graph`() {
        val module = readRepoFile("app/src/main/kotlin/com/inscopelabs/buildengine/di/UseCaseModule.kt")

        assertFalse(
            "UseCaseModule must not provide CreateWorkOrderUseCase, or it stays injectable",
            module.contains("CreateWorkOrderUseCase"),
        )
    }

    // -- The console implements no work-order creation endpoint (I-10) -----------------------

    @Test
    fun `the gateway endpoint table defines no work-order creation route`() {
        val endpoints = readRepoFile("app/src/main/kotlin/com/inscopelabs/buildengine/core/network/GatewayEndpoints.kt")

        assertFalse(
            "the console must not name a work-order creation route: the contract defines no " +
                "request, response or schema for it (I-10)",
            endpoints.contains("/order"),
        )
    }

    @Test
    fun `the gateway api exposes no work-order creation method`() {
        val api = readRepoFile("app/src/main/kotlin/com/inscopelabs/buildengine/data/remote/GatewayApi.kt")

        assertFalse(
            "the console must not expose a work-order creation method (I-10)",
            Regex("""fun\s+\w*[Oo]rder\w*\s*\(""").containsMatchIn(api),
        )
    }

    // -- The documented alternative is shown, and carries no action --------------------------

    @Test
    fun `the documented alternative is present in the plan detail screen`() {
        val screen = readRepoFile("app/src/main/kotlin/com/inscopelabs/buildengine/ui/plans/PlanDetailScreen.kt")

        assertTrue(
            "the plan detail screen must show the work-order deferral",
            screen.contains("WorkOrderDeferralCard"),
        )
        assertTrue(
            "the plan detail screen must render the card",
            screen.contains("work_order_deferral_card"),
        )
        assertTrue(
            "the card must render the operator-facing text",
            screen.contains("R.string.work_order_deferral_body"),
        )
    }

    @Test
    fun `the documented alternative carries no action`() {
        val screen = readRepoFile("app/src/main/kotlin/com/inscopelabs/buildengine/ui/plans/PlanDetailScreen.kt")
        val body = functionBody(screen, "private fun WorkOrderDeferralCard()")

        assertFalse(
            "the documented alternative must not offer a work-order creation action",
            body.contains("onClick"),
        )
        assertFalse(
            "the documented alternative must not offer a button",
            body.contains("Button("),
        )
    }

    @Test
    fun `the operator-facing text states where work orders come from`() {
        val strings = readRepoFile("app/src/main/res/values/strings.xml")

        assertTrue(
            "the work-order title must be a string resource",
            strings.contains("name=\"work_order_title\""),
        )
        assertTrue(
            "the work-order body must be a string resource",
            strings.contains("name=\"work_order_deferral_body\""),
        )
        assertTrue(
            "the operator-facing text must say work orders are created in engine tooling",
            strings.contains("Work orders are created in engine tooling"),
        )
        assertTrue(
            "the operator-facing text must say the console does not create work orders",
            strings.contains("This console does not create work orders"),
        )
    }

    @Test
    fun `the operator-facing text and the domain reason agree on the deferral`() {
        val strings = readRepoFile("app/src/main/res/values/strings.xml")
        val body = strings.substringAfter("name=\"work_order_deferral_body\">").substringBefore("</string>")

        assertTrue(
            "the operator-facing text and the domain reason must both name engine tooling",
            body.contains("engine tooling") && WorkOrder.DEFERRED_REASON.contains("engine tooling"),
        )
        assertTrue(
            "the operator-facing text must not offer a creation path the domain refuses",
            body.contains("does not create work orders"),
        )
    }

    // -- Helpers ---------------------------------------------------------------------------

    /**
     * The text of the function declaration at [signature], from its opening brace to the
     * matching closing brace.
     */
    private fun functionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("could not find `$signature` in the source", start >= 0)
        val open = source.indexOf('{', start)
        assertTrue("could not find the body of `$signature`", open >= 0)

        var depth = 0
        var index = open
        while (index < source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open, index + 1)
                }
            }
            index++
        }
        throw AssertionError("unbalanced braces in `$signature`")
    }

    private fun mainKotlinFiles(): List<Pair<String, String>> {
        val root = listOf(
            File("app/src/main/kotlin"),
            File("src/main/kotlin"),
            File("../app/src/main/kotlin"),
        ).firstOrNull { it.exists() && it.isDirectory }
            ?: error("Could not find the main kotlin source directory")

        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.invariantSeparatorsPath }
            .map { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
            .toList()
    }

    /**
     * Reads a repository file given a path relative to the repository root.
     *
     * The unit test working directory is the `app` module, so both the repository root and
     * the module directory are tried.
     */
    private fun readRepoFile(path: String): String {
        val candidates = listOf(File(path), File("..", path))
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate `$path` from ${File(".").absolutePath}")
        return file.readText()
    }
}

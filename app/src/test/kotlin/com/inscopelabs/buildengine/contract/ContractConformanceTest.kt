package com.inscopelabs.buildengine.contract

import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.GatewayEndpoints
import com.inscopelabs.buildengine.core.network.GatewayFailureState
import com.inscopelabs.buildengine.core.network.GatewayHttpClient
import com.inscopelabs.buildengine.core.network.GatewayProtocolException
import com.inscopelabs.buildengine.core.network.dto.BranchHeadDto
import com.inscopelabs.buildengine.core.network.dto.DecideAckDto
import com.inscopelabs.buildengine.core.network.dto.DecideRequestDto
import com.inscopelabs.buildengine.core.network.dto.DoctorCheckDto
import com.inscopelabs.buildengine.core.network.dto.DoctorReportDto
import com.inscopelabs.buildengine.core.network.dto.EngineScopeRequestDto
import com.inscopelabs.buildengine.core.network.dto.EnrollChallengeDto
import com.inscopelabs.buildengine.core.network.dto.EnrollRequestDto
import com.inscopelabs.buildengine.core.network.dto.EnrollResponseDto
import com.inscopelabs.buildengine.core.network.dto.GatewayAckDto
import com.inscopelabs.buildengine.core.network.dto.GatewayErrorDto
import com.inscopelabs.buildengine.core.network.dto.GatewayHealthDto
import com.inscopelabs.buildengine.core.network.dto.OperationLookupDto
import com.inscopelabs.buildengine.core.network.dto.PlanStatusDto
import com.inscopelabs.buildengine.core.network.dto.PlanStatusTransitionAckDto
import com.inscopelabs.buildengine.core.network.dto.PlanStatusTransitionRequestDto
import com.inscopelabs.buildengine.core.network.dto.PlanSummaryDto
import com.inscopelabs.buildengine.core.network.dto.PlanValidationDto
import com.inscopelabs.buildengine.core.network.dto.PublishRequestDto
import com.inscopelabs.buildengine.core.network.dto.ReconcileRequestDto
import com.inscopelabs.buildengine.core.network.dto.RenderCheckDto
import com.inscopelabs.buildengine.core.network.dto.RevokeKeyRequestDto
import com.inscopelabs.buildengine.core.network.dto.RevokeKeyResponseDto
import com.inscopelabs.buildengine.core.network.dto.RotateKeyRequestDto
import com.inscopelabs.buildengine.core.network.dto.SyncRequestDto
import com.inscopelabs.buildengine.core.network.dto.SyncStatusDto
import com.inscopelabs.buildengine.core.network.dto.TaskSummaryDto
import com.inscopelabs.buildengine.core.network.dto.UploadRequestDto
import com.inscopelabs.buildengine.data.remote.GatewayRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract-to-client freeze verification against `docs/gateway-contract.md` (Revision 1.1)
 * and `docs/schemas/` (`PR3-003`).
 *
 * Pre-existing client-to-contract discrepancies are pinned in explicit ratchet baselines
 * (findings, not approvals) so tests fail on any NEW drift and fail when a baseline
 * discrepancy is fixed without shrinking the allowlist.
 */
class ContractConformanceTest {

    private val repoRoot: File = ContractSchemaSupport.resolveRepoRoot()
    private val contractText: String = ContractSchemaSupport.readContractMarkdown(repoRoot)

    // -- Task B & D: Endpoint-path and HTTP-method conformance --

    @Test
    fun `B D - every GatewayEndpoints constant and GatewayRepository HTTP method maps to contract revision 1_1`() {
        assertTrue(contractText.contains("Revision 1.1"))
        val contractRoutes = ContractSchemaSupport.parseContractRoutes(contractText)
        val section3Routes = contractRoutes.filter { it.source == "section_3" }
        val normalizedSection3Paths = section3Routes.map { it.normalizedPath }.toSet()

        val clientConstants = ContractSchemaSupport.parseGatewayEndpointConstants(repoRoot)
        assertFalse(
            "Dead route DECISION must remain deleted from GatewayEndpoints",
            "DECISION" in clientConstants,
        )

        val expectedConstantToFn: Map<String, Pair<String, List<String>>> = mapOf(
            "HEALTH" to ("health" to listOf("GET")),
            "PLANS" to ("plans" to listOf("GET")),
            "PLAN_STATUS" to ("planStatus" to listOf("GET", "POST")),
            "PLAN_DECIDE" to ("planDecide" to listOf("POST")),
            "PLAN_VALIDATE" to ("planValidate" to listOf("POST")),
            "PLAN_RENDER_CHECK" to ("planRenderCheck" to listOf("GET")),
            "TASKS_NEXT" to ("tasksNext" to listOf("GET")),
            "BRANCH_HEAD" to ("branchHead" to listOf("GET")),
            "PUBLISH" to ("publish" to listOf("POST")),
            "RECONCILE" to ("reconcile" to listOf("POST")),
            "UPLOAD" to ("upload" to listOf("POST")),
            "HALT" to ("halt" to listOf("POST")),
            "RESUME" to ("resume" to listOf("POST")),
            "SYNC" to ("sync" to listOf("POST")),
            "DOCTOR" to ("doctor" to listOf("GET")),
            "OPERATION_LOOKUP" to ("operationLookup" to listOf("GET")),
            "AUDIT" to ("audit" to listOf("GET")),
            "AUDIT_REF" to ("auditRecord" to listOf("GET")),
            "ENROLL" to ("enroll" to listOf("POST")),
            "ENROLL_ROTATE" to ("enrollRotate" to listOf("POST")),
            "ENROLL_CHALLENGE" to ("enrollChallenge" to listOf("POST")),
            "ADMIN_REVOKE" to ("adminRevoke" to listOf("POST")),
        )
        assertEquals(
            "GatewayEndpoints constant set must match audited client endpoints",
            expectedConstantToFn.keys,
            clientConstants.keys,
        )

        val repoCalls = ContractSchemaSupport.parseGatewayRepositoryMethods(repoRoot)
        val repoMethodsByFn: Map<String, Set<String>> = repoCalls.groupBy(
            keySelector = { it.second },
            valueTransform = { it.first },
        ).mapValues { (_, methods) -> methods.toSet() }

        for ((constName, rawClientPath) in clientConstants) {
            val normalizedClientPath = ContractSchemaSupport.normalizePath(rawClientPath)
            assertTrue(
                "Client endpoint $constName ($rawClientPath -> $normalizedClientPath) has no §3 contract route",
                normalizedClientPath in normalizedSection3Paths,
            )

            val (fnName, expectedMethods) = expectedConstantToFn.getValue(constName)
            val actualRepoMethods = repoMethodsByFn[fnName].orEmpty()
            assertEquals(
                "GatewayRepository HTTP methods for $constName ($fnName) mismatch",
                expectedMethods.toSet(),
                actualRepoMethods,
            )

            val matchingContractMethods = section3Routes
                .filter { it.normalizedPath == normalizedClientPath }
                .map { it.method }
                .toSet()
            assertTrue(
                "Contract §3 lacks expected HTTP method(s) $expectedMethods for $normalizedClientPath (found $matchingContractMethods)",
                expectedMethods.all { it in matchingContractMethods },
            )
        }

        // Also assert §2 table and §3 headings remain consistent on core routes
        val section2Pairs = contractRoutes.filter { it.source == "section_2" }
            .map { it.method to it.normalizedPath }
            .toSet()
        val section3Pairs = section3Routes.map { it.method to it.normalizedPath }.toSet()
        // §2 lists POST /plans/{param}/order which intentionally has no §3 section (excluded by ADR 0014 D5)
        assertTrue(section2Pairs.contains("POST" to "/plans/{param}/order"))
        assertFalse(section3Pairs.contains("POST" to "/plans/{param}/order"))
    }

    // -- Task C: Request DTO-to-schema field conformance --

    @Test
    fun `C - every request DTO matches its frozen JSON schema and carries idempotencyKey where contract requires it`() {
        val requestMappings: List<RequestDtoExpectation> = listOf(
            RequestDtoExpectation("publish", "publish.schema.json", PublishRequestDto.serializer().descriptor, requiresIdempotencyKey = true),
            RequestDtoExpectation("upload", "upload.schema.json", UploadRequestDto.serializer().descriptor, requiresIdempotencyKey = true),
            RequestDtoExpectation("reconcile", "reconcile.schema.json", ReconcileRequestDto.serializer().descriptor, requiresIdempotencyKey = true),
            RequestDtoExpectation("halt", "halt.schema.json", EngineScopeRequestDto.serializer().descriptor, requiresIdempotencyKey = true),
            RequestDtoExpectation("resume", "resume.schema.json", EngineScopeRequestDto.serializer().descriptor, requiresIdempotencyKey = true),
            RequestDtoExpectation("decide", "decide.schema.json", DecideRequestDto.serializer().descriptor, requiresIdempotencyKey = true),
            RequestDtoExpectation("plan-status-transition", "plan-status-transition.schema.json", PlanStatusTransitionRequestDto.serializer().descriptor, requiresIdempotencyKey = true),
            RequestDtoExpectation("enroll", "enroll.schema.json", EnrollRequestDto.serializer().descriptor, requiresIdempotencyKey = false),
            RequestDtoExpectation("enroll-rotate", "enroll-rotate.schema.json", RotateKeyRequestDto.serializer().descriptor, requiresIdempotencyKey = false),
            RequestDtoExpectation("admin-revoke", "admin-revoke.schema.json", RevokeKeyRequestDto.serializer().descriptor, requiresIdempotencyKey = false),
            RequestDtoExpectation("sync", "sync.schema.json", SyncRequestDto.serializer().descriptor, requiresIdempotencyKey = false),
        )

        for (spec in requestMappings) {
            val schemaObj = ContractSchemaSupport.readSchemaObject(repoRoot, spec.schemaFile)
            val requestSchema = ContractSchemaSupport.extractSchemaSection(schemaObj, definitionName = "request")
            val dtoFields = ContractSchemaSupport.descriptorElementNames(spec.descriptor)

            assertEquals(
                "Request DTO for ${spec.name} (${spec.descriptor.serialName}) properties differ from ${spec.schemaFile}",
                requestSchema.properties,
                dtoFields,
            )
            assertTrue(
                "Request DTO for ${spec.name} misses schema-required fields: ${requestSchema.required - dtoFields}",
                dtoFields.containsAll(requestSchema.required),
            )
            if (spec.requiresIdempotencyKey) {
                assertTrue(
                    "Contract §1.6 requires 'idempotencyKey' on ${spec.name} request DTO and schema",
                    "idempotencyKey" in dtoFields && "idempotencyKey" in requestSchema.required,
                )
            } else {
                assertFalse(
                    "Non-journalled/read endpoint ${spec.name} does not require 'idempotencyKey' in schema",
                    "idempotencyKey" in dtoFields,
                )
            }
        }
    }

    // -- Task C: Response DTO-to-schema field conformance + known ratchet gaps --

    @Test
    fun `C - response DTOs do not require unpromised fields and match known ratchet gaps`() {
        val responseSpecs = listOf(
            ResponseDtoExpectation("publish-ack", "publish.schema.json", "response", GatewayAckDto.serializer().descriptor),
            ResponseDtoExpectation("upload-ack", "upload.schema.json", "response", GatewayAckDto.serializer().descriptor),
            ResponseDtoExpectation("reconcile-ack", "reconcile.schema.json", "response", GatewayAckDto.serializer().descriptor),
            ResponseDtoExpectation("halt-ack", "halt.schema.json", "response", GatewayAckDto.serializer().descriptor),
            ResponseDtoExpectation("resume-ack", "resume.schema.json", "response", GatewayAckDto.serializer().descriptor),
            ResponseDtoExpectation("decide-ack", "decide.schema.json", "response", DecideAckDto.serializer().descriptor),
            ResponseDtoExpectation("plan-status-transition-ack", "plan-status-transition.schema.json", "response", PlanStatusTransitionAckDto.serializer().descriptor),
            ResponseDtoExpectation("operation-lookup", "operation-lookup.schema.json", null, OperationLookupDto.serializer().descriptor),
            ResponseDtoExpectation("branch-head", "branch-head.schema.json", null, BranchHeadDto.serializer().descriptor),
            ResponseDtoExpectation("tasks-next", "tasks-next.schema.json", null, TaskSummaryDto.serializer().descriptor),
            ResponseDtoExpectation("plan-status", "plan-status.schema.json", null, PlanStatusDto.serializer().descriptor),
            ResponseDtoExpectation("plan-render-check", "plan-render-check.schema.json", null, RenderCheckDto.serializer().descriptor),
            ResponseDtoExpectation("plan-validate", "plan-validate.schema.json", "response", PlanValidationDto.serializer().descriptor),
            ResponseDtoExpectation("plans-list", "plans-list.schema.json", null, PlanSummaryDto.serializer().descriptor),
            ResponseDtoExpectation("doctor", "doctor.schema.json", null, DoctorReportDto.serializer().descriptor),
            ResponseDtoExpectation("health", "health.schema.json", null, GatewayHealthDto.serializer().descriptor),
            ResponseDtoExpectation("error", "error.schema.json", null, GatewayErrorDto.serializer().descriptor),
            ResponseDtoExpectation("sync-status", "sync.schema.json", "response", SyncStatusDto.serializer().descriptor),
            ResponseDtoExpectation("enroll-challenge", "enroll-challenge.schema.json", null, EnrollChallengeDto.serializer().descriptor),
            ResponseDtoExpectation("enroll-response", "enroll.schema.json", "response", EnrollResponseDto.serializer().descriptor),
            ResponseDtoExpectation("enroll-rotate-response", "enroll-rotate.schema.json", "response", EnrollResponseDto.serializer().descriptor),
            ResponseDtoExpectation("admin-revoke-response", "admin-revoke.schema.json", "response", RevokeKeyResponseDto.serializer().descriptor),
        )

        val actualDiscrepancies = linkedMapOf<String, String>()

        for (spec in responseSpecs) {
            val schemaObj = ContractSchemaSupport.readSchemaObject(repoRoot, spec.schemaFile)
            val responseSchema = ContractSchemaSupport.extractSchemaSection(schemaObj, spec.definitionName)
            val dtoElements = ContractSchemaSupport.descriptorElementNames(spec.descriptor)
            val requiredDtoElements = ContractSchemaSupport.descriptorRequiredElementNames(spec.descriptor)

            // Every DTO element WITHOUT a default value must exist in the schema's properties.
            val unpromisedRequired = requiredDtoElements - responseSchema.properties
            assertTrue(
                "${spec.name}: DTO has non-default fields not promised by ${spec.schemaFile}: $unpromisedRequired",
                unpromisedRequired.isEmpty(),
            )

            // Record DTO fields that do not exist in this endpoint's schema properties (even if defaulted).
            val extraDtoFields = dtoElements - responseSchema.properties
            // `GatewayAckDto.outcome` is intentionally shared across ack endpoints (populated on reconcile,
            // defaulted "" on publish/upload/halt/resume) and is not a defect; other unknown fields are flagged.
            val nonSharedExtra = if (spec.descriptor == GatewayAckDto.serializer().descriptor) {
                extraDtoFields - setOf("outcome")
            } else {
                extraDtoFields
            }
            if (nonSharedExtra.isNotEmpty()) {
                actualDiscrepancies["response_extra_field:${spec.name}:${nonSharedExtra.sorted().joinToString(",")}"] =
                    "${spec.descriptor.serialName} declares fields not in ${spec.schemaFile} response schema: $nonSharedExtra"
            }

            // Record missing schema-required fields if a DTO uses a mismatched field name (e.g. rotatedAt vs enrolledAt)
            val missingRequiredFromDto = responseSchema.required - dtoElements
            if (missingRequiredFromDto.isNotEmpty()) {
                actualDiscrepancies["response_missing_required:${spec.name}:${missingRequiredFromDto.sorted().joinToString(",")}"] =
                    "${spec.descriptor.serialName} omits schema-required fields from ${spec.schemaFile}: $missingRequiredFromDto"
            }
        }

        // Also validate nested DoctorCheckDto against doctor.schema.json properties.checks.items
        val doctorSchema = ContractSchemaSupport.readSchemaObject(repoRoot, "doctor.schema.json")
        val checkItemSchema = ContractSchemaSupport.extractNestedArrayItemSection(doctorSchema, "checks")
        val checkDtoElements = ContractSchemaSupport.descriptorElementNames(DoctorCheckDto.serializer().descriptor)
        val checkRequired = ContractSchemaSupport.descriptorRequiredElementNames(DoctorCheckDto.serializer().descriptor)
        assertTrue((checkRequired - checkItemSchema.properties).isEmpty())
        assertEquals(checkItemSchema.properties, checkDtoElements)

        // Contract revision 1.2 makes `order` required on the GET /tasks/next item (ADR 0014 D1, ADR 0015).
        // The generic required-field check above reports it until TaskSummaryDto consumes it; see the baseline.

        ContractSchemaSupport.verifyRatchet(
            checkName = "Response DTO / schema conformance",
            actualDiscrepancies = actualDiscrepancies,
            baseline = RESPONSE_RATCHET_BASELINE,
        )
    }

    // -- Contract §4.1 Error Registry vs ErrorMapper ratchet verification --

    @Test
    fun `A E - contract error registry parity against ErrorMapper GATEWAY_CODES matches ratchet baseline`() {
        val contractCodes = ContractSchemaSupport.parseContractErrorCodes(contractText)
        assertTrue("Expected contract §4.1 error registry to contain >= 40 codes", contractCodes.size >= 40)

        val mapperCodes = ErrorMapper.GATEWAY_CODES
        val actualDiscrepancies = linkedMapOf<String, String>()

        for (missingInMapper in (contractCodes - mapperCodes).sorted()) {
            actualDiscrepancies["errormap_missing:$missingInMapper"] =
                "Contract §4.1 code '$missingInMapper' is not present in ErrorMapper.GATEWAY_CODES"
        }
        for (extraInMapper in (mapperCodes - contractCodes).sorted()) {
            actualDiscrepancies["errormap_extra:$extraInMapper"] =
                "ErrorMapper.GATEWAY_CODES declares '$extraInMapper' which is absent from contract §4.1"
        }

        ContractSchemaSupport.verifyRatchet(
            checkName = "ErrorMapper vs Contract §4.1 registry",
            actualDiscrepancies = actualDiscrepancies,
            baseline = ERROR_CODE_RATCHET_BASELINE,
        )
    }

    // -- Task E(1): Unknown error code fails closed in ErrorMapper --

    @Test
    fun `E1 - unknown gateway error code maps to fail-closed UNKNOWN failure state in ErrorMapper`() {
        val mapper = ErrorMapper()

        val state = mapper.stateFor("UNRECOGNIZED_FUTURE_GATEWAY_CODE", 409)
        assertEquals(GatewayFailureState.UNKNOWN, state)

        val mapped = mapper.map(
            statusCode = 409,
            body = """{"code":"UNRECOGNIZED_FUTURE_GATEWAY_CODE","message":"Refused by future guard","request_id":"req-123"}""",
        )
        assertEquals(GatewayFailureState.UNKNOWN.code, mapped.code)
        assertTrue(mapped.message.startsWith("UNRECOGNIZED_FUTURE_GATEWAY_CODE:"))
        assertTrue(mapped.message.contains("Refused by future guard"))
    }

    // -- Task E(2): Malformed or empty response bodies rejected rather than defaulted to success --

    @Test
    fun `E2 - malformed or empty lookup and ack bodies fail closed via DTO deserialization and GatewayRepository`() = runTest {
        val strictNoDefaults = Json

        // Direct serializer checks on empty / truncated / wrong-type JSON for lookup and ack DTOs
        for (badBody in listOf("", "   ", "{not-json", "[]", "null")) {
            assertThrowsSerialization<GatewayAckDto>(strictNoDefaults, badBody, "GatewayAckDto")
            assertThrowsSerialization<DecideAckDto>(strictNoDefaults, badBody, "DecideAckDto")
            assertThrowsSerialization<PlanStatusTransitionAckDto>(strictNoDefaults, badBody, "PlanStatusTransitionAckDto")
            assertThrowsSerialization<OperationLookupDto>(strictNoDefaults, badBody, "OperationLookupDto")
        }

        // Empty JSON object `{}` never defaults to accepted=true or completed status
        val emptyAck = strictNoDefaults.decodeFromString<GatewayAckDto>("{}")
        assertFalse(emptyAck.accepted)
        assertEquals("", emptyAck.auditRef)
        assertEquals("", emptyAck.headSha)

        val emptyDecideAck = strictNoDefaults.decodeFromString<DecideAckDto>("{}")
        assertFalse(emptyDecideAck.accepted)
        assertEquals("", emptyDecideAck.auditRef)

        val emptyTransitionAck = strictNoDefaults.decodeFromString<PlanStatusTransitionAckDto>("{}")
        assertFalse(emptyTransitionAck.accepted)
        assertEquals("", emptyTransitionAck.auditRef)

        val emptyLookup = strictNoDefaults.decodeFromString<OperationLookupDto>("{}")
        assertEquals("", emptyLookup.status)
        assertEquals("", emptyLookup.id)

        // End-to-end GatewayRepository HTTP decoder rejects empty and malformed 200 OK bodies with GatewayProtocolException
        val server = MockWebServer()
        server.start()
        val http = HttpClient(OkHttp) { expectSuccess = false }
        try {
            val repo = GatewayRepository(
                httpClient = GatewayHttpClient(http, baseUrlProvider = { server.url("/").toString() }),
                errorMapper = ErrorMapper(),
            )
            for (badWireBody in listOf("", "{malformed", "[]")) {
                server.enqueue(MockResponse().setResponseCode(200).setBody(badWireBody))
                val lookupErr = runCatching {
                    repo.lookupOperation("3b1f0c7a9e5d4a28b6c1d0e9f8a7b6c5")
                }.exceptionOrNull()
                assertTrue(
                    "Expected GatewayProtocolException for lookup body '$badWireBody', got $lookupErr",
                    lookupErr is GatewayProtocolException,
                )

                server.enqueue(MockResponse().setResponseCode(200).setBody(badWireBody))
                val ackErr = runCatching {
                    repo.publish(
                        PublishRequestDto(
                            plan = "atlas",
                            task = "10008",
                            branch = "task-10008-refactor-parser",
                            head = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
                            capability = "ctxop-v1\n...",
                            idempotencyKey = "3b1f0c7a9e5d4a28b6c1d0e9f8a7b6c5",
                        ),
                    )
                }.exceptionOrNull()
                assertTrue(
                    "Expected GatewayProtocolException for publish ack body '$badWireBody', got $ackErr",
                    ackErr is GatewayProtocolException,
                )
            }
        } finally {
            http.close()
            server.shutdown()
        }
    }

    // -- Task E(3): Explicit test pinning CURRENT ignoreUnknownKeys=true response tolerance --

    @Test
    fun `E3_OPERATOR_DECISION_CURRENT_BEHAVIOR_GatewayRepository_tolerates_unknown_response_fields`() = runTest {
        // NOTE FOR FUTURE DECISION (strict vs tolerant response bodies):
        // GatewayRepository currently configures `Json { ignoreUnknownKeys = true }` per §1.5 extensibility,
        // so unexpected fields on 200 OK response JSON are silently ignored rather than rejected.
        // Changing GatewayRepository to strict decoding (`ignoreUnknownKeys = false`) will intentionally
        // cause this test to fail and must be coordinated as an explicit operator decision.
        val server = MockWebServer()
        server.start()
        val http = HttpClient(OkHttp) { expectSuccess = false }
        try {
            val repo = GatewayRepository(
                httpClient = GatewayHttpClient(http, baseUrlProvider = { server.url("/").toString() }),
                errorMapper = ErrorMapper(),
            )
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "accepted": true,
                      "operation": "PUBLISH",
                      "branch": "task-10008-refactor-parser",
                      "headSha": "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
                      "auditRef": "audit-20261030-98234",
                      "unexpectedFutureField": "should_be_ignored_under_current_config"
                    }
                    """.trimIndent(),
                ),
            )
            val ack = repo.publish(
                PublishRequestDto(
                    plan = "atlas",
                    task = "10008",
                    branch = "task-10008-refactor-parser",
                    head = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
                    capability = "ctxop-v1\n...",
                    idempotencyKey = "3b1f0c7a9e5d4a28b6c1d0e9f8a7b6c5",
                ),
            )
            assertTrue(ack.accepted)
            assertEquals("audit-20261030-98234", ack.auditRef)
        } finally {
            http.close()
            server.shutdown()
        }
    }

    private inline fun <reified T> assertThrowsSerialization(json: Json, payload: String, label: String) {
        val err = runCatching { json.decodeFromString<T>(payload) }.exceptionOrNull()
        assertTrue(
            "Expected SerializationException decoding malformed $label payload '$payload', got $err",
            err is SerializationException || err is IllegalArgumentException,
        )
    }

    private data class RequestDtoExpectation(
        val name: String,
        val schemaFile: String,
        val descriptor: SerialDescriptor,
        val requiresIdempotencyKey: Boolean,
    )

    private data class ResponseDtoExpectation(
        val name: String,
        val schemaFile: String,
        val definitionName: String?,
        val descriptor: SerialDescriptor,
    )

    private companion object {
        val RESPONSE_RATCHET_BASELINE: List<ContractSchemaSupport.RatchetEntry> = listOf(
            ContractSchemaSupport.RatchetEntry(
                id = "response_extra_field:enroll-rotate-response:enrolledAt",
                justification = "GatewayApi.rotateKey reuses EnrollResponseDto (enrolledAt) instead of rotatedAt declared in enroll-rotate.schema.json; finding pending RotateKeyResponseDto fix",
                reference = "PR3-003, contract §3.16, enroll-rotate.schema.json",
            ),
            ContractSchemaSupport.RatchetEntry(
                id = "response_missing_required:enroll-rotate-response:rotatedAt",
                justification = "EnrollResponseDto omits rotatedAt required by enroll-rotate.schema.json definitions.response, causing RotateDeviceKeyUseCase response.enrolledAt to read 0L",
                reference = "PR3-003, contract §3.16, enroll-rotate.schema.json",
            ),
        )

        val ERROR_CODE_RATCHET_BASELINE: List<ContractSchemaSupport.RatchetEntry> = emptyList()
    }
}

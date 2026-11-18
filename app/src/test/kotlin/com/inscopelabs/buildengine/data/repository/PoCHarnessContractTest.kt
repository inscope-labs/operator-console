package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.capability.NonceSource
import com.inscopelabs.buildengine.capability.codec.OperatorCapabilityCodec
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.capability.signing.HmacCapabilitySigner
import com.inscopelabs.buildengine.capability.signing.SignedCapability
import com.inscopelabs.buildengine.contract.ContractSchemaSupport
import com.inscopelabs.buildengine.core.crypto.Base64Url
import com.inscopelabs.buildengine.core.crypto.EcdsaEncoding
import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.GatewayHttpClient
import com.inscopelabs.buildengine.core.security.BiometricGate
import com.inscopelabs.buildengine.core.time.FixedClock
import com.inscopelabs.buildengine.data.local.IdempotencyKeyGenerator
import com.inscopelabs.buildengine.data.remote.GatewayRepository
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.TaskStatus
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase
import com.inscopelabs.buildengine.domain.usecase.common.VerifyBranchHeadUseCase
import com.inscopelabs.buildengine.domain.usecase.write.PublishResult
import com.inscopelabs.buildengine.domain.usecase.write.PublishTarget
import com.inscopelabs.buildengine.domain.usecase.write.PublishTaskUseCase
import com.inscopelabs.buildengine.domain.usecase.write.SetTaskStatusResult
import com.inscopelabs.buildengine.domain.usecase.write.SetTaskStatusTarget
import com.inscopelabs.buildengine.domain.usecase.write.SetTaskStatusUseCase
import com.inscopelabs.buildengine.testutil.InMemoryOperationJournalDao
import com.inscopelabs.buildengine.testutil.RecordingFakeJournalCipher
import com.inscopelabs.buildengine.testutil.VectorLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PoC deterministic contract integration harness (PoC Task 38 / ADR 0016 D-2).
 *
 * Drives `SET_IN_PROGRESS` and `PUBLISH` through the real client stack (use cases ->
 * [ConfirmAndMintCapabilityUseCase] -> [CapabilityMinter] -> [OperatorCapabilityCodec] ->
 * [OperationRepositoryImpl] -> [GatewayRepository] -> [GatewayHttpClient] -> [MockWebServer]),
 * using recorded fixtures in `docs/fixtures/vectors/` and checking every wire request against
 * `docs/schemas/publish.schema.json` and `docs/schemas/plan-status-transition.schema.json`.
 */
class PoCHarnessContractTest {

    private val json = Json { ignoreUnknownKeys = false }
    private lateinit var server: MockWebServer
    private lateinit var httpClient: HttpClient
    private lateinit var gatewayRepository: GatewayRepository
    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var planRepository: PlanRepositoryImpl
    private lateinit var journalDao: InMemoryOperationJournalDao
    private lateinit var cipher: RecordingFakeJournalCipher
    private lateinit var operationRepository: OperationRepositoryImpl

    private val repoRoot by lazy { ContractSchemaSupport.resolveRepoRoot() }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        httpClient = HttpClient(OkHttp) { expectSuccess = false }
        gatewayRepository = GatewayRepository(
            httpClient = GatewayHttpClient(httpClient, baseUrlProvider = { server.url("/").toString() }),
            errorMapper = ErrorMapper(),
        )
        taskRepository = TaskRepositoryImpl(gatewayRepository, ErrorMapper())
        planRepository = PlanRepositoryImpl(gatewayRepository, ErrorMapper())
        journalDao = InMemoryOperationJournalDao()
        cipher = RecordingFakeJournalCipher()
        operationRepository = OperationRepositoryImpl(
            gateway = gatewayRepository,
            journalDao = journalDao,
            cipher = cipher,
            keyGenerator = IdempotencyKeyGenerator(SecureRandomSource()),
            clock = FixedClock(1761840000L - 300L),
        )
    }

    @After
    fun tearDown() {
        httpClient.close()
        server.shutdown()
    }

    @Test
    fun `SET_IN_PROGRESS executes end-to-end against recorded vector and conforms to plan-status-transition schema`() = runTest {
        val vector = VectorLoader.vectors("valid_capabilities.json")
            .first { it.getValue("id").jsonPrimitive.content == "valid-set-in-progress" }
        val fields = vector.getValue("fields").jsonObject
        val expectedCap = OperatorCapabilityCodec.decode(vector.getValue("canonicalMessage").jsonPrimitive.content)

        val clock = FixedClock(fields.getValue("exp").jsonPrimitive.long - 300L)
        val vectorNonce = fields.getValue("nonce").jsonPrimitive.content
        val fixedNonce = object : NonceSource {
            override fun newNonce(): String = vectorNonce
        }
        val minter = CapabilityMinter(
            signer = HmacCapabilitySigner(
                keyId = fields.getValue("kid").jsonPrimitive.content,
                secretUtf8 = "lab-secret-key-32-chars-minimum!!",
            ),
            nonceSource = fixedNonce,
            clock = clock,
        )

        val requestBodies = mutableListOf<String>()
        var nextTasksStatus = "pending"
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.method == "GET" && request.path == "/branches/${expectedCap.branch}/head" ->
                    MockResponse().setResponseCode(200).setBody(
                        """{"branch":"${expectedCap.branch}","headSha":"${expectedCap.head}"}""",
                    )
                request.method == "POST" && request.path == "/plans/${expectedCap.plan}/status" -> {
                    requestBodies += request.body.readUtf8()
                    nextTasksStatus = "in_progress"
                    MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "accepted": true,
                          "operation": "SET_IN_PROGRESS",
                          "plan": "${expectedCap.plan}",
                          "task": "${expectedCap.task}",
                          "newStatus": "in_progress",
                          "headSha": "${expectedCap.head}",
                          "auditRef": "audit-poc-set-in-progress-1"
                        }
                        """.trimIndent(),
                    )
                }
                request.method == "GET" && request.path == "/tasks/next" ->
                    MockResponse().setResponseCode(200).setBody(
                        """
                        [
                          {
                            "id": "${expectedCap.task}",
                            "planId": "${expectedCap.plan}",
                            "title": "Refactor parser",
                            "status": "$nextTasksStatus",
                            "branch": "${expectedCap.branch}",
                            "headSha": "${expectedCap.head}",
                            "order": "${expectedCap.order}"
                          }
                        ]
                        """.trimIndent(),
                    )
                request.method == "GET" && request.path == "/plans/${expectedCap.plan}/status" ->
                    MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "id": "${expectedCap.plan}",
                          "status": "in_progress",
                          "halted": false,
                          "openBranches": ["${expectedCap.branch}"],
                          "blockedTasks": [],
                          "currentMilestone": "M3"
                        }
                        """.trimIndent(),
                    )
                else -> MockResponse().setResponseCode(404).setBody("""{"errorCode":"NOT_FOUND"}""")
            }
        }

        val useCase = SetTaskStatusUseCase(
            operationRepository = operationRepository,
            taskRepository = taskRepository,
            confirmAndMintCapability = ConfirmAndMintCapabilityUseCase(
                capabilityMinter = minter,
                biometricGate = BiometricGate { _, _ -> true },
                verifyBranchHeadUseCase = VerifyBranchHeadUseCase(taskRepository),
                clock = clock,
            ),
            nonceSource = fixedNonce,
            planRepository = planRepository,
        )

        val result = useCase(
            SetTaskStatusTarget(
                taskId = expectedCap.task,
                plan = expectedCap.plan,
                order = expectedCap.order,
                branch = expectedCap.branch,
                headSha = expectedCap.head,
                currentStatus = TaskStatus.PENDING,
                targetStatus = TaskStatus.IN_PROGRESS,
            ),
        )

        assertTrue("Expected Executed, got $result", result is SetTaskStatusResult.Executed)
        val executed = result as SetTaskStatusResult.Executed
        assertEquals(
            OperationOutcome.Succeeded(
                auditRef = "audit-poc-set-in-progress-1",
                headSha = expectedCap.head,
                outcome = "in_progress",
            ),
            executed.outcome,
        )
        assertEquals(TaskStatus.IN_PROGRESS, executed.authoritativeTask?.status)
        assertEquals(expectedCap.plan, executed.authoritativePlanStatus?.id)

        val payload = json.parseToJsonElement(requestBodies.single()).jsonObject
        assertRequestConformsToSchema("plan-status-transition.schema.json", payload)
        assertEquals("in_progress", payload.getValue("targetStatus").jsonPrimitive.content)
        assertTrue(Regex("^[0-9a-f]{32}$").matches(payload.getValue("idempotencyKey").jsonPrimitive.content))

        val wireCap = payload.getValue("capability").jsonPrimitive.content
        val wireMessage = wireCap.substringBeforeLast("\n")
        assertEquals(vector.getValue("canonicalMessage").jsonPrimitive.content, wireMessage)
        assertFalse(cipher.recordedPlaintexts.any { it.contains(vectorNonce) || it.contains(wireCap) })
    }

    @Test
    fun `PUBLISH executes end-to-end against recorded vector, HMAC vector signature, and publish schema`() = runTest {
        val vector = VectorLoader.vectors("valid_capabilities.json")
            .first { it.getValue("id").jsonPrimitive.content == "valid-publish" }
        val hmacVector = VectorLoader.vectors("hmac_vectors.json")
            .first { it.getValue("id").jsonPrimitive.content == "hmac-sha256-publish" }
        val fields = vector.getValue("fields").jsonObject
        val expectedCap = OperatorCapabilityCodec.decode(vector.getValue("canonicalMessage").jsonPrimitive.content)

        val clock = FixedClock(fields.getValue("exp").jsonPrimitive.long - 300L)
        val vectorNonce = fields.getValue("nonce").jsonPrimitive.content
        val fixedNonce = object : NonceSource {
            override fun newNonce(): String = vectorNonce
        }
        val minter = CapabilityMinter(
            signer = HmacCapabilitySigner(
                keyId = fields.getValue("kid").jsonPrimitive.content,
                secretUtf8 = hmacVector.getValue("secretUtf8").jsonPrimitive.content,
            ),
            nonceSource = fixedNonce,
            clock = clock,
        )

        val requestBodies = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.method == "GET" && request.path == "/branches/${expectedCap.branch}/head" ->
                    MockResponse().setResponseCode(200).setBody(
                        """{"branch":"${expectedCap.branch}","headSha":"${expectedCap.head}"}""",
                    )
                request.method == "POST" && request.path == "/tasks/${expectedCap.task}/publish" -> {
                    requestBodies += request.body.readUtf8()
                    MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "accepted": true,
                          "operation": "PUBLISH",
                          "branch": "${expectedCap.branch}",
                          "headSha": "${expectedCap.head}",
                          "auditRef": "audit-poc-publish-1"
                        }
                        """.trimIndent(),
                    )
                }
                else -> MockResponse().setResponseCode(404).setBody("""{"errorCode":"NOT_FOUND"}""")
            }
        }

        val publishUseCase = PublishTaskUseCase(
            operationRepository = operationRepository,
            confirmAndMintCapability = ConfirmAndMintCapabilityUseCase(
                capabilityMinter = minter,
                biometricGate = BiometricGate { _, _ -> true },
                verifyBranchHeadUseCase = VerifyBranchHeadUseCase(taskRepository),
                clock = clock,
            ),
            nonceSource = fixedNonce,
        )

        val result = publishUseCase(
            PublishTarget(
                taskId = expectedCap.task,
                plan = expectedCap.plan,
                order = expectedCap.order,
                branch = expectedCap.branch,
                headSha = expectedCap.head,
            ),
        )

        assertTrue("Expected PublishResult.Executed, got $result", result is PublishResult.Executed)
        val outcome = (result as PublishResult.Executed).outcome
        assertEquals(OperationOutcome.Succeeded(auditRef = "audit-poc-publish-1", headSha = expectedCap.head), outcome)

        val payload = json.parseToJsonElement(requestBodies.single()).jsonObject
        assertRequestConformsToSchema("publish.schema.json", payload)

        val wireCap = payload.getValue("capability").jsonPrimitive.content
        val expectedSig = hmacVector.getValue("expectedSignatureBase64Url").jsonPrimitive.content
        val expectedWire = "${vector.getValue("canonicalMessage").jsonPrimitive.content}\n$expectedSig"
        assertEquals(expectedWire, wireCap)
        assertFalse(cipher.recordedPlaintexts.any { it.contains(expectedSig) || it.contains(vectorNonce) })
    }

    @Test
    fun `SET_DONE historical vector is refused locally under ADR 0016 D-1 with zero network calls`() = runTest {
        val setDoneVector = VectorLoader.vectors("valid_capabilities.json")
            .first { it.getValue("id").jsonPrimitive.content == "valid-set-done" }
        val fields = setDoneVector.getValue("fields").jsonObject
        val clock = FixedClock(1700000000L)
        val nonce = fields.getValue("nonce").jsonPrimitive.content
        val nonceSource = object : NonceSource {
            override fun newNonce(): String = nonce
        }
        var prompts = 0
        val useCase = SetTaskStatusUseCase(
            operationRepository = operationRepository,
            taskRepository = taskRepository,
            confirmAndMintCapability = ConfirmAndMintCapabilityUseCase(
                capabilityMinter = CapabilityMinter(
                    signer = HmacCapabilitySigner("k2", "lab-secret-key-32-chars-minimum!!"),
                    nonceSource = nonceSource,
                    clock = clock,
                ),
                biometricGate = BiometricGate { _, _ ->
                    prompts++
                    true
                },
                verifyBranchHeadUseCase = VerifyBranchHeadUseCase(taskRepository),
                clock = clock,
            ),
            nonceSource = nonceSource,
            planRepository = planRepository,
        )

        val result = useCase(
            SetTaskStatusTarget(
                taskId = fields.getValue("task").jsonPrimitive.content,
                plan = fields.getValue("plan").jsonPrimitive.content,
                order = fields.getValue("order").jsonPrimitive.content,
                branch = fields.getValue("branch").jsonPrimitive.content,
                headSha = fields.getValue("head").jsonPrimitive.content,
                currentStatus = TaskStatus.IN_PROGRESS,
                targetStatus = TaskStatus.DONE,
            ),
        )

        assertTrue(result is SetTaskStatusResult.UnsupportedByContract)
        assertEquals(0, prompts)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `ECDSA P-256 vector wire capability encodes canonically and verifies against publicKeyJwk`() {
        val root = VectorLoader.root("ecdsa_vectors.json")
        val jwk = root.getValue("publicKeyJwk").jsonObject
        val verifyVec = VectorLoader.vectors("ecdsa_vectors.json")
            .first { it.getValue("id").jsonPrimitive.content == "ecdsa-publish-verifies" }
        val message = verifyVec.getValue("message").jsonPrimitive.content
        val rawSigB64Url = verifyVec.getValue("expectedRawP1363Base64Url").jsonPrimitive.content
        val rawSig = Base64Url.decode(rawSigB64Url)
        val derSig = Base64Url.decode(
            verifyVec.getValue("derSignatureBase64").jsonPrimitive.content
                .replace('+', '-')
                .replace('/', '_')
                .trimEnd('='),
        )
        assertTrue(EcdsaEncoding.derToP1363(derSig).contentEquals(rawSig))

        val capability = OperatorCapabilityCodec.decode(message)
        val signed = SignedCapability(capability, rawSig)
        assertEquals(OperationType.PUBLISH, signed.capability.op)
        assertEquals("$message\n$rawSigB64Url", signed.toWire())

        // Verify using JWK public key
        val x = BigInteger(1, Base64Url.decode(jwk.getValue("x").jsonPrimitive.content))
        val y = BigInteger(1, Base64Url.decode(jwk.getValue("y").jsonPrimitive.content))
        val params = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }
        val ecSpec = params.getParameterSpec(ECParameterSpec::class.java)
        val pubKey = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), ecSpec))
        val verifier = Signature.getInstance("SHA256withECDSA").apply {
            initVerify(pubKey)
            update(message.toByteArray(Charsets.UTF_8))
        }
        assertTrue("ECDSA vector signature must verify under JWK public key", verifier.verify(derSig))
    }

    private fun assertRequestConformsToSchema(schemaFile: String, payload: JsonObject) {
        val schemaObj = ContractSchemaSupport.readSchemaObject(repoRoot, schemaFile)
        val section = ContractSchemaSupport.extractSchemaSection(schemaObj, "request")
        assertTrue(
            "$schemaFile request schema requires additionalProperties=false",
            section.additionalPropertiesFalse,
        )
        assertTrue(
            "Payload keys ${payload.keys} missing required schema keys ${section.required}",
            payload.keys.containsAll(section.required),
        )
        assertTrue(
            "Payload contains unknown keys not in $schemaFile: ${payload.keys - section.properties}",
            section.properties.containsAll(payload.keys),
        )
    }
}

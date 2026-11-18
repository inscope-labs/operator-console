package com.inscopelabs.buildengine.core.network

import com.inscopelabs.buildengine.contract.ContractSchemaSupport
import com.inscopelabs.buildengine.data.repository.OperationRepositoryImpl
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies exact parity between the console's gateway error code registry and the contract.
 *
 * `docs/schemas/error.schema.json` types `"code"` as `"string"` ("Authoritative machine-readable
 * error code") and does not embed an inline `enum`; the authoritative enumerated catalogue is
 * `docs/gateway-contract.md` §4.1 Error Code Registry.
 *
 * PoC task 14 / PR3-012 F3.
 */
class GatewayErrorCatalogueParityTest {

    private val repoRoot = ContractSchemaSupport.resolveRepoRoot()
    private val contractText = ContractSchemaSupport.readContractMarkdown(repoRoot)
    private val contractCodes = ContractSchemaSupport.parseContractErrorCodes(contractText)

    @Test
    fun `error schema defers code enumeration cleanly and contract registry matches ErrorMapper`() {
        val errorSchema = ContractSchemaSupport.readSchemaObject(repoRoot, "error.schema.json")
        val codeProperty = errorSchema["properties"]?.jsonObject?.get("code")?.jsonObject
        assertEquals("string", codeProperty?.get("type")?.jsonPrimitive?.content)

        val missingFromMapper = (contractCodes - ErrorMapper.GATEWAY_CODES).sorted()
        val extraInMapper = (ErrorMapper.GATEWAY_CODES - contractCodes).sorted()

        assertEquals(
            "ErrorMapper.GATEWAY_CODES is missing contract §4.1 codes: $missingFromMapper",
            emptyList<String>(),
            missingFromMapper,
        )
        assertEquals(
            "ErrorMapper.GATEWAY_CODES declares codes absent from contract §4.1: $extraInMapper",
            emptyList<String>(),
            extraInMapper,
        )
        assertEquals(contractCodes, ErrorMapper.GATEWAY_CODES)
    }

    @Test
    fun `OperationRepositoryImpl shares the single ErrorMapper gateway code source of truth`() {
        assertEquals(ErrorMapper.GATEWAY_CODES, OperationRepositoryImpl.KNOWN_GATEWAY_CODES)
        assertTrue(
            "All CONFLICT_CODES must belong to ErrorMapper.GATEWAY_CODES",
            ErrorMapper.GATEWAY_CODES.containsAll(OperationRepositoryImpl.CONFLICT_CODES),
        )
    }

    @Test
    fun `every contract error code maps to a non-UNKNOWN failure state in ErrorMapper`() {
        val mapper = ErrorMapper()
        for (code in contractCodes) {
            val state = mapper.stateFor(code)
            assertNotEquals(
                "Contract §4.1 code '$code' unexpectedly mapped to UNKNOWN",
                GatewayFailureState.UNKNOWN,
                state,
            )
        }
    }
}

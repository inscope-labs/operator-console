package com.inscopelabs.buildengine.contract

import java.io.File
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure JVM helpers for contract-to-client conformance verification (`PR3-003`).
 *
 * Uses only JUnit assertions (via tests), `java.io.File`, and `kotlinx.serialization`
 * JSON/descriptor APIs — no new test dependencies.
 */
object ContractSchemaSupport {

    private val json = Json { ignoreUnknownKeys = false }

    private val PATH_PARAM_REGEX = Regex("""\{[^}]+\}""")
    private val SECTION2_ROUTE_REGEX = Regex("""`\s*(GET|POST|PUT|DELETE|PATCH)\s+(/[^`\s]+)\s*`""")
    private val SECTION3_HEADING_REGEX = Regex("""^###\s+3\.\d+[a-z]?\s+(.+)$""")
    private val SECTION3_ROUTE_REGEX = Regex("""`(GET|POST|PUT|DELETE|PATCH)\s+(/[^`\s]+)`""")
    private val ENDPOINT_CONST_REGEX = Regex("""const\s+val\s+([A-Z0-9_]+)\s*:\s*String\s*=\s*"(/[^"]+)"""")
    private val ERROR_TABLE_ROW_REGEX = Regex("""^\|\s*(`[^|]+`)\s*\|\s*`?\d{3}\b""")
    private val BACKTICK_CODE_REGEX = Regex("""`([A-Z0-9_]+)`""")

    data class ContractRoute(
        val method: String,
        val rawPath: String,
        val normalizedPath: String,
        val source: String,
    )

    data class SchemaSection(
        val properties: Set<String>,
        val required: Set<String>,
        val additionalPropertiesFalse: Boolean,
    )

    data class RatchetEntry(
        val id: String,
        val justification: String,
        val reference: String,
    )

    fun resolveRepoRoot(): File {
        val candidates = listOf(
            File("."),
            File(".."),
            File("../.."),
        )
        return candidates.firstOrNull { dir ->
            File(dir, "docs/gateway-contract.md").isFile &&
                File(dir, "docs/schemas/index.json").isFile &&
                File(dir, "app/src/main/kotlin").isDirectory
        } ?: error("Could not locate repository root containing docs/gateway-contract.md from ${File(".").absolutePath}")
    }

    fun readContractMarkdown(repoRoot: File = resolveRepoRoot()): String =
        File(repoRoot, "docs/gateway-contract.md").readText()

    fun readSchemaIndex(repoRoot: File = resolveRepoRoot()): Map<String, String> {
        val indexObj = json.parseToJsonElement(File(repoRoot, "docs/schemas/index.json").readText()).jsonObject
        val schemas = indexObj["schemas"]!!.jsonObject
        return schemas.mapValues { (_, v) -> v.jsonPrimitive.content }
    }

    fun readSchemaObject(repoRoot: File, fileName: String): JsonObject {
        val cleanName = fileName.removePrefix("./")
        val file = File(repoRoot, "docs/schemas/$cleanName")
        require(file.isFile) { "Missing schema file: ${file.path}" }
        return json.parseToJsonElement(file.readText()).jsonObject
    }

    fun extractSchemaSection(schemaObj: JsonObject, definitionName: String? = null): SchemaSection {
        val targetObj: JsonObject = if (definitionName != null) {
            schemaObj["definitions"]?.jsonObject?.get(definitionName)?.jsonObject
                ?: error("Missing definitions.$definitionName in schema ${schemaObj["\$id"]}")
        } else if (schemaObj["type"]?.jsonPrimitive?.content == "array") {
            schemaObj["items"]?.jsonObject
                ?: error("Missing items object in array schema ${schemaObj["\$id"]}")
        } else {
            schemaObj
        }
        return parseObjectSchema(targetObj)
    }

    fun extractNestedArrayItemSection(schemaObj: JsonObject, propertyName: String): SchemaSection {
        val propObj = schemaObj["properties"]?.jsonObject?.get(propertyName)?.jsonObject
            ?: error("Missing properties.$propertyName in schema ${schemaObj["\$id"]}")
        val itemsObj = propObj["items"]?.jsonObject
            ?: error("Missing properties.$propertyName.items in schema ${schemaObj["\$id"]}")
        return parseObjectSchema(itemsObj)
    }

    private fun parseObjectSchema(obj: JsonObject): SchemaSection {
        val props = obj["properties"]?.jsonObject?.keys ?: emptySet()
        val req = (obj["required"] as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()
        val addlFalse = obj["additionalProperties"]?.jsonPrimitive?.content == "false"
        return SchemaSection(
            properties = props,
            required = req,
            additionalPropertiesFalse = addlFalse,
        )
    }

    fun descriptorElementNames(descriptor: SerialDescriptor): Set<String> =
        (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }.toSet()

    fun descriptorRequiredElementNames(descriptor: SerialDescriptor): Set<String> =
        (0 until descriptor.elementsCount)
            .filter { !descriptor.isElementOptional(it) }
            .map { descriptor.getElementName(it) }
            .toSet()

    fun normalizePath(rawPath: String): String =
        PATH_PARAM_REGEX.replace(rawPath.trim(), "{param}")

    /** Parses endpoints from §2 mapping table and §3 endpoint headings in `docs/gateway-contract.md`. */
    fun parseContractRoutes(contractText: String): List<ContractRoute> {
        val routes = mutableListOf<ContractRoute>()
        var inSection2 = false
        for (line in contractText.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("## 2.")) {
                inSection2 = true
                continue
            }
            if (inSection2 && trimmed.startsWith("## 3.")) {
                inSection2 = false
            }
            if (inSection2 && trimmed.startsWith("|")) {
                for (match in SECTION2_ROUTE_REGEX.findAll(trimmed)) {
                    val method = match.groupValues[1]
                    val path = match.groupValues[2]
                    routes += ContractRoute(
                        method = method,
                        rawPath = path,
                        normalizedPath = normalizePath(path),
                        source = "section_2",
                    )
                }
            }
            val headingMatch = SECTION3_HEADING_REGEX.matchEntire(trimmed)
            if (headingMatch != null) {
                for (match in SECTION3_ROUTE_REGEX.findAll(headingMatch.groupValues[1])) {
                    val method = match.groupValues[1]
                    val path = match.groupValues[2]
                    routes += ContractRoute(
                        method = method,
                        rawPath = path,
                        normalizedPath = normalizePath(path),
                        source = "section_3",
                    )
                }
            }
        }
        return routes
    }

    /** Parses route `const val` entries from `GatewayEndpoints.kt` (excluding `BASE_URL`). */
    fun parseGatewayEndpointConstants(repoRoot: File = resolveRepoRoot()): Map<String, String> {
        val file = File(repoRoot, "app/src/main/kotlin/com/inscopelabs/buildengine/core/network/GatewayEndpoints.kt")
        val content = file.readText()
        val result = linkedMapOf<String, String>()
        for (match in ENDPOINT_CONST_REGEX.findAll(content)) {
            result[match.groupValues[1]] = match.groupValues[2]
        }
        return result
    }

    /**
     * Parses `GatewayRepository.kt` `httpClient.get(...)` / `httpClient.post(...)` invocations and maps each
     * consumed `GatewayEndpoints.<fn>(...)` to its HTTP method family.
     */
    fun parseGatewayRepositoryMethods(repoRoot: File = resolveRepoRoot()): List<Pair<String, String>> {
        val file = File(repoRoot, "app/src/main/kotlin/com/inscopelabs/buildengine/data/remote/GatewayRepository.kt")
        val content = file.readText()
        val callRegex = Regex("""httpClient\.(get|post|put|delete|patch)\s*\(\s*(?:path\s*=\s*)?GatewayEndpoints\.([A-Za-z0-9_]+)\s*\(""")
        return callRegex.findAll(content).map {
            it.groupValues[1].uppercase() to it.groupValues[2]
        }.toList()
    }

    /** Parses every gateway error code listed in `docs/gateway-contract.md` §4.1 Error Code Registry. */
    fun parseContractErrorCodes(contractText: String): Set<String> {
        val codes = linkedSetOf<String>()
        var inErrorRegistry = false
        for (line in contractText.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("### 4.1 ")) {
                inErrorRegistry = true
                continue
            }
            if (inErrorRegistry && (trimmed.startsWith("### 4.2") || trimmed.startsWith("---"))) {
                if (codes.isNotEmpty()) break
            }
            if (inErrorRegistry && trimmed.startsWith("|")) {
                val rowMatch = ERROR_TABLE_ROW_REGEX.find(trimmed) ?: continue
                val firstCol = rowMatch.groupValues[1]
                for (codeMatch in BACKTICK_CODE_REGEX.findAll(firstCol)) {
                    codes += codeMatch.groupValues[1]
                }
            }
        }
        return codes
    }

    /**
     * Enforces ratchet baseline semantics:
     * - Fails on any NEW discrepancy ID not present in [baseline].
     * - Fails on any STALE [baseline] entry whose ID no longer appears in [actualDiscrepancies].
     */
    fun verifyRatchet(
        checkName: String,
        actualDiscrepancies: Map<String, String>,
        baseline: List<RatchetEntry>,
    ) {
        val baselineById = baseline.associateBy { it.id }
        val unexpected = actualDiscrepancies.keys.filter { it !in baselineById }
        val stale = baseline.filter { it.id !in actualDiscrepancies.keys }

        if (unexpected.isNotEmpty() || stale.isNotEmpty()) {
            val msg = buildString {
                appendLine("$checkName ratchet check failed:")
                if (unexpected.isNotEmpty()) {
                    appendLine("Unexpected NEW discrepancies (${unexpected.size}):")
                    unexpected.sorted().forEach { id ->
                        appendLine("  - $id: ${actualDiscrepancies[id]}")
                    }
                }
                if (stale.isNotEmpty()) {
                    appendLine("Stale ratchet baseline entries (${stale.size}); remove them so the baseline shrinks:")
                    stale.forEach { entry ->
                        appendLine("  - ${entry.id} (${entry.reference}): ${entry.justification}")
                    }
                }
            }
            throw AssertionError(msg)
        }
    }
}

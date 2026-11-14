package com.inscopelabs.buildengine.architecture

/**
 * Finding reported by [ArchitectureScanner] checks for `T3-601` / `PR3-033`.
 */
data class ScanFinding(
    val rule: String,
    val source: String,
    val line: Int,
    val matchText: String,
    val detail: String,
)

/**
 * Explicit ratchet-baseline exception for pre-existing findings in main sources.
 * Every exception must carry a one-line justification and reference; tests fail both
 * on any NEW unbaselined finding and on any STALE baseline entry that no longer hits.
 */
data class BaselineException(
    val rule: String,
    val sourceSuffix: String,
    val matchText: String,
    val justification: String,
    val reference: String,
)

/**
 * Pure string-scanning helpers backing `ArchitectureTest` (`T3-601` / `PR3-033`).
 *
 * Kept side-effect-free (no direct filesystem access) so both the real-tree tests in
 * `ArchitectureTest` and inline positive/negative fixtures in `ArchitectureScannerTest`
 * execute the exact same rules.
 */
object ArchitectureScanner {

    private val TOKEN_RULES: List<Pair<String, Regex>> = listOf(
        "github_classic_token" to Regex("""\bgh[pousr]_[A-Za-z0-9]{36,}"""),
        "github_fine_grained_pat" to Regex("""\bgithub_pat_[A-Za-z0-9_]{20,}"""),
        "aws_access_key_id" to Regex("""\bAKIA[0-9A-Z]{16}\b"""),
        "pem_private_key_header" to Regex("""-----BEGIN (?:[A-Z0-9 ]+ )?PRIVATE KEY-----"""),
    )

    private val SHELL_AND_GIT_RULES: List<Pair<String, Regex>> = listOf(
        "runtime_get_runtime" to Regex("""\bRuntime\.getRuntime\b"""),
        "process_exec_call" to Regex("""\.exec\s*\("""),
        "process_builder" to Regex("""\bProcessBuilder\b"""),
        "jgit_package" to Regex("""\borg\.eclipse\.jgit\b"""),
        "system_bin_sh" to Regex("""/system/bin/sh"""),
        "sh_minus_c" to Regex("""sh -c"""),
    )

    /**
     * Journalled privileged write methods on `GatewayApi` / `GatewayRepository`.
     *
     * Documented exclusion (NOT violations): enrollment / key-management calls
     * (`enroll`, `rotateKey`, `revokeKey`, `enrollChallenge`) from the enrollment use cases
     * are excluded because they manage device keys rather than journalled git/plan mutations.
     */
    val JOURNALLED_WRITE_METHODS: Set<String> = setOf(
        "publish",
        "upload",
        "reconcile",
        "halt",
        "resume",
        "decide",
        "transitionPlanStatus",
    )

    private val GATEWAY_WRITE_CALL_REGEX = Regex(
        """\b(?:gateway|gatewayApi)\.(publish|upload|reconcile|halt|resume|decide|transitionPlanStatus)\s*\(""",
    )

    private val FUN_DECL_REGEX = Regex(
        """^\s*(?:(?:override|private|internal|public|protected|suspend|inline)\s+)*fun\b""",
    )

    private val EXECUTE_OPERATION_CALL_REGEX = Regex("""\bexecuteOperation\s*\(""")

    private val PACKAGE_LAB_REGEX = Regex(
        """^\s*package\s+com\.inscopelabs\.buildengine\.lab\b""",
        RegexOption.MULTILINE,
    )

    private val LAB_IMPORT_OR_FQN_REGEX = Regex(
        """(?:^\s*import\s+com\.inscopelabs\.buildengine\.lab\.[^\s;]+|\bcom\.inscopelabs\.buildengine\.lab\.[A-Za-z_][A-Za-z0-9_]*)""",
    )

    private val HMAC_SIGNER_REGEX = Regex("""\bHmacCapabilitySigner\b""")

    private val LOGGER_CALL_START_REGEX = Regex("""\bLogger\.[A-Za-z_]+\s*\(""")

    private val SENSITIVE_LOG_EXPR_REGEX = Regex(
        """(?i)(nonce|signature|privateKey|bearer|credential|toWire\s*\()""",
    )

    private val ENTITY_ANNOTATION_REGEX = Regex("""@Entity\b""")

    private val CLASS_DECL_REGEX = Regex(
        """\b(?:data\s+)?class\s+([A-Za-z_][A-Za-z0-9_]*)""",
    )

    private val PROPERTY_DECL_REGEX = Regex(
        """(?:^|[,(\s;])(?:(?:private|internal|public|protected|override|const|lateinit)\s+)*(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\b""",
    )

    private val SENSITIVE_ENTITY_PROP_REGEX = Regex(
        """(?i)(nonce|signature|capability|capabilityWire|privateKey|secret|token|credential|bearer)""",
    )

    /** Rule A: token / credential / private-key header shapes. */
    fun scanTokenPatterns(source: String, content: String): List<ScanFinding> {
        val findings = mutableListOf<ScanFinding>()
        content.lineSequence().forEachIndexed { idx, line ->
            for ((ruleName, regex) in TOKEN_RULES) {
                for (match in regex.findAll(line)) {
                    findings += ScanFinding(
                        rule = "token_pattern:$ruleName",
                        source = source,
                        line = idx + 1,
                        matchText = match.value,
                        detail = "Forbidden credential/key shape ($ruleName) found in $source:${idx + 1}",
                    )
                }
            }
        }
        return findings
    }

    /** Rule B: shell or local Git execution patterns. */
    fun scanShellAndGitExecution(source: String, content: String): List<ScanFinding> {
        val findings = mutableListOf<ScanFinding>()
        content.lineSequence().forEachIndexed { idx, line ->
            for ((ruleName, regex) in SHELL_AND_GIT_RULES) {
                for (match in regex.findAll(line)) {
                    findings += ScanFinding(
                        rule = "shell_git:$ruleName",
                        source = source,
                        line = idx + 1,
                        matchText = match.value,
                        detail = "Forbidden shell/Git execution pattern ($ruleName) found in $source:${idx + 1}",
                    )
                }
            }
        }
        return findings
    }

    /**
     * Rule D: prevents privileged write calls bypassing the operation journal.
     *
     * Heuristic:
     * - Transport definitions (`GatewayApi.kt`, `GatewayRepository.kt`) are allowed.
     * - Outside `OperationRepositoryImpl.kt`, any `gateway.<op>(` or `gatewayApi.<op>(` call for
     *   a journalled write method (`publish`, `upload`, `reconcile`, `halt`, `resume`, `decide`,
     *   `transitionPlanStatus`) is a violation.
     * - Inside `OperationRepositoryImpl.kt`, scanning upward from each such call to the nearest
     *   enclosing `fun` declaration must encounter `executeOperation(`.
     * - Heuristic limits: assumes standard Kotlin layout where `executeOperation(` appears above
     *   the gateway call inside the same `fun` body, and does not parse full Kotlin AST or branch
     *   control flow (for instance, an `if` branch outside `executeOperation` within a `fun` that
     *   also contains `executeOperation(` elsewhere would not be separated by line scan alone).
     * - Documented exclusion: key-management calls (`enroll`, `rotateKey`, `revokeKey`,
     *   `enrollChallenge`) are not journalled operations and are excluded from this check.
     */
    fun scanJournalBypass(source: String, content: String): List<ScanFinding> {
        val normalized = source.replace('\\', '/')
        if (normalized.endsWith("/GatewayApi.kt") || normalized == "GatewayApi.kt" ||
            normalized.endsWith("/GatewayRepository.kt") || normalized == "GatewayRepository.kt"
        ) {
            return emptyList()
        }

        val isJournalImpl = normalized.endsWith("/OperationRepositoryImpl.kt") ||
            normalized == "OperationRepositoryImpl.kt"
        val lines = content.lines()
        val findings = mutableListOf<ScanFinding>()

        for ((idx, line) in lines.withIndex()) {
            for (match in GATEWAY_WRITE_CALL_REGEX.findAll(line)) {
                val method = match.groupValues[1]
                if (!isJournalImpl) {
                    findings += ScanFinding(
                        rule = "journal_bypass:outside_executor",
                        source = source,
                        line = idx + 1,
                        matchText = match.value.trim(),
                        detail = "Journalled write '$method' called outside OperationRepositoryImpl at $source:${idx + 1}",
                    )
                } else {
                    val insideExecuteOperation = hasExecuteOperationInEnclosingFunction(lines, idx)
                    if (!insideExecuteOperation) {
                        findings += ScanFinding(
                            rule = "journal_bypass:not_in_executeOperation",
                            source = source,
                            line = idx + 1,
                            matchText = match.value.trim(),
                            detail = "Gateway write '$method' in OperationRepositoryImpl at line ${idx + 1} is not preceded by executeOperation( in its enclosing fun",
                        )
                    }
                }
            }
        }
        return findings
    }

    private fun hasExecuteOperationInEnclosingFunction(lines: List<String>, callLineIdx: Int): Boolean {
        var funLineIdx = -1
        for (i in callLineIdx downTo 0) {
            if (FUN_DECL_REGEX.containsMatchIn(lines[i])) {
                funLineIdx = i
                break
            }
        }
        if (funLineIdx == -1) return false
        for (i in funLineIdx..callLineIdx) {
            if (EXECUTE_OPERATION_CALL_REGEX.containsMatchIn(lines[i])) {
                return true
            }
        }
        return false
    }

    /**
     * Rule E: flags references to `com.inscopelabs.buildengine.lab` or `HmacCapabilitySigner`
     * in non-lab production files (exempting files in package `com.inscopelabs.buildengine.lab`
     * and the `HmacCapabilitySigner.kt` definition itself).
     */
    fun scanLabInProduction(source: String, content: String): List<ScanFinding> {
        val normalized = source.replace('\\', '/')
        if (PACKAGE_LAB_REGEX.containsMatchIn(content)) {
            return emptyList()
        }
        val isHmacDefinition = normalized.endsWith("/HmacCapabilitySigner.kt") ||
            normalized == "HmacCapabilitySigner.kt"

        val findings = mutableListOf<ScanFinding>()
        content.lineSequence().forEachIndexed { idx, line ->
            for (match in LAB_IMPORT_OR_FQN_REGEX.findAll(line)) {
                findings += ScanFinding(
                    rule = "lab_isolation:lab_package_reference",
                    source = source,
                    line = idx + 1,
                    matchText = match.value.trim(),
                    detail = "Production file references lab package at $source:${idx + 1}: ${match.value.trim()}",
                )
            }
            if (!isHmacDefinition) {
                for (match in HMAC_SIGNER_REGEX.findAll(line)) {
                    findings += ScanFinding(
                        rule = "lab_isolation:hmac_signer_reference",
                        source = source,
                        line = idx + 1,
                        matchText = match.value,
                        detail = "Production file references HmacCapabilitySigner at $source:${idx + 1}",
                    )
                }
            }
        }
        return findings
    }

    /**
     * Rule F (Logging): detects `Logger.*(...)` calls whose string interpolations (`${...}` or
     * `$ident`) reference `nonce`, `signature`, `privateKey`, `bearer`, `credential`, or `toWire(`.
     * Literal words without interpolation are allowed.
     */
    fun scanSensitiveLogging(source: String, content: String): List<ScanFinding> {
        val findings = mutableListOf<ScanFinding>()
        for (startMatch in LOGGER_CALL_START_REGEX.findAll(content)) {
            val openParenIdx = content.indexOf('(', startMatch.range.first)
            if (openParenIdx < 0) continue
            val closeParenIdx = findMatchingDelimiter(content, openParenIdx, '(', ')')
            val callBody = if (closeParenIdx > openParenIdx) {
                content.substring(openParenIdx, closeParenIdx + 1)
            } else {
                content.substring(openParenIdx)
            }
            val callLine = lineNumberAt(content, startMatch.range.first)
            val interpolations = extractStringInterpolations(callBody)
            for (expr in interpolations) {
                if (SENSITIVE_LOG_EXPR_REGEX.containsMatchIn(expr)) {
                    findings += ScanFinding(
                        rule = "sensitive_logging:interpolated_secret",
                        source = source,
                        line = callLine,
                        matchText = expr.trim(),
                        detail = "Logger call at $source:$callLine interpolates sensitive expression '$expr'",
                    )
                }
            }
        }
        return findings
    }

    /**
     * Rule F (Persistence): inspects every Room `@Entity` class in Kotlin sources (parsing only
     * the `@Entity`-annotated class constructor and top-level class body properties so local helper
     * variables in sibling classes of the same file are not misclassified) and flags properties
     * named like `nonce`, `signature`, `capability`, `capabilityWire`, `privateKey`, `secret`,
     * `token`, `credential`, or `bearer`.
     */
    fun scanRoomEntities(source: String, content: String): List<ScanFinding> {
        if (!ENTITY_ANNOTATION_REGEX.containsMatchIn(content)) {
            return emptyList()
        }
        val findings = mutableListOf<ScanFinding>()
        for (entityMatch in ENTITY_ANNOTATION_REGEX.findAll(content)) {
            val headerStart = skipOptionalAnnotationArgs(content, entityMatch.range.last + 1)
            val classMatch = CLASS_DECL_REGEX.find(content, headerStart) ?: continue
            val className = classMatch.groupValues[1]

            var cursor = classMatch.range.last + 1
            while (cursor < content.length && content[cursor].isWhitespace()) cursor++

            // Primary constructor parameter list
            if (cursor < content.length && content[cursor] == '(') {
                val ctorClose = findMatchingDelimiter(content, cursor, '(', ')')
                if (ctorClose > cursor) {
                    val ctorBlock = content.substring(cursor, ctorClose + 1)
                    checkEntityProperties(
                        source = source,
                        className = className,
                        blockText = ctorBlock,
                        baseOffset = cursor,
                        fullContent = content,
                        findings = findings,
                    )
                    cursor = ctorClose + 1
                }
            }

            // Optional class body (inspect only brace depth == 1 properties, skipping fun/init bodies)
            val braceOpen = findNextTopLevelOpenBrace(content, cursor)
            if (braceOpen >= 0) {
                val braceClose = findMatchingDelimiter(content, braceOpen, '{', '}')
                if (braceClose > braceOpen) {
                    val topLevelBody = stripNestedBraces(content.substring(braceOpen + 1, braceClose))
                    checkEntityProperties(
                        source = source,
                        className = className,
                        blockText = topLevelBody,
                        baseOffset = braceOpen + 1,
                        fullContent = content,
                        findings = findings,
                    )
                }
            }
        }
        return findings
    }

    /**
     * Enforces the ratchet baseline invariant:
     * - Fails on any NEW finding not covered by [baseline].
     * - Fails on any STALE [baseline] entry that matches zero current findings (baseline must shrink).
     */
    fun verifyRatchetBaseline(
        guardName: String,
        findings: List<ScanFinding>,
        baseline: List<BaselineException>,
    ) {
        val matchedBaselineIndices = mutableSetOf<Int>()
        val unexpected = mutableListOf<ScanFinding>()

        for (finding in findings) {
            val normalizedSource = finding.source.replace('\\', '/')
            val matchIndex = baseline.indexOfFirst { entry ->
                entry.rule == finding.rule &&
                    normalizedSource.endsWith(entry.sourceSuffix) &&
                    finding.matchText == entry.matchText
            }
            if (matchIndex >= 0) {
                matchedBaselineIndices += matchIndex
            } else {
                unexpected += finding
            }
        }

        val stale = baseline.mapIndexedNotNull { idx, entry ->
            if (idx !in matchedBaselineIndices) entry else null
        }

        if (unexpected.isNotEmpty() || stale.isNotEmpty()) {
            val msg = buildString {
                appendLine("$guardName ratchet baseline check failed:")
                if (unexpected.isNotEmpty()) {
                    appendLine("Unexpected NEW violations (${unexpected.size}):")
                    unexpected.forEach { appendLine("  - [${it.rule}] ${it.source}:${it.line} (${it.matchText}) — ${it.detail}") }
                }
                if (stale.isNotEmpty()) {
                    appendLine("Stale baseline exceptions that no longer occur (${stale.size}); remove them so the ratchet shrinks:")
                    stale.forEach { appendLine("  - [${it.rule}] ${it.sourceSuffix} (${it.matchText}) — ${it.justification}") }
                }
            }
            throw AssertionError(msg)
        }
    }

    private fun checkEntityProperties(
        source: String,
        className: String,
        blockText: String,
        baseOffset: Int,
        fullContent: String,
        findings: MutableList<ScanFinding>,
    ) {
        for (propMatch in PROPERTY_DECL_REGEX.findAll(blockText)) {
            val propName = propMatch.groupValues[1]
            if (SENSITIVE_ENTITY_PROP_REGEX.containsMatchIn(propName)) {
                val absoluteOffset = baseOffset + propMatch.range.first
                val line = lineNumberAt(fullContent, absoluteOffset)
                findings += ScanFinding(
                    rule = "sensitive_persistence:entity_property",
                    source = source,
                    line = line,
                    matchText = propName,
                    detail = "@Entity class $className declares sensitive property '$propName' at $source:$line",
                )
            }
        }
    }

    private fun extractStringInterpolations(text: String): List<String> {
        val expressions = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            if (text[i] == '\\') {
                i += 2
                continue
            }
            if (text[i] == '$') {
                if (i + 1 < text.length && text[i + 1] == '{') {
                    val closeIdx = findMatchingDelimiter(text, i + 1, '{', '}')
                    if (closeIdx > i + 1) {
                        expressions += text.substring(i + 2, closeIdx)
                        i = closeIdx + 1
                        continue
                    }
                } else if (i + 1 < text.length && (text[i + 1].isLetter() || text[i + 1] == '_')) {
                    var end = i + 2
                    while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) {
                        end++
                    }
                    expressions += text.substring(i + 1, end)
                    i = end
                    continue
                }
            }
            i++
        }
        return expressions
    }

    private fun skipOptionalAnnotationArgs(content: String, startIdx: Int): Int {
        var i = startIdx
        while (i < content.length && content[i].isWhitespace()) i++
        if (i < content.length && content[i] == '(') {
            val closeIdx = findMatchingDelimiter(content, i, '(', ')')
            if (closeIdx > i) return closeIdx + 1
        }
        return i
    }

    private fun findNextTopLevelOpenBrace(content: String, startIdx: Int): Int {
        var i = startIdx
        while (i < content.length) {
            when (content[i]) {
                '{' -> return i
                ';' -> return -1
                '(' -> {
                    val close = findMatchingDelimiter(content, i, '(', ')')
                    i = if (close > i) close + 1 else i + 1
                }
                else -> {
                    // Stop if another declaration/annotation starts before a body brace
                    if (content.startsWith("fun ", i) || content.startsWith("class ", i)) {
                        return -1
                    }
                    i++
                }
            }
        }
        return -1
    }

    private fun stripNestedBraces(body: String): String {
        val out = StringBuilder(body.length)
        var depth = 0
        var inString = false
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && inString) {
                if (depth == 0) out.append(c)
                if (i + 1 < body.length && depth == 0) out.append(body[i + 1])
                i += 2
                continue
            }
            if (c == '"') {
                inString = !inString
                if (depth == 0) out.append(c)
                i++
                continue
            }
            if (!inString) {
                when (c) {
                    '{' -> {
                        depth++
                        out.append(' ')
                    }
                    '}' -> {
                        if (depth > 0) depth--
                        out.append(' ')
                    }
                    else -> out.append(if (depth == 0) c else ' ')
                }
            } else {
                out.append(if (depth == 0) c else ' ')
            }
            i++
        }
        return out.toString()
    }

    private fun findMatchingDelimiter(text: String, openIdx: Int, openChar: Char, closeChar: Char): Int {
        var depth = 0
        var inString = false
        var i = openIdx
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && inString) {
                i += 2
                continue
            }
            if (c == '"') {
                inString = !inString
                i++
                continue
            }
            if (!inString) {
                if (c == openChar) {
                    depth++
                } else if (c == closeChar) {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private fun lineNumberAt(content: String, offset: Int): Int {
        var line = 1
        val limit = offset.coerceAtMost(content.length)
        for (i in 0 until limit) {
            if (content[i] == '\n') line++
        }
        return line
    }
}

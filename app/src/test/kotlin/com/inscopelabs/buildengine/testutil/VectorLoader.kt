package com.inscopelabs.buildengine.testutil

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Loads the conformance vectors in `docs/fixtures/vectors/`.
 *
 * The vector files are produced by `docs/fixtures/vectors/generate_vectors.py` and must never
 * be edited by hand. Gradle runs unit tests with the module directory (`app/`) as the working
 * directory, so the repository-level docs folder is one level up.
 */
object VectorLoader {

    /** The parsed root object of a vector file, e.g. `valid_capabilities.json`. */
    fun root(name: String): JsonObject =
        Json.parseToJsonElement(file(name).readText(Charsets.UTF_8)).jsonObject

    /** The `vectors` array of a vector file, as objects. */
    fun vectors(name: String): List<JsonObject> =
        root(name).getValue("vectors").jsonArray.map { it.jsonObject }

    private fun file(name: String): File {
        val candidates = listOf(
            File("../docs/fixtures/vectors/$name"),
            File("docs/fixtures/vectors/$name"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Vector file '$name' not found (working directory ${File(".").absolutePath})")
    }
}

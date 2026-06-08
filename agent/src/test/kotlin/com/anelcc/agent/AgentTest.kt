package com.anelcc.agent

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AgentTest {
    private fun withFiles(test: (FileTools) -> Unit) {
        val parent = Files.createDirectories(Path.of("build", "test-data"))
        val root = Files.createTempDirectory(parent, "agent-")
        try {
            Files.writeString(root.resolve("GreetingActivity.kt"), "class GreetingActivity")
            Files.writeString(root.resolve("GreetingFormatter.kt"), "object GreetingFormatter")
            test(FileTools(root))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private val callContent = Json.parseToJsonElement("""{
        "role":"model","parts":[{"thoughtSignature":"opaque-signature",
        "functionCall":{"id":"call-1","name":"list_files","args":{}}}]
    }""").jsonObject

    private val finalContent = Json.parseToJsonElement("""{
        "role":"model","parts":[{"text":"Found GreetingActivity.kt"}]
    }""").jsonObject

    @Test
    fun `returns real file list and preserves model content and call ID`() = withFiles { files ->
        var requests = 0
        val agent = Agent(files, { history, tools ->
            assertTrue(tools.toString().contains("list_files"))
            if (requests++ == 0) {
                assertEquals(1, history.size)
                callContent
            } else {
                assertEquals(callContent, history[1])
                val result = history[2].jsonObject.getValue("parts").jsonArray[0]
                    .jsonObject.getValue("functionResponse").jsonObject
                assertEquals("call-1", result.getValue("id").jsonPrimitive.content)
                val paths = result.getValue("response").jsonObject.getValue("files").jsonArray
                assertEquals(listOf("GreetingActivity.kt", "GreetingFormatter.kt"), paths.map { it.jsonPrimitive.content })
                finalContent
            }
        })
        assertEquals("Found GreetingActivity.kt", agent.reviewCode())
        assertEquals(2, requests)
    }

    @Test
    fun `stops a model that repeatedly requests tools`() = withFiles { files ->
        var requests = 0
        val agent = Agent(files, { _, _ -> requests++; callContent }, maxRounds = 2)
        assertFailsWith<GeminiRequestException> { agent.reviewCode() }
        assertEquals(2, requests)
    }

    @Test
    fun `returns unknown tools as errors instead of executing them`() = withFiles { files ->
        var requests = 0
        val agent = Agent(files, { history, _ ->
            if (requests++ == 0) {
                Json.parseToJsonElement("""{"role":"model","parts":[{
                    "functionCall":{"name":"delete_file","args":{"path":"anything.kt"}}
                }]}""").jsonObject
            } else {
                assertTrue(history.last().toString().contains("Unknown tool"))
                finalContent
            }
        })
        agent.reviewCode()
        assertEquals(2, requests)
    }

    private fun readResult(files: FileTools, args: JsonElement): JsonObject {
        var requests = 0
        var result: JsonObject? = null
        Agent(files, { history, _ ->
            if (requests++ == 0) {
                buildJsonObject {
                    put("role", "model")
                    putJsonArray("parts") {
                        addJsonObject {
                            putJsonObject("functionCall") {
                                put("name", "read_file")
                                put("id", "read-1")
                                put("args", args)
                            }
                        }
                    }
                }
            } else {
                val response = history.last().jsonObject.getValue("parts").jsonArray[0]
                    .jsonObject.getValue("functionResponse").jsonObject
                assertEquals("read-1", response.getValue("id").jsonPrimitive.content)
                result = response.getValue("response").jsonObject
                finalContent
            }
        }).reviewCode()
        return requireNotNull(result)
    }

    @Test
    fun `read_file returns actual numbered source`() = withFiles { files ->
        val result = readResult(files, buildJsonObject { put("path", "GreetingActivity.kt") })
        assertEquals("1: class GreetingActivity", result.getValue("contents").jsonPrimitive.content)
    }

    @Test
    fun `rejects missing nonstring blank and extra arguments`() = withFiles { files ->
        val invalidArgs = listOf(
            buildJsonObject {},
            buildJsonObject { put("path", 123) },
            buildJsonObject { put("path", " ") },
            buildJsonObject { put("path", "GreetingActivity.kt"); put("extra", true) },
            JsonPrimitive("GreetingActivity.kt"),
        )
        invalidArgs.forEach { args -> assertTrue("error" in readResult(files, args)) }
    }

    @Test
    fun `returns errors for paths outside root and missing files`() = withFiles { files ->
        listOf("../outside.kt", "/outside.kt", "missing.kt").forEach { path ->
            assertTrue("error" in readResult(files, buildJsonObject { put("path", path) }))
        }
    }

    private fun readCall(path: String): JsonObject = buildJsonObject {
        put("role", "model")
        putJsonArray("parts") {
            addJsonObject {
                putJsonObject("functionCall") {
                    put("name", "read_file")
                    putJsonObject("args") { put("path", path) }
                }
            }
        }
    }

    @Test
    fun `coverage reports both files read and deduplicates equivalent paths`() = withFiles { files ->
        val responses = listOf(callContent, readCall("./GreetingActivity.kt"),
            readCall("GreetingActivity.kt"), readCall("GreetingFormatter.kt"), finalContent).iterator()
        val agent = Agent(files, { _, _ -> responses.next() })
        agent.reviewCode()
        val coverage = requireNotNull(agent.lastCoverage)
        assertTrue(coverage.discoveryCompleted)
        assertTrue(coverage.finalAnswerReceived)
        assertEquals(setOf("GreetingActivity.kt", "GreetingFormatter.kt"), coverage.readFiles)
        assertTrue(coverage.skippedFiles.isEmpty())
    }

    @Test
    fun `a final answer does not hide skipped files`() = withFiles { files ->
        val responses = listOf(callContent, readCall("GreetingActivity.kt"), finalContent).iterator()
        val agent = Agent(files, { _, _ -> responses.next() })
        agent.reviewCode()
        val coverage = requireNotNull(agent.lastCoverage)
        assertTrue(coverage.finalAnswerReceived)
        assertEquals(setOf("GreetingFormatter.kt"), coverage.skippedFiles)
    }

    @Test
    fun `failed reads remain skipped and coverage survives an API failure`() = withFiles { files ->
        var calls = 0
        val agent = Agent(files, { history, _ ->
            calls++
            if (calls == 2) {
                // A rejected read must not mark any discovered file as read.
                readCall("../GreetingFormatter.kt")
            } else if (calls == 3) {
                assertTrue(history.last().toString().contains("error"))
                throw GeminiRequestException("Simulated API outage")
            } else callContent
        })
        assertFailsWith<GeminiRequestException> { agent.reviewCode() }
        val coverage = requireNotNull(agent.lastCoverage)
        assertTrue(!coverage.finalAnswerReceived)
        assertTrue(coverage.readFiles.isEmpty())
        assertEquals(coverage.discoveredFiles, coverage.skippedFiles)
    }

    @Test
    fun `round limit keeps coverage and missing listing stays unknown`() = withFiles { files ->
        val agent = Agent(files, { _, _ -> callContent }, maxRounds = 2)
        assertFailsWith<GeminiRequestException> { agent.reviewCode() }
        assertEquals(setOf("GreetingActivity.kt", "GreetingFormatter.kt"),
            requireNotNull(agent.lastCoverage).skippedFiles)
        assertTrue(!requireNotNull(agent.lastCoverage).finalAnswerReceived)

        val noListing = Agent(files, { _, _ -> finalContent })
        noListing.reviewCode()
        assertTrue(!requireNotNull(noListing.lastCoverage).discoveryCompleted)
    }

}

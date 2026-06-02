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
                assertEquals(listOf("GreetingActivity.kt"), paths.map { it.jsonPrimitive.content })
                finalContent
            }
        })
        assertEquals("Found GreetingActivity.kt", agent.discoverFiles())
        assertEquals(2, requests)
    }

    @Test
    fun `stops a model that repeatedly requests tools`() = withFiles { files ->
        var requests = 0
        val agent = Agent(files, { _, _ -> requests++; callContent }, maxRounds = 2)
        assertFailsWith<GeminiRequestException> { agent.discoverFiles() }
        assertEquals(2, requests)
    }

    @Test
    fun `returns unknown tools as errors instead of executing them`() = withFiles { files ->
        var requests = 0
        val agent = Agent(files, { history, _ ->
            if (requests++ == 0) {
                Json.parseToJsonElement("""{"role":"model","parts":[{
                    "functionCall":{"name":"read_file","args":{"path":"anything.kt"}}
                }]}""").jsonObject
            } else {
                assertTrue(history.last().toString().contains("Unknown tool"))
                finalContent
            }
        })
        agent.discoverFiles()
        assertEquals(2, requests)
    }
}

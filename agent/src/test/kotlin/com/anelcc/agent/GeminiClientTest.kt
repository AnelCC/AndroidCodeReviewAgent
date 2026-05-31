package com.anelcc.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeminiClientTest {
    @Test
    fun `reads answer parts and skips thought text`() {
        val body = """{"candidates":[{"finishReason":"STOP","content":{"parts":[
            {"text":"internal","thought":true},{"text":"Hello"},{"text":"World"}
        ]}}]}"""
        assertEquals("Hello\nWorld", parseGeminiResponseText(body))
    }

    @Test
    fun `rejects blocked and empty responses`() {
        assertFailsWith<GeminiRequestException> {
            parseGeminiResponseText("""{"promptFeedback":{"blockReason":"SAFETY"}}""")
        }
        assertFailsWith<GeminiRequestException> {
            parseGeminiResponseText("""{"candidates":[{"finishReason":"STOP","content":{"parts":[]}}]}""")
        }
    }

    @Test
    fun `rejects truncated output`() {
        assertFailsWith<GeminiRequestException> {
            parseGeminiResponseText("""{"candidates":[{"finishReason":"MAX_TOKENS"}]}""")
        }
    }

    @Test
    fun `rejects a model ID that could change the endpoint path`() {
        assertFailsWith<IllegalArgumentException> { GeminiClient("dummy", "../other") }
    }
}

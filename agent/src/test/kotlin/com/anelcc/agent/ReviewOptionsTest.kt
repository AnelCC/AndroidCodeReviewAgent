package com.anelcc.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReviewOptionsTest {
    @Test
    fun `uses samples and eight rounds by default`() {
        assertEquals(ReviewOptions("samples", 8), parseReviewOptions(emptyList()))
    }

    @Test
    fun `accepts a custom limit with or without a folder`() {
        assertEquals(ReviewOptions("samples", 6), parseReviewOptions(listOf("--max-rounds", "6")))
        assertEquals(ReviewOptions("my sources", 12),
            parseReviewOptions(listOf("my sources", "--max-rounds", "12")))
        assertEquals(ReviewOptions("other", 3),
            parseReviewOptions(listOf("--max-rounds", "3", "other")))
    }

    @Test
    fun `rejects missing invalid and ambiguous options`() {
        listOf(
            listOf("--max-rounds"),
            listOf("--max-rounds", "0"),
            listOf("--max-rounds", "-1"),
            listOf("--max-rounds", "1.5"),
            listOf("--max-rounds", "abc"),
            listOf("--max-rounds", "999999999999999999"),
            listOf("--max-rounds", "2", "--max-rounds", "3"),
            listOf("--unknown"),
            listOf("one", "two"),
        ).forEach { args ->
            assertFailsWith<IllegalArgumentException> { parseReviewOptions(args) }
        }
    }
}

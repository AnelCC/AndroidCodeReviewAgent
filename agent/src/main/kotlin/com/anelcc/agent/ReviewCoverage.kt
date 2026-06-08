package com.anelcc.agent

/** Tool activity, not a guarantee that the model analyzed every line. */
data class ReviewCoverage(
    val discoveryCompleted: Boolean,
    val finalAnswerReceived: Boolean,
    val discoveredFiles: Set<String>,
    val readFiles: Set<String>,
) {
    val skippedFiles: Set<String> get() = discoveredFiles - readFiles

    fun printSummary() {
        println("[coverage] final answer received=$finalAnswerReceived")
        if (!discoveryCompleted) {
            println("[coverage] discovery not completed; total file coverage is unknown")
        }
        println("[coverage] discovered=${discoveredFiles.size}, read=${readFiles.size}, skipped=${skippedFiles.size}")
        println("[coverage] read files: ${readFiles.sorted().joinToString().ifEmpty { "(none)" }}")
        println("[coverage] skipped files (not successfully read): ${skippedFiles.sorted().joinToString().ifEmpty { "(none known)" }}")
        println("[coverage] Successful reads do not guarantee a complete or correct model analysis.")
    }
}

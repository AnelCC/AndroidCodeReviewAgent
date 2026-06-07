package com.anelcc.agent

internal const val DEFAULT_MAX_ROUNDS = 8

internal data class ReviewOptions(
    val folder: String = "samples",
    val maxRounds: Int = DEFAULT_MAX_ROUNDS,
)

/** Parses arguments after --review, without requiring credentials or network access. */
internal fun parseReviewOptions(args: List<String>): ReviewOptions {
    var folder: String? = null
    var maxRounds: Int? = null
    var index = 0
    while (index < args.size) {
        val argument = args[index]
        when {
            argument == "--max-rounds" -> {
                require(maxRounds == null) { "Specify --max-rounds only once." }
                val value = args.getOrNull(++index)?.toIntOrNull()
                require(value != null && value > 0) { "--max-rounds requires a positive whole number." }
                maxRounds = value
            }
            argument.startsWith("--") -> throw IllegalArgumentException("Unknown review option.")
            else -> {
                require(folder == null && argument.isNotBlank()) { "Specify only one review folder." }
                folder = argument
            }
        }
        index++
    }
    return ReviewOptions(folder ?: "samples", maxRounds ?: DEFAULT_MAX_ROUNDS)
}

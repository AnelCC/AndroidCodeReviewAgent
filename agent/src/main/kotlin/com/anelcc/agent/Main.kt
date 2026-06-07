package com.anelcc.agent

fun main(args: Array<String>) {
    if (args.firstOrNull() in setOf("--gemini", "--review")) {
        val reviewOptions = if (args.first() == "--review") {
            try {
                parseReviewOptions(args.drop(1))
            } catch (error: IllegalArgumentException) {
                System.err.println(error.message)
                System.err.println("Usage: --review [folder] [--max-rounds positive-integer]")
                kotlin.system.exitProcess(1)
            }
        } else null
        val key = System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }
        if (key == null) {
            System.err.println("Set GEMINI_API_KEY before using --gemini or --review.")
            kotlin.system.exitProcess(1)
        }
        val model = System.getenv("GEMINI_MODEL")?.takeIf { it.isNotBlank() }
            ?: "gemini-3.5-flash-lite"
        try {
            val client = GeminiClient(key, model)
            if (args.first() == "--review") {
                val options = requireNotNull(reviewOptions)
                val root = java.nio.file.Path.of(options.folder)
                Agent(FileTools(root), client::generateContent, options.maxRounds).reviewCode()
            } else {
                println("[model request] Gemini model=$model; text-only connection check")
                val answer = client.generateText(
                    "In one short sentence, explain what an AI code review agent does."
                )
                println("[model response] $answer")
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            System.err.println("Gemini request interrupted.")
            kotlin.system.exitProcess(1)
        } catch (error: GeminiRequestException) {
            System.err.println(error.message)
            kotlin.system.exitProcess(1)
        } catch (error: Exception) {
            System.err.println("Gemini request failed. Check network access and GEMINI_MODEL.")
            kotlin.system.exitProcess(1)
        }
        return
    }

    val reviewRoot = java.nio.file.Path.of(args.firstOrNull() ?: "samples")
    val tools = FileTools(reviewRoot)

    println("[tool call] list_files()")
    val files = tools.listFiles()
    println("[tool result] ${files.size} Kotlin file(s): ${files.joinToString()}")

    for (file in files) {
        println("\n[tool call] read_file(path=\"$file\")")
        println("[tool result]\n${tools.readFile(file)}")
    }
}

package com.anelcc.agent

fun main(args: Array<String>) {
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

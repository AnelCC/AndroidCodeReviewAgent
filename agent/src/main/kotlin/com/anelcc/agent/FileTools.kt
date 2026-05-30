package com.anelcc.agent

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines

/** Read-only tools restricted to Kotlin files inside one review folder. */
class FileTools(root: Path) {
    private val root = root.toRealPath()

    init {
        require(this.root.isDirectory()) { "Review root must be a directory" }
    }

    fun listFiles(): List<String> = Files.walk(root).use { paths ->
        paths.filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
            .filter { it.toRealPath().startsWith(root) }
            .map { root.relativize(it).toString().replace('\\', '/') }
            .sorted()
            .toList()
    }

    fun readFile(relativePath: String): String {
        val requested = Path.of(relativePath)
        require(!requested.isAbsolute) { "Use a path relative to the review folder" }
        val file = root.resolve(requested).normalize()
        require(file.startsWith(root)) { "File must stay inside the review folder" }
        val realFile = file.toRealPath()
        require(realFile.startsWith(root)) { "File must stay inside the review folder" }
        require(realFile.isRegularFile() && realFile.fileName.toString().endsWith(".kt")) {
            "Only Kotlin source files can be read"
        }
        require(Files.size(realFile) <= 100_000) { "File exceeds the 100 KB limit" }
        return realFile.readLines().mapIndexed { index, line -> "${index + 1}: $line" }
            .joinToString("\n")
    }
}

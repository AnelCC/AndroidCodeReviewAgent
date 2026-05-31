package com.anelcc.agent

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.serialization.json.*

/** One text request using Gemini's generateContent REST endpoint. */
class GeminiClient(private val apiKey: String, private val model: String) {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    init {
        require(model.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid Gemini model ID" }
    }

    fun generateText(prompt: String): String {
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { addJsonObject { put("text", prompt) } }
                }
            }
        }
        val request = HttpRequest.newBuilder(
            URI.create("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        )
            .timeout(Duration.ofSeconds(90))
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        println("[model HTTP] status=${response.statusCode()}")
        if (response.statusCode() !in 200..299) {
            // Local messages only: never print raw errors, requests, or headers.
            throw GeminiRequestException(when (response.statusCode()) {
                400 -> "Gemini rejected the request. Check the API key and model configuration."
                401, 403 -> "Gemini denied access. Check the API key and project permissions."
                404 -> "Gemini model was not found. Check GEMINI_MODEL and project access."
                429 -> "Gemini quota or rate limit reached. Check free-tier limits in AI Studio; no automatic retry was made."
                else -> "Gemini returned HTTP ${response.statusCode()}. Try again later."
            })
        }
        return parseGeminiResponseText(response.body())
    }
}

internal class GeminiRequestException(message: String) : RuntimeException(message)

internal fun parseGeminiResponseText(body: String): String {
    val response = Json.parseToJsonElement(body).jsonObject
    val candidate = response["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
        ?: throw GeminiRequestException("Gemini returned no answer; the prompt may have been blocked.")
    if (candidate["finishReason"]?.jsonPrimitive?.content != "STOP") {
        throw GeminiRequestException("Gemini did not finish a complete answer.")
    }
    val text = candidate["content"]?.jsonObject?.get("parts")?.jsonArray.orEmpty()
        .filter { it.jsonObject["thought"]?.jsonPrimitive?.booleanOrNull != true }
        .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
        .joinToString("\n")
    if (text.isBlank()) throw GeminiRequestException("Gemini returned no text.")
    return text
}

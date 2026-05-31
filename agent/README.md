# Kotlin agent

Run commands from the repository root.

## Local file tools (no API call)

```sh
./gradlew :agent:run
```

An optional relative or absolute review folder can be passed with `--args="samples"`.

## Gemini connection check (current learning path)

Use an API key from a Google AI Studio project marked **Free tier**. Keep that
project on the free tier; the client cannot determine your billing tier.
Free-tier requests have quotas and may require project verification. Prompts
and responses may be used by Google to improve its products.

In zsh, enter your key without displaying it or putting it in shell history:

```sh
read -s "GEMINI_API_KEY?Gemini API key: "
export GEMINI_API_KEY
echo
./gradlew :agent:run --args="--gemini"
```

The default model ID is `gemini-3.5-flash-lite`, documented as having free-tier
text input/output. Override it with `GEMINI_MODEL` only when needed. This mode
sends one fixed text prompt and prints the answer; it does not send source files
or execute model-requested tools yet. The key is sent in a header, never a URL.
No automatic retries or paid fallback are configured.

Model: https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite
Pricing: https://ai.google.dev/gemini-api/docs/pricing
REST: https://ai.google.dev/api/generate-content

## Implementation

`GeminiClient.kt` sends text requests using Java's built-in HTTP client.
`GeminiClientTest.kt` verifies response parsing without making API calls.
JSON is handled by kotlinx.serialization 1.9.0, compatible with this project's
Kotlin 2.2 version. No serialization compiler plugin is needed for its JSON tree API.

## Java setup

If the terminal cannot locate Java, use Android Studio's bundled JDK:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

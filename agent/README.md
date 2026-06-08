# Kotlin agent

Run commands from the repository root.

## Local file tools (no API call)

```sh
./gradlew :agent:run
```

An optional relative or absolute review folder can be passed with `--args="samples"`.

## Gemini connection check

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

## Code review

With GEMINI_API_KEY set in the current terminal, run:

```sh
./gradlew :agent:run --args="--review"
```

To choose another review folder:

```sh
./gradlew :agent:run --args="--review samples"
```

To allow a different maximum number of model requests:

```sh
./gradlew :agent:run --args="--review samples --max-rounds 12"
```

The default is eight rounds (previously four). A round is one request to Gemini,
not one file or one tool call. The final review also needs a round. Gemini can
request several tools in one response. The agent stops early when it produces
a final answer. Increasing the limit allows more requests and may consume more
free-tier quota; it does not force the agent to use every round.
`--max-rounds` must be a positive whole number; invalid values fail before any
API request. Logs display the current round and maximum, such as `round=2/8`.

`--review` replaces the earlier `--discover` command. No flag still runs the
local file-tool demonstration, and `--gemini` still runs the text connection check.

Main calls `Agent.reviewCode()`. The prompt asks Gemini to discover Kotlin files
with list_files and inspect them with read_file before reporting bugs. Each
finding should contain a file path, line number, title, trigger, impact, and fix.
The prompt asks for evidence from inspected source and an explicit statement of
any failed reads or unreviewed files. These are model instructions, not a strict
output schema or a guarantee of complete bug detection. The sample's intentional
bug is not disclosed in the prompt.

Kotlin validates tool arguments and executes the real file tools. read_file
requires exactly one nonblank string argument, path. FileTools restricts reads
to Kotlin files inside the configured review folder and returns numbered source.
That source is sent to Google for review. Use the toy samples on the free tier.

Logs show each model request, tool call, tool result, and model text. All model
content parts are preserved between rounds, including thought signatures;
function response IDs match request IDs when present. The model chooses tool
calls; the prompt guides the sequence. The loop stops at the configured maximum number of model
requests, with no automatic retries or paid fallback.

Run offline tests with:

```sh
./gradlew :agent:test
```

## File coverage

After each review attempt, Kotlin prints a `[coverage]` summary, even if the
request fails or the round limit is reached. It records paths from successful
list_files calls and files successfully read by read_file. Repeated reads count
once. Skipped files are discovered paths minus successfully read paths; failed
reads remain skipped. A final model answer does not imply that no files were
skipped. Without a successful listing, total coverage is reported as unknown.
These records describe tool execution, not proof that Gemini analyzed every
line or found every bug. They do not add API calls or force additional reads.

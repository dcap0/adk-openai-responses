# adk-openai-responses

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A reactive Java adapter integrating the OpenAI Responses API with the Google Agent Development Kit (ADK) 1.0.

This library provides a bridge between Google's ADK `LlmRequest` / `LlmResponse` models and any OpenAI-compatible LLM endpoint. It is designed to support both production API gateways and unauthenticated local models (such as Ollama or LM Studio) out of the box.

## Features

* **Reactive Streaming:** Real-time Server-Sent Events (SSE) mapped directly to RxJava `Flowable` streams for backpressure-aware chunking.
* **Synchronous Generation:** Support for blocking, single-turn response execution.
* **Flexible Authentication:** Auto-loads keys from the environment or connects smoothly to unauthenticated local servers.
* **Zero Logging Pollution:** Built strictly against `slf4j-api`, allowing the consuming application to dictate the logging engine.

## Usage

The library relies on standard Google ADK `LlmRequest` and `LlmResponse` objects. You can interact with your models either synchronously or via reactive streams.


### Build

In order to use this project, the easiest solution is to use your local Maven cache. `~/.m2/repository`

```bash
./gradlew publishToMavenLocal
```

### Gradle

Update build.gradle.kts (or build.gradle) to check the local Maven cache

```kotlin
repositories{
    mavenLocal()
    mavenCentral()
}
```

Then just add the dependency:

Gradle:
```kotlin
dependencies {
    implementation("org.ddmac:adk-openai-responses:0.1.0")
}
```

### Maven

Maven already checks the local cache, so just add it to your dependencies:

```xml
<dependencies>
    <dependency>
        <groupId>org.ddmac</groupId>
        <artifactId>adk-openai-responses</artifactId>
        <version>0.1.0</version>
    </dependency>
</dependencies>
```


### 1. Initialization

There are three ways to initialize the LLM:

```java
// For unauthenticated local models (e.g., Ollama, LM Studio)
OpenAIResponsesLlm localLlm = new OpenAIResponsesLlm("llama3", "http://localhost:11434");

// For OpenAI production (auto-loads OPENAI_API_KEY from environment)
OpenAIResponsesLlm prodLlm = new OpenAIResponsesLlm("gpt-4o");

// For custom enterprise gateways requiring auth
OpenAIResponsesLlm gatewayLlm = new OpenAIResponsesLlm("mixtral", "https://api.mycompany.com", "my-secret-key");
```

First, instantiate the OpenAI adapter and inject it into your ADK `LlmAgent` builder.

```java
// Initialize the adapter (pointing to your local or remote LLM)
OpenAIResponsesLlm customLlm = new OpenAIResponsesLlm("gemma3:1b-it-qat", "http://localhost:11434");

// Build the Agent, injecting the model, instructions, and tools
LlmAgent sysAdminAgent = LlmAgent.builder()
        .name("netadmin-agent")
        .description("Network Administrator Agent")
        .model(customLlm)
        .instruction("You are a Network Administrator who monitors various servers on a network and analyzes network data. Provide the user the latest new technology using Google Search.")
        .build();
```

### 2. Synchronous Usage

To wait for the complete response before processing, change the StreamingMode to NONE in your RunConfig. The runner will emit a single, complete event.

```java
// 1. Set up the ADK Runner and Session
InMemoryRunner runner = new InMemoryRunner(sysAdminAgent);
String userId = "user";
Session session = runner.sessionService()
        .createSession(sysAdminAgent.name(), userId)
        .blockingGet();

// 2. Build the user message
Content userMsg = Content.builder()
        .role("user")
        .parts(List.of(Part.builder().text("Are there any good tools for monitoring traffic on a network?").build()))
        .build();

// Configure the runner for Synchronous execution
RunConfig syncConfig = RunConfig.builder()
                .streamingMode(RunConfig.StreamingMode.NONE)
                .build();

// Execute and print the final compiled response
runner.runAsync(userId, session.id(), userMsg, syncConfig).blockingForEach(event -> {
        event.content().flatMap(Content::parts).ifPresent(parts -> {
            for (Part part : parts) {
                part.text().ifPresent(System.out::print);
            }
        });
});
```

### Asynchronous Streaming (Server-Sent Events)

To stream tokens in real-time, configure the RunConfig for Server-Sent Events (SSE) and use the runner's runAsync method.

```java
// 1. Set up the ADK Runner and Session
InMemoryRunner runner = new InMemoryRunner(sysAdminAgent);
        String userId = "user";
        Session session = runner.sessionService()
                .createSession(sysAdminAgent.name(), userId)
                .blockingGet();

// 2. Build the user message
Content userMsg = Content.builder()
        .role("user")
        .parts(List.of(Part.builder().text("Are there any good tools for monitoring traffic on a network?").build()))
        .build();

// 3. Configure the runner for Streaming
RunConfig streamConfig = RunConfig.builder()
        .streamingMode(RunConfig.StreamingMode.SSE)
        .build();

// 4. Execute and flush tokens to standard out as they arrive
runner.runAsync(u, session.id(), userMsg, rConfig).blockingForEach(event -> {
        event.content().flatMap(Content::parts).ifPresent(parts -> {
            for (Part part : parts) {
                part.text().ifPresent(t -> {
                    System.out.print(t);
                    System.out.flush();
                });
            }
        });
});
```

## TODO

* Add Tool functionality to support developing agents.

## Repository

[https://github.com/dcap0/adk-openai-responses](https://github.com/dcap0/adk-openai-responses)

## License

This project is licensed under the Apache License, Version 2.0.
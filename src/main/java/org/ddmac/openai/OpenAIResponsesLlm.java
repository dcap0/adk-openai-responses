package org.ddmac.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.models.*;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import org.ddmac.openai.dto.Content;
import org.ddmac.openai.dto.OpenAIResponsesAPIRequest;
import org.ddmac.openai.dto.OpenAIResponsesAPIRequest.Input;
import org.ddmac.openai.dto.OpenAIResponsesAPISingleResponse;
import org.ddmac.openai.dto.OpenAIResponsesAPIStreamingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * A reactive Large Language Model (LLM) adapter that integrates the OpenAI Responses API
 * with the Google Agent Development Kit (ADK).
 * <p>
 * This class extends {@link BaseLlm} to provide support for both synchronous and
 * asynchronous Server-Sent Events (SSE) streaming generation. It maps ADK {@link LlmRequest}
 * objects to OpenAI-compatible payloads and converts the resulting HTTP responses back into
 * a reactive {@link Flowable} stream of {@link LlmResponse} objects.
 */
public class OpenAIResponsesLlm extends BaseLlm {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIResponsesLlm.class);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final URI aiURI;

    /**
     * Constructs a new OpenAIResponsesLlm adapter.
     *
     * @param model   The specific model identifier to be used (e.g., "gpt-4o").
     * @param baseUrl The base URL of the OpenAI-compatible server. The path "/v1/responses"
     * will be automatically appended to this URL.
     */
    public OpenAIResponsesLlm(String model, String baseUrl) {
        super(model);
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
        this.aiURI = URI.create(baseUrl + "/v1/responses");
    }

    /**
     * Generates content using the underlying OpenAI-compatible model.
     *
     * @param llmRequest The standardized request from the ADK containing prompts, context, and configurations.
     * @param stream     If {@code true}, establishes an asynchronous SSE connection and emits partial chunks.
     * If {@code false}, blocks and emits a single complete response.
     * @return A {@link Flowable} stream of {@link LlmResponse} objects.
     */
    @Override
    public Flowable<LlmResponse> generateContent(LlmRequest llmRequest, boolean stream) {
        if(stream){
            return streamResponse(llmRequest);
        } else {
            return singleResponse(llmRequest);
        }
    }

    /**
     * Handles asynchronous, Server-Sent Events (SSE) streaming generation.
     *
     * @param llmRequest The ADK request to process.
     * @return A reactive stream emitting partial {@link LlmResponse} chunks as they arrive.
     */
    private Flowable<LlmResponse> streamResponse(LlmRequest llmRequest){
        return Flowable.create(emitter -> {
                String reqBody = mapToOpenAIRequest(llmRequest,true).toJson();

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(aiURI)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                        .build();

                httpClient.sendAsync(
                        httpRequest,
                        HttpResponse.BodyHandlers.ofInputStream()
                ).whenComplete((res,throwable) -> {
                    if(throwable != null){
                        emitter.onError(throwable);
                        return;
                    }

                    if(res.statusCode() != 200){
                        emitter.onError(new RuntimeException("OpenAI API Error " + res.statusCode()));
                        return;
                    }

                    try(BufferedReader br = new BufferedReader(new InputStreamReader(res.body()))){
                        String line;
                        String currentEvent = "";

                        while((line = br.readLine()) != null){
                            if(line.isEmpty()) continue;
                            if(!line.startsWith("event: ") && !line.startsWith("data: ")){
                                throw new RuntimeException("Error processing line: "+line);
                            }
                            if(line.startsWith("event: ")){
                                currentEvent = line.substring(7).trim();
                            }
                            if(line.startsWith("data: ")){
                                String jsonData = line.substring(6).trim();

                                switch (currentEvent){
                                    case "response.output_text.delta":
                                        try {
                                            LlmResponse chunk = parseStreamingResponse(jsonData);
                                            emitter.onNext(chunk);
                                        } catch (JsonProcessingException e){
                                            emitter.onError(e);
                                            return;
                                        }
                                        break;
                                    case "response.completed":
                                        captureUsage(jsonData);
                                        emitter.onNext(
                                                LlmResponse
                                                        .builder()
                                                        .turnComplete(true)
                                                        .build()
                                        );
                                        emitter.onComplete();
                                        return;
                                    case "response.output_text.done":
                                        //check stuff
                                        break;
                                    case "error":
                                        emitter.onError(new RuntimeException("API response parsing failed: "+jsonData));
                                        return;
                                    default:
                                        break;
                                }
                            }
                        }
                    } catch (IOException e) {
                        emitter.onError(e);
                        return;
                    }
                    emitter.onComplete();
                });

        },
                BackpressureStrategy.BUFFER
        );
    }

    /**
     * Handles synchronous, single-turn content generation.
     *
     * @param llmRequest The ADK request to process.
     * @return A reactive stream emitting exactly one complete {@link LlmResponse}.
     */
    private Flowable<LlmResponse> singleResponse(LlmRequest llmRequest){
        return Flowable.fromCallable(() -> {
            try{
                String reqBody = mapToOpenAIRequest(llmRequest,false).toJson();

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(aiURI)
                        .header("Content-Type","application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                        .build();

                HttpResponse<String> res = httpClient.send(httpRequest,HttpResponse.BodyHandlers.ofString());

                return parseSingleResponse(res.body());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Maps the ADK's standardized LlmRequest into the specific JSON shape required
     * by the OpenAI Responses API.
     *
     * @param llmRequest The incoming ADK request.
     * @param stream     Whether the payload should request a streaming response.
     * @return A constructed {@link OpenAIResponsesAPIRequest} ready for serialization.
     */
    private OpenAIResponsesAPIRequest<?> mapToOpenAIRequest(LlmRequest llmRequest, boolean stream){
        OpenAIResponsesAPIRequest.Builder<?> builder = OpenAIResponsesAPIRequest.builder()
                .model(model())
                .stream(stream);

        if(stream) {
            String instructions = String.join("\n",llmRequest.getSystemInstructions());

            builder = builder.instructions(instructions);

            List<Input> inputs = llmRequest.contents().stream()
                    .map(content -> {
                        //get role and map model to assistant
                        String role = content.role().orElse("user").toLowerCase();
                        if (role.equals("model")) {
                            role = "assistant";
                        }

                        List<Content> contentList = content.parts().orElse(new ArrayList<>())
                                .stream().map(part -> new Content("input_text", part.text().orElse("")))
                                .toList();

                        return new Input(role, contentList);
                    })
                    .toList();

            builder = builder.input(inputs);
        } else {
            String input = String.join(" ",llmRequest.contents().stream().map(com.google.genai.types.Content::text).toList());
            builder = builder.input(input);
        }

        return builder.build();
    }

    /**
     * Deserializes an SSE JSON chunk and converts it into a partial ADK response.
     *
     * @param json The JSON string received in the "data:" line of the SSE stream.
     * @return A partial {@link LlmResponse}.
     * @throws JsonProcessingException If the JSON cannot be parsed.
     */
    private LlmResponse parseStreamingResponse(String json) throws JsonProcessingException {
        OpenAIResponsesAPIStreamingResponse res = mapper.readValue(json, OpenAIResponsesAPIStreamingResponse.class);
        String text = res.delta() != null ? res.delta() : (res.text() != null ? res.text() : "");
        return buildLlmResponse(text,true);
    }

    /**
     * Deserializes a complete API JSON response and converts it into a final ADK response.
     *
     * @param json The raw JSON string returned by the non-streaming endpoint.
     * @return A complete, non-partial {@link LlmResponse}.
     * @throws JsonProcessingException If the JSON cannot be parsed.
     */
    private LlmResponse parseSingleResponse(String json) throws JsonProcessingException {
        OpenAIResponsesAPISingleResponse res = mapper.readValue(json, OpenAIResponsesAPISingleResponse.class);
        StringBuilder sb = new StringBuilder();

        res.output().stream()
                .flatMap(out -> out.content().stream())
                .forEach(content -> sb.append(content.text()));

        return buildLlmResponse(sb.toString(),false);
    }

    /**
     * Constructs the final ADK {@link LlmResponse} object, setting the appropriate
     * stream management flags.
     *
     * @param text      The text generated by the model.
     * @param isPartial {@code true} if this is an incomplete chunk from a stream; {@code false} otherwise.
     * @return The configured {@link LlmResponse}.
     */
    private LlmResponse buildLlmResponse(String text, boolean isPartial){
        Part part = Part.builder().text(text).build();
        com.google.genai.types.Content content = com.google.genai.types.Content.builder()
                .role("assistant")
                .parts(List.of(part))
                .build();

        return LlmResponse.builder().content(content)
                .partial(isPartial)
                .turnComplete(!isPartial)
                .build();
    }

    /**
     * Extracts token usage statistics from the final completion event of a stream
     * and logs them for telemetry purposes.
     *
     * @param json The JSON payload of the "response.completed" event.
     */
    private void captureUsage(String json){
        try {
            OpenAIResponsesAPIStreamingResponse res = mapper.readValue(json, OpenAIResponsesAPIStreamingResponse.class);
            StringBuilder sb = new StringBuilder();
            if(res.response() != null && res.response().usage() != null){
                sb.append("Token Usage\n")
                        .append(String.format("\tInput Tokens: %d\n", res.response().usage().inputTokens()))
                        .append(String.format("\tOutput Tokens: %d\n", res.response().usage().outputTokens()))
                        .append(String.format("\tTotal Tokens: %d\n", res.response().usage().totalTokens()));
                logger.warn(sb.toString());
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public BaseLlmConnection connect(LlmRequest llmRequest) {
        throw new UnsupportedOperationException("Bidirectional live streaming is not supported by this provider.");
    }
}

package org.ddmac.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
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

public class OpenAIResponsesLlm extends BaseLlm {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIResponsesLlm.class);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final URI aiURI;

    public OpenAIResponsesLlm(String model, String baseUrl) {
        super(model);
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
        this.aiURI = URI.create(baseUrl + "/v1/responses");
    }

    @Override
    public Flowable<LlmResponse> generateContent(LlmRequest llmRequest, boolean stream) {
        if(stream){
            return streamResponse(llmRequest);
        } else {
            return singleResponse(llmRequest);
        }
    }

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

    private LlmResponse parseStreamingResponse(String json) throws JsonProcessingException {
        OpenAIResponsesAPIStreamingResponse res = mapper.readValue(json, OpenAIResponsesAPIStreamingResponse.class);
        String text = res.delta() != null ? res.delta() : (res.text() != null ? res.text() : "");
        return buildLlmResponse(text,true);
    }

    private LlmResponse parseSingleResponse(String json) throws JsonProcessingException {
        OpenAIResponsesAPISingleResponse res = mapper.readValue(json, OpenAIResponsesAPISingleResponse.class);
        StringBuilder sb = new StringBuilder();

        res.output().stream()
                .flatMap(out -> out.content().stream())
                .forEach(content -> sb.append(content.text()));

        return buildLlmResponse(sb.toString(),false);
    }

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

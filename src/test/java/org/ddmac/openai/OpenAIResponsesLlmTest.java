package org.ddmac.openai;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.adk.models.LlmRequest;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SystemStubsExtension.class)
public class OpenAIResponsesLlmTest {

    @SystemStub
    private EnvironmentVariables env = new EnvironmentVariables("OPENAI_API_KEY","DeFiNiTeLyStIlLaKeY");

    private WireMockServer mockWebServer;
    private OpenAIResponsesLlm llm;

    private Content testMsg = Content.builder()
            .role("user")
            .parts(List.of(
                    Part.builder().text("test").build()
            ))
            .build();

    @BeforeEach
    void setup() throws IOException {
        mockWebServer = new WireMockServer(0);
        mockWebServer.start();

        llm = new OpenAIResponsesLlm("test",mockWebServer.baseUrl());
    }

    @AfterEach
    void teardown() throws IOException {
        mockWebServer.shutdown();
    }

    // helper function to grab payloads from resources.
    private String loadResource(String fName){
        try {
            URL resource =this.getClass().getResource(fName);
            if (resource == null){
                throw new IllegalArgumentException("File not found: " + fName);
            }
            Path path = Paths.get(resource.toURI());
            return Files.readString(path);
        } catch (Exception e) {
            throw new RuntimeException("failed to load payload",e);
        }
    }

    @Test
    void shouldInitForLocalUnauthenticated(){
        assertThatCode(() -> new OpenAIResponsesLlm("llama3","http://localhost:11434"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldInitForExplicitAPIKey(){
        assertThatCode(() -> new OpenAIResponsesLlm("gpt-o4","https://api.openai.com","DeFiNiTeLyAkEy"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldInitForEnvVariable(){
        assertThatCode(() -> new OpenAIResponsesLlm("gpt-o4"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldFailAuthNoKeyNoEnv(){
        String currentEnvKey = System.getenv("OPENAI_API_KEY");
        if (currentEnvKey == null || currentEnvKey.isBlank()) {
            assertThatThrownBy(() -> new OpenAIResponsesLlm("gpt-4o", "https://api.openai.com", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Missing API Key");

            assertThatThrownBy(() -> new OpenAIResponsesLlm("gpt-4o"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Missing API Key");
        }
    }


    @Test
    void shouldHandleSingleResponseSuccess(){
        String mockRes = loadResource("/mock-responses/single-response.json");

        mockWebServer.stubFor(
                post(
                        urlEqualTo("/v1/responses")
                )
                .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type","application/json")
                            .withBody(mockRes)
                )
        );

        LlmRequest request = LlmRequest.builder()
                .contents(List.of(testMsg))
                .build();

        TestSubscriber<LlmResponse> testSubscriber = llm.generateContent(request,false).test();

        testSubscriber.awaitDone(5, TimeUnit.SECONDS)
                .assertNoErrors()
                .assertComplete()
                .assertValueCount(1);

        LlmResponse finalRes = testSubscriber.values().getFirst();

        String t = finalRes
                .content()
                .flatMap(Content::parts)
                .orElseThrow()
                .getFirst()
                .text()
                .orElseThrow();

        assertThat(t).isEqualTo("Hello, World!");
        assertThat(finalRes.partial().orElseThrow()).isFalse();
        assertThat(finalRes.turnComplete().orElseThrow()).isTrue();
    }

    @Test
    void shouldHandleStreamResponseSuccess(){
        String mockSseStream = loadResource("/mock-responses/sse-response.txt");

        mockWebServer.stubFor(
                post(urlEqualTo("/v1/responses"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type","text/event-stream")
                                        .withBody(mockSseStream)
                        )
        );


        LlmRequest req = LlmRequest.builder()
                .contents(List.of(testMsg))
                .build();

        TestSubscriber<LlmResponse> testSubscriber = llm.generateContent(req,true).test();

        testSubscriber.awaitDone(5,TimeUnit.SECONDS)
                .assertNoErrors()
                .assertComplete()
                .assertValueCount(4);

        List<String> valid = List.of("Hello",", ","World!");

        for(int i = 0; i < valid.size(); i++){
            LlmResponse value = testSubscriber.values().get(i);
            String chunk = value.content()
                    .flatMap(Content::parts)
                    .orElseThrow()
                    .getFirst()
                    .text()
                    .orElseThrow();

            assertThat(chunk).isEqualTo(valid.get(i));
        }

        LlmResponse finalRes = testSubscriber.values().get(3);
        assertThat(finalRes.partial().orElseThrow()).isFalse();
        assertThat(finalRes.turnComplete().orElseThrow()).isTrue();

    }

    @Test
    void shouldEmitErroOnFailureInStream(){
        mockWebServer.stubFor(
                post(
                        urlEqualTo("/v1/responses")
                ).willReturn(
                        aResponse()
                                .withStatus(500)
                )
        );

        LlmRequest req = LlmRequest.builder()
                .contents(List.of(testMsg))
                .build();

        TestSubscriber<LlmResponse> testSubscriber = llm.generateContent(req,true).test();

        testSubscriber.awaitDone(5, TimeUnit.SECONDS)
                .assertError(RuntimeException.class)
                .assertNotComplete();
    }

    @Test
    void shouldThrowUnsupportedOperationForBidirectionalConnect() {
        LlmRequest request = LlmRequest.builder().build();

        assertThatThrownBy(() -> llm.connect(request))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Bidirectional live streaming is not supported");
    }
}

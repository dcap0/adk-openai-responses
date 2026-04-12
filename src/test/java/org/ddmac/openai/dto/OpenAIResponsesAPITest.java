package org.ddmac.openai.dto;

import org.ddmac.openai.exception.OpenAIBuildException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OpenAIResponsesAPITest {

    @Test
    void shouldBuildValidSingleRequest(){
        OpenAIResponsesAPIRequest<String> req = OpenAIResponsesAPIRequest
                .<String>builder()
                .model("gpt-4o")
                .input("hello, world!")
                .stream(false)
                .build();

        assertThat(req.model()).isEqualTo("gpt-4o");
        assertThat(req.input()).isEqualTo("hello, world!");
        assertThat(req.stream()).isFalse();
    }

    @Test
    void shouldBuildValidStreamRequest(){
        List<OpenAIResponsesAPIRequest.Input> inputs = List.of(
                new OpenAIResponsesAPIRequest.Input("user",List.of(new Content("text","hello, world!")))
        );

        OpenAIResponsesAPIRequest<List<OpenAIResponsesAPIRequest.Input>> req = OpenAIResponsesAPIRequest.builder()
                .model("gemma-3b")
                .input(inputs)
                .stream(true)
                .instructions("do stuff")
                .build();

        assertThat(req.input()).hasSize(1);
        assertThat(req.instructions()).isEqualTo("do stuff");
    }

    @Test
    void shouldThrowExceptionWhenModelIsMissing() {
        assertThatThrownBy(() -> OpenAIResponsesAPIRequest.<String>builder()
                .input("Hello")
                .build()
        ).isInstanceOf(OpenAIBuildException.class)
                .hasMessageContaining("No model provided");
    }

    @Test
    void shouldThrowExceptionWhenInputIsMissing() {
        assertThatThrownBy(() -> OpenAIResponsesAPIRequest.<String>builder()
                .model("gpt-4o")
                .build()
        ).isInstanceOf(OpenAIBuildException.class)
                .hasMessageContaining("No input provided");
    }

    @Test
    void shouldThrowExceptionWhenInputListIsEmpty() {
        assertThatThrownBy(() -> OpenAIResponsesAPIRequest.<List<OpenAIResponsesAPIRequest.Input>>builder()
                .model("gpt-4o")
                .input(List.of()) // Empty list
                .build()
        ).isInstanceOf(OpenAIBuildException.class)
                .hasMessageContaining("Input list is empty");
    }
}

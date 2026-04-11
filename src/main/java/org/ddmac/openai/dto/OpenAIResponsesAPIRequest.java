package org.ddmac.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ddmac.openai.exception.OpenAIBuildException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAIResponsesAPIRequest<T> {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIResponsesAPIRequest.class);

    @JsonProperty("model")
    private final String model;

    @JsonProperty("input")
    private final T input;

    @JsonProperty("instructions")
    private final String instructions;

    @JsonProperty("stream")
    private final boolean stream;

    private OpenAIResponsesAPIRequest(Builder<T> builder) {
        this.model = builder.model;
        this.input = builder.input;
        this.instructions = builder.instructions;
        this.stream = builder.stream;
    }


    public String getModel() {
        return model;
    }

    public T getInput() {
        return input;
    }

    public String getInstructions() {
        return instructions;
    }

    public boolean isStream() {
        return stream;
    }

    public String toJson() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize request", e);
            return "{}";
        }
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static class Builder<T> {
        private String model;
        private T input;
        private String instructions;
        private boolean stream;

        public Builder<T> model(String model) {
            this.model = model;
            return this;
        }

        @SuppressWarnings("unchecked")
        public Builder<String> input(String input) {
            if (this.input != null) {
                throw new IllegalStateException("Input has already been set, you cannot set it twice");
            }
            this.input = (T) input;
            return (Builder<String>) this;
        }

        @SuppressWarnings("unchecked")
        public Builder<List<Input>> input(List<Input> input) {
            if (this.input != null) {
                throw new IllegalStateException("Input has already been set, you cannot set it twice");
            }
            this.input = (T) input;
            return (Builder<List<Input>>) this;
        }

        public Builder<T> instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        public Builder<T> stream(boolean stream) {
            this.stream = stream;
            return this;
        }

        private void validateFields() {
            if (this.model == null || this.model.isEmpty() || this.model.isBlank()) {
                throw new OpenAIBuildException("No model provided");
            }

            if (this.input == null) {
                throw new OpenAIBuildException("No input provided");
            }

            if (this.input instanceof String && (((String) this.input).isEmpty() || ((String) this.input).isBlank())) {
                throw new OpenAIBuildException("String input expected, but not provided");
            }

            if (this.input instanceof List<?>) {
                if (((List<?>) this.input).isEmpty()) {
                    throw new OpenAIBuildException("Input list is empty");
                }
            }


            if (this.stream && (this.instructions == null || this.instructions.isEmpty() || this.instructions.isBlank())) {
                logger.warn("No instructions provided. AI will act in a general sense");
            }
            
        }

        public OpenAIResponsesAPIRequest<T> build() {
            validateFields();
            return new OpenAIResponsesAPIRequest<>(this);
        }

    }

    public static class Input {
        @JsonProperty("role")
        private String role;

        @JsonProperty("content")
        private List<Content> content;

        public Input() {
        }

        public Input(String role, List<Content> content) {
            this.role = role;
            this.content = content;
        }
    }

    @Override
    public String toString() {
        return toJson();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.model, this.input, this.instructions, this.stream);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        OpenAIResponsesAPIRequest<?> that = (OpenAIResponsesAPIRequest<?>) o;
        return stream == that.stream && Objects.equals(model, that.model) && Objects.equals(input, that.input) && Objects.equals(instructions, that.instructions);
    }
}

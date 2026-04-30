package org.ddmac.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ddmac.openai.exception.OpenAIBuildException;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Represents the outgoing JSON request payload for the OpenAI Responses API.
 * <p>
 * This immutable record encapsulates the configuration and prompt data required to generate a
 * model response. It utilizes a generic type to support both simple string prompts
 * and complex, multi-turn chat histories. Instances of this class should be constructed
 * using the provided {@link Builder} to ensure data validity.
 *
 * @param model        The identifier of the model being requested (e.g., "gpt-4o").
 * @param input        The input payload for the model. Must be either a {@link String} or a {@code List<Input>}.
 * @param instructions The system instructions or overarching prompt guiding the model's behavior.
 * @param stream       {@code true} if the request asks for Server-Sent Events (SSE) streaming; {@code false} otherwise.
 * @param <T>          The type of the input payload.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAIResponsesAPIRequest<T>(
        @JsonProperty("model") String model,
        @JsonProperty("input") T input,
        @JsonProperty("instructions") String instructions,
        @JsonProperty("stream") boolean stream,
        @JsonProperty("tools") List<Tool> tools
) {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIResponsesAPIRequest.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Serializes this request record into a valid JSON string for transmission.
     *
     * @return The JSON string representation, or {@code "{}"} if serialization fails.
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize request", e);
            return "{}";
        }
    }

    /**
     * Creates a new Builder for constructing an {@code OpenAIResponsesAPIRequest}.
     *
     * @param <T> The anticipated type of the input (String or List of Inputs).
     * @return A new {@link Builder} instance.
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Builder class for safely constructing {@link OpenAIResponsesAPIRequest} instances.
     * Ensures that all required fields are present and valid before instantiation.
     *
     * @param <T> The type of the input payload.
     */
    public static class Builder<T> {
        private String model;
        private T input;
        private String instructions;
        private boolean stream;
        private List<Tool> tools;

        private Builder(){}

        /**
         * Sets the target model.
         *
         * @param model The model identifier.
         * @return This builder instance.
         */
        public Builder<T> model(String model) {
            this.model = model;
            return this;
        }

        /**
         * Sets the input as a simple, single-turn string prompt.
         *
         * @param input The text prompt.
         * @return This builder, cast to expect a String input.
         * @throws IllegalStateException If the input has already been configured.
         */
        @SuppressWarnings("unchecked")
        public Builder<String> input(String input) {
            if (this.input != null) {
                throw new IllegalStateException("Input has already been set, you cannot set it twice");
            }
            this.input = (T) input;
            return (Builder<String>) this;
        }

        /**
         * Sets the input as a structured list of conversation turns.
         *
         * @param input A list of {@link Input} message objects.
         * @return This builder, cast to expect a List of Inputs.
         * @throws IllegalStateException If the input has already been configured.
         */
        @SuppressWarnings("unchecked")
        public Builder<List<Input>> input(List<Input> input) {
            if (this.input != null) {
                throw new IllegalStateException("Input has already been set, you cannot set it twice");
            }
            this.input = (T) input;
            return (Builder<List<Input>>) this;
        }

        /**
         * Sets the overarching system instructions.
         *
         * @param instructions The system prompt or instructions.
         * @return This builder instance.
         */
        public Builder<T> instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        /**
         * Configures whether the response should be streamed.
         *
         * @param stream {@code true} for SSE streaming, {@code false} for a single synchronous response.
         * @return This builder instance.
         */
        public Builder<T> stream(boolean stream) {
            this.stream = stream;
            return this;
        }

        /**
         * Provides a list of tools for the agent.
         *
         * @param tools A list of tools for the agent to have access to.
         * @return This builder instance
         */
        public Builder<T> tools(List<Tool> tools){
            this.tools = tools;
            return this;
        }

        /**
         * Validates the internal state of the builder before constructing the request.
         *
         * @throws OpenAIBuildException If required fields are missing, empty, or of an invalid type.
         */
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
                if (!(((List<?>) this.input).getFirst() instanceof Input)) { //eww
                    throw new OpenAIBuildException("List must be list of Input objects");
                }
            }

            if (!(this.input instanceof List<?>) && !(this.input instanceof String)) {
                throw new OpenAIBuildException("Invalid input type. String or List<Input> required");
            }


            if (this.stream && (this.instructions == null || this.instructions.isEmpty() || this.instructions.isBlank())) {
                logger.warn("No instructions provided. AI will act in a general sense");
            }

        }

        /**
         * Validates the configuration and builds the final request record.
         *
         * @return A fully constructed, immutable {@link OpenAIResponsesAPIRequest}.
         * @throws OpenAIBuildException If the configuration is invalid.
         */
        public OpenAIResponsesAPIRequest<T> build() {
            validateFields();
            return new OpenAIResponsesAPIRequest<>(model,input,instructions,stream,tools);
        }

    }

    /**
     * Represents a single turn or message within a structured conversation payload.
     *
     * @param role    The role of the author of this message (e.g., "user", "assistant", "system").
     * @param content A list of {@link Content} blocks comprising the actual message payload.
     */
    public record Input(
            @JsonProperty("role") String role,
            @JsonProperty("content") List<Content> content
    ) {
    }

    /**
     * Returns the string representation of this request record.
     *
     * @return The JSON string generated by {@link #toJson()}.
     */
    @Override
    public @NonNull String toString() {
        return toJson();
    }
}

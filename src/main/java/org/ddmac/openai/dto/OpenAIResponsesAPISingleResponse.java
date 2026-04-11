package org.ddmac.openai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAIResponsesAPISingleResponse(
        @JsonProperty("output")
        List<Output> output,
        Usage usage
) {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIResponsesAPISingleResponse.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();


    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Output(
            @JsonProperty("content") List<Content> content,
            @JsonProperty("role") String role,
            @JsonProperty("type") String type
    ) {
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize response", e);
            return "{}";
        }
    }

    @Override
    public String toString() {
        return toJson();
    }

    public List<Output> getOutput() {
        return output;
    }
}

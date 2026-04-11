package org.ddmac.openai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//really basic Responses API call
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAIResponsesAPIStreamingResponse(
        @JsonProperty("delta") String delta,
        @JsonProperty("text") String text,
        @JsonProperty("response") ResponseData response
) {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIResponsesAPIStreamingResponse.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResponseData(
            String id,
            String status,
            @JsonProperty("usage") Usage usage,
            @JsonProperty("output_text") String outputText
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

}

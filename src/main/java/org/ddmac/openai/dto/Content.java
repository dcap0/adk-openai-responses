package org.ddmac.openai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Content(
        @JsonProperty("type") String type,
        @JsonProperty("text") String text
) {
    private static final Logger logger = LoggerFactory.getLogger(Content.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize", e);
            return "{}";
        }
    }

    @Override
    public @NonNull String toString() {
        return toJson();
    }

}

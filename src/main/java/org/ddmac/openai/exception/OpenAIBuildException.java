package org.ddmac.openai.exception;

/**
 * A runtime exception indicating that an OpenAI API request could not be safely constructed.
 * <p>
 * This exception is typically thrown during the validation phase of request building
 * (such as within the compact constructor of {@link org.ddmac.openai.dto.OpenAIResponsesAPIRequest})
 * when required fields are found to be missing, empty, or of an invalid type.
 */
public class OpenAIBuildException extends RuntimeException {

    /**
     * Constructs a new {@code OpenAIBuildException} with the specified detail message.
     *
     * @param message The detail message explaining the specific reason the build process failed
     * (e.g., "No model provided" or "Input list is empty").
     */
    public OpenAIBuildException(String message) {
        super(message);
    }
}

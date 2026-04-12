package org.ddmac.openai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SystemStubsExtension.class)
public class OpenAIResponsesLlmTest {

    @SystemStub
    private EnvironmentVariables env = new EnvironmentVariables("OPENAI_API_KEY","DeFiNiTeLyStIlLaKeY");

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
}

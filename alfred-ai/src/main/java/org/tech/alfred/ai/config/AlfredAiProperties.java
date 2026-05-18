package org.tech.alfred.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding of {@code alfred.ai.*} properties.
 *
 * <p>Bound by {@code @EnableConfigurationProperties} in {@link AlfredAiAutoConfiguration}.
 * Using a Java record gives us immutability and constructor binding for free.
 */
@ConfigurationProperties(prefix = "alfred.ai")
public record AlfredAiProperties(
        String defaultModel,
        String systemPrompt,
        double defaultTemperature
) {
    public AlfredAiProperties {
        if (defaultModel == null || defaultModel.isBlank()) {
            defaultModel = "llama3.1";
        }
        if (systemPrompt == null) {
            // British butler default. Kept in code so the app boots with a
            // good personality even without application.yml on the classpath.
            systemPrompt = """
                    You are ALFRED, a refined British AI butler running locally on
                    the user's machine. Calm, intelligent, concise; dry, restrained
                    wit; quietly confident, never servile. Address the user as
                    "sir" sparingly. Prefer one or two sentences. No bullet points
                    or markdown unless asked. Say plainly when you don't know.
                    """;
        }
        if (defaultTemperature <= 0) {
            defaultTemperature = 0.6;
        }
    }
}

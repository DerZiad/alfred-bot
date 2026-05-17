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
            systemPrompt = """
                    You are Alfred, a helpful local AI assistant running on the user's machine.
                    Be concise, accurate, and proactive. When unsure, say so.
                    """;
        }
        if (defaultTemperature <= 0) {
            defaultTemperature = 0.7;
        }
    }
}

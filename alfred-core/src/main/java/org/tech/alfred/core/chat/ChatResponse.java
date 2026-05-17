package org.tech.alfred.core.chat;

import java.util.Objects;

/** Final, non-streaming chat response. */
public record ChatResponse(
        Message message,
        Usage usage
) {
    public ChatResponse {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(usage, "usage");
    }

    /** Token usage telemetry. */
    public record Usage(int promptTokens, int completionTokens) {
        public int totalTokens() {
            return promptTokens + completionTokens;
        }
    }
}

package org.tech.alfred.core.chat;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Inbound request to a {@link ChatService}.
 *
 * @param messages  Full prompt history (system + user + assistant turns).
 * @param model     Optional override of the configured default model.
 * @param options   Per-call generation options (temperature, max tokens, ...).
 */
public record ChatRequest(
        List<Message> messages,
        Optional<String> model,
        ChatOptions options
) {
    public ChatRequest {
        Objects.requireNonNull(messages, "messages");
        messages = List.copyOf(messages);
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(options, "options");
    }

    public static ChatRequest of(List<Message> messages) {
        return new ChatRequest(messages, Optional.empty(), ChatOptions.defaults());
    }
}

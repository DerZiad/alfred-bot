package org.tech.alfred.core.chat;

/**
 * Provider-neutral generation options.
 *
 * <p>Concrete adapters translate these into their backend's native shape
 * (Ollama options, OpenAI parameters, ...). Unknown / unsupported options
 * are silently ignored by the adapter.
 */
public record ChatOptions(
        double temperature,
        int maxTokens,
        double topP
) {
    public static ChatOptions defaults() {
        return new ChatOptions(0.7, 2048, 0.9);
    }
}

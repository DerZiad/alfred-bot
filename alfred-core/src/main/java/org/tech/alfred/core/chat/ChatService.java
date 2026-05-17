package org.tech.alfred.core.chat;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Provider-neutral conversational AI SPI.
 *
 * <p>Two methods, two modes:
 * <ul>
 *   <li>{@link #chat(ChatRequest)} - request/response, returns a {@link Mono}
 *       resolving to the complete answer.</li>
 *   <li>{@link #stream(ChatRequest)} - streams tokens as a {@link Flux} so
 *       the UI can render the response progressively.</li>
 * </ul>
 *
 * <p>Implementations live in alfred-ai (Ollama via Spring AI) and may later
 * include OpenAI, vLLM, LM Studio, etc.
 */
public interface ChatService {

    Mono<ChatResponse> chat(ChatRequest request);

    Flux<String> stream(ChatRequest request);
}

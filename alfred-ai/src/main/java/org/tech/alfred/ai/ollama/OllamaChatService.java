package org.tech.alfred.ai.ollama;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.tech.alfred.ai.config.AlfredAiProperties;
import org.tech.alfred.core.chat.ChatRequest;
import org.tech.alfred.core.chat.ChatResponse;
import org.tech.alfred.core.chat.Message;
import org.tech.alfred.core.chat.Role;
import org.tech.alfred.core.chat.ChatService;

/**
 * {@link ChatService} implementation backed by Spring AI's {@link ChatClient}
 * against Ollama.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Translate our provider-neutral {@link ChatRequest} into a Spring AI
 *       {@link Prompt} (role mapping, options translation).</li>
 *   <li>Expose both unary ({@link Mono}) and streaming ({@link Flux}) variants.</li>
 *   <li>Log timings and token usage for observability.</li>
 * </ul>
 *
 * <p>Kept deliberately thin: business logic (memory injection, tool dispatch,
 * guardrails) belongs in a higher-level orchestrator, not in this adapter.
 */
public class OllamaChatService implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(OllamaChatService.class);

    private final ChatClient chatClient;
    private final AlfredAiProperties props;

    public OllamaChatService(ChatClient chatClient, AlfredAiProperties props) {
        this.chatClient = chatClient;
        this.props = props;
    }

    @Override
    public Mono<ChatResponse> chat(ChatRequest request) {
        long started = System.nanoTime();
        ChatClient.ChatClientRequestSpec spec = buildSpec(request);
        log.debug(request.toString());
        return Mono.fromCallable(() -> spec.call().content())
                .map(text -> new ChatResponse(
                        Message.assistant(text),
                        // TODO populate real token usage when Spring AI surfaces it for Ollama.
                        new ChatResponse.Usage(0, 0)))
                .doOnSuccess(r -> log.debug(
                        "chat() completed in {}",
                        Duration.ofNanos(System.nanoTime() - started)));
    }

    @Override
    public Flux<String> stream(ChatRequest request) {
        return buildSpec(request).stream().content();
    }

    private ChatClient.ChatClientRequestSpec buildSpec(ChatRequest request) {
        String userTurn = request.messages().stream()
                .filter(m -> m.role() == Role.USER)
                .map(Message::content)
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");

        ChatClient.ChatClientRequestSpec spec = chatClient.prompt().user(userTurn);
        request.model().ifPresent(model ->
                // Future: per-call model override via OllamaOptions
                log.debug("Per-call model override requested: {} (not yet applied)", model));
        return spec;
    }
}

package org.tech.alfred.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import org.tech.alfred.ai.ollama.OllamaChatService;
import org.tech.alfred.core.chat.ChatService;

/**
 * Auto-configuration for alfred-ai.
 *
 * <p>Registered via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * Activates only when an {@link OllamaChatModel} is on the classpath (i.e. the
 * Spring AI Ollama starter is present) - keeping the door open for swapping in
 * other adapters without changing alfred-ui.
 */
@AutoConfiguration
@ConditionalOnClass(OllamaChatModel.class)
@EnableConfigurationProperties(AlfredAiProperties.class)
public class AlfredAiAutoConfiguration {

    /**
     * Pre-built {@link ChatClient} with our default system prompt applied.
     * Adapters can override on a per-call basis.
     */
    @Bean
    public ChatClient alfredChatClient(OllamaChatModel ollama, AlfredAiProperties props) {
        return ChatClient.builder(ollama)
                .defaultSystem(props.systemPrompt())
                .build();
    }

    @Bean
    public ChatService chatService(ChatClient client, AlfredAiProperties props) {
        return new OllamaChatService(client, props);
    }
}

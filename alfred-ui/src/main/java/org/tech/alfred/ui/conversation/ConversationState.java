package org.tech.alfred.ui.conversation;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Session-scoped conversation state. Currently one conversation per process;
 * a future "conversations sidebar" will let users switch by ID.
 */
@Component
public class ConversationState {

    private volatile UUID conversationId = UUID.randomUUID();

    public UUID conversationId() {
        return conversationId;
    }

    public void newConversation() {
        this.conversationId = UUID.randomUUID();
    }
}

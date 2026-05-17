package org.tech.alfred.core.chat;

/** Speaker role within a conversation. Mirrors the OpenAI/Ollama convention. */
public enum Role {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

package org.tech.alfred.core.chat;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Single chat message. Immutable value type implemented as a Java record.
 *
 * <p>{@code id} and {@code timestamp} are auto-populated via the convenience
 * factory methods - use {@link #user(String)}, {@link #assistant(String)},
 * {@link #system(String)} from application code.
 */
public record Message(
        UUID id,
        Role role,
        String content,
        Instant timestamp
) {
    public Message {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    public static Message system(String content) {
        return new Message(UUID.randomUUID(), Role.SYSTEM, content, Instant.now());
    }

    public static Message user(String content) {
        return new Message(UUID.randomUUID(), Role.USER, content, Instant.now());
    }

    public static Message assistant(String content) {
        return new Message(UUID.randomUUID(), Role.ASSISTANT, content, Instant.now());
    }
}

package org.tech.alfred.core.memory;

import java.time.Instant;
import java.util.UUID;

import org.tech.alfred.core.chat.Message;

/** A single persisted conversation turn. */
public record MemoryEntry(UUID conversationId, Message message, Instant persistedAt) {}

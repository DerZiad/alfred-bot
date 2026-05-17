package org.tech.alfred.core.memory;

import java.util.List;
import java.util.UUID;

/** Persistent conversation memory SPI. Phase 1: chronological. Phase 2: vector recall. */
public interface MemoryStore {

    void save(MemoryEntry entry);

    List<MemoryEntry> recent(UUID conversationId, int limit);

    List<UUID> conversations();
}

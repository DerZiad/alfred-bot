package org.tech.alfred.memory.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.tech.alfred.core.chat.Message;
import org.tech.alfred.core.chat.Role;
import org.tech.alfred.core.memory.MemoryEntry;
import org.tech.alfred.core.memory.MemoryStore;

/**
 * SQLite-backed conversation memory.
 *
 * <p>Schema is intentionally minimal so we can ship Phase 1 quickly and evolve.
 * Phase 2 will add an {@code embeddings} table (vector BLOB + dim metadata)
 * for semantic recall, and a swap to pgvector when running with Postgres.
 */
public class SqliteMemoryStore implements MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(SqliteMemoryStore.class);
    private static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS conversation_messages (
                id              TEXT PRIMARY KEY,
                conversation_id TEXT NOT NULL,
                role            TEXT NOT NULL,
                content         TEXT NOT NULL,
                created_at      INTEGER NOT NULL,
                persisted_at    INTEGER NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_conv_msgs_conv_time
                ON conversation_messages(conversation_id, created_at DESC);
            """;

    private final DataSource ds;

    public SqliteMemoryStore(DataSource ds) {
        this.ds = ds;
    }

    public void init() {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            for (String stmt : SCHEMA.split(";")) {
                String trimmed = stmt.trim();
                if (!trimmed.isEmpty()) s.execute(trimmed);
            }
            log.info("SQLite memory store initialized");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize memory schema", e);
        }
    }

    @Override
    public void save(MemoryEntry entry) {
        String sql = """
                INSERT INTO conversation_messages
                    (id, conversation_id, role, content, created_at, persisted_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            Message m = entry.message();
            ps.setString(1, m.id().toString());
            ps.setString(2, entry.conversationId().toString());
            ps.setString(3, m.role().name());
            ps.setString(4, m.content());
            ps.setLong(5, m.timestamp().toEpochMilli());
            ps.setLong(6, entry.persistedAt().toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist message", e);
        }
    }

    @Override
    public List<MemoryEntry> recent(UUID conversationId, int limit) {
        String sql = """
                SELECT id, role, content, created_at, persisted_at
                FROM conversation_messages
                WHERE conversation_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;
        List<MemoryEntry> out = new ArrayList<>(limit);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conversationId.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Message m = new Message(
                            UUID.fromString(rs.getString("id")),
                            Role.valueOf(rs.getString("role")),
                            rs.getString("content"),
                            Instant.ofEpochMilli(rs.getLong("created_at")));
                    out.add(new MemoryEntry(
                            conversationId, m,
                            Instant.ofEpochMilli(rs.getLong("persisted_at"))));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load recent messages", e);
        }
        return out;
    }

    @Override
    public List<UUID> conversations() {
        Set<UUID> seen = new HashSet<>();
        try (Connection c = ds.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT DISTINCT conversation_id FROM conversation_messages")) {
            while (rs.next()) seen.add(UUID.fromString(rs.getString(1)));
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list conversations", e);
        }
        return new ArrayList<>(seen);
    }
}

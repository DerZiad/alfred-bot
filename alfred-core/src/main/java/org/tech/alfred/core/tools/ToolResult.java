package org.tech.alfred.core.tools;

import java.util.UUID;

/** Result of executing a {@link ToolCall}. */
public record ToolResult(UUID callId, String toolName, String content, boolean error) {
    public static ToolResult ok(UUID callId, String toolName, String content) {
        return new ToolResult(callId, toolName, content, false);
    }

    public static ToolResult fail(UUID callId, String toolName, String message) {
        return new ToolResult(callId, toolName, message, true);
    }
}

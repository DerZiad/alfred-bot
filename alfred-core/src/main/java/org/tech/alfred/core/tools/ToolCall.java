package org.tech.alfred.core.tools;

import java.util.Map;
import java.util.UUID;

/** A single tool invocation request from the LLM. */
public record ToolCall(UUID id, String toolName, Map<String, Object> arguments) {}

package org.tech.alfred.core.tools;

/**
 * A callable capability exposed to the LLM.
 *
 * <p>Implementations are auto-discovered by the registry (alfred-tools)
 * as Spring beans. Keep tools idempotent and side-effect-explicit; the LLM
 * may choose to call them in unexpected orders.
 */
public interface Tool {

    ToolSpec spec();

    ToolResult execute(ToolCall call);
}

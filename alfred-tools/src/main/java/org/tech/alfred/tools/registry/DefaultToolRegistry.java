package org.tech.alfred.tools.registry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.tech.alfred.core.tools.Tool;
import org.tech.alfred.core.tools.ToolCall;
import org.tech.alfred.core.tools.ToolRegistry;
import org.tech.alfred.core.tools.ToolResult;

/**
 * Spring-DI-backed registry. All beans implementing {@link Tool} are
 * auto-discovered at construction time and indexed by {@code spec().name()}.
 *
 * <p>External plugins register via the same path: add a Spring-discovered
 * {@code Tool} bean and the registry picks it up.
 */
public class DefaultToolRegistry implements ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultToolRegistry.class);

    private final Map<String, Tool> byName = new LinkedHashMap<>();

    public DefaultToolRegistry(List<Tool> tools) {
        for (Tool t : tools) {
            String name = t.spec().name();
            if (byName.putIfAbsent(name, t) != null) {
                throw new IllegalStateException("Duplicate tool name: " + name);
            }
            log.info("Registered tool: {}", name);
        }
    }

    @Override
    public Collection<Tool> all() {
        return byName.values();
    }

    @Override
    public Optional<Tool> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    @Override
    public ToolResult execute(ToolCall call) {
        UUID callId = call.id() != null ? call.id() : UUID.randomUUID();
        Tool tool = byName.get(call.toolName());
        if (tool == null) {
            return ToolResult.fail(callId, call.toolName(), "Unknown tool: " + call.toolName());
        }
        try {
            return tool.execute(call);
        } catch (RuntimeException e) {
            log.warn("Tool {} failed", call.toolName(), e);
            return ToolResult.fail(callId, call.toolName(), e.getMessage());
        }
    }
}

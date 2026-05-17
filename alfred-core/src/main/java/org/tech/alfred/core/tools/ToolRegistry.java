package org.tech.alfred.core.tools;

import java.util.Collection;
import java.util.Optional;

/** Lookup and execution of registered {@link Tool}s. */
public interface ToolRegistry {

    Collection<Tool> all();

    Optional<Tool> find(String name);

    ToolResult execute(ToolCall call);
}

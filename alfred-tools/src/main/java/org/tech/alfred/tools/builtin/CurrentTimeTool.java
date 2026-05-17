package org.tech.alfred.tools.builtin;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import org.tech.alfred.core.tools.Tool;
import org.tech.alfred.core.tools.ToolCall;
import org.tech.alfred.core.tools.ToolResult;
import org.tech.alfred.core.tools.ToolSpec;

/** Sample built-in tool. Returns the current wall-clock time in the requested time zone. */
public class CurrentTimeTool implements Tool {

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "current_time",
                "Returns the current date and time in the requested IANA time zone (defaults to system).",
                Map.of("timezone", new ToolSpec.ParameterSpec(
                        "string", "IANA zone id, e.g. America/Los_Angeles", false)));
    }

    @Override
    public ToolResult execute(ToolCall call) {
        UUID id = call.id() != null ? call.id() : UUID.randomUUID();
        Object tz = call.arguments() != null ? call.arguments().get("timezone") : null;
        ZoneId zone = tz instanceof String s && !s.isBlank() ? ZoneId.of(s) : ZoneId.systemDefault();
        return ToolResult.ok(id, spec().name(), ZonedDateTime.now(zone).toString());
    }
}

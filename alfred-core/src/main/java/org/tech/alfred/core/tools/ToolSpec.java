package org.tech.alfred.core.tools;

import java.util.Map;

/**
 * Static description of a callable tool. Serializable to an LLM
 * function-calling schema (OpenAI / Ollama tool format).
 */
public record ToolSpec(
        String name,
        String description,
        Map<String, ParameterSpec> parameters
) {
    public record ParameterSpec(String jsonType, String description, boolean required) {}
}

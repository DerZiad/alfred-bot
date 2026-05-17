package org.tech.alfred.tools.config;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import org.tech.alfred.core.tools.Tool;
import org.tech.alfred.core.tools.ToolRegistry;
import org.tech.alfred.tools.builtin.CurrentTimeTool;
import org.tech.alfred.tools.registry.DefaultToolRegistry;

@AutoConfiguration
public class AlfredToolsAutoConfiguration {

    @Bean
    public Tool currentTimeTool() {
        return new CurrentTimeTool();
    }

    @Bean
    public ToolRegistry toolRegistry(List<Tool> tools) {
        return new DefaultToolRegistry(tools);
    }
}

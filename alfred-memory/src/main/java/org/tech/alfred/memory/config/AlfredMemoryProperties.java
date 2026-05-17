package org.tech.alfred.memory.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alfred.memory")
public record AlfredMemoryProperties(String databasePath) {
    public AlfredMemoryProperties {
        if (databasePath == null || databasePath.isBlank()) {
            databasePath = Paths.get(System.getProperty("user.home"), ".alfred", "memory.db").toString();
        }
    }

    public Path path() {
        return Paths.get(databasePath);
    }
}

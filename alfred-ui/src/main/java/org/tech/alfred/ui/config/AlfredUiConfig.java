package org.tech.alfred.ui.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.tech.alfred.ui.voice.AudioLevelMeter;

/**
 * UI-layer bean registrations. The cinematic HUD components are built
 * in Java (not Spring beans - they're scene-graph nodes), but a couple
 * of helpers want to be singletons:
 * <ul>
 *   <li>{@link AudioLevelMeter} - one meter, multiple consumers.</li>
 * </ul>
 */
@Configuration
public class AlfredUiConfig {

    @Bean
    public AudioLevelMeter audioLevelMeter() {
        return new AudioLevelMeter();
    }
}

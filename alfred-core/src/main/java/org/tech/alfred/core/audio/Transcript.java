package org.tech.alfred.core.audio;

import java.time.Duration;

/** STT output: recognized text plus confidence + duration metadata. */
public record Transcript(
        String text,
        double confidence,
        Duration audioDuration
) {}

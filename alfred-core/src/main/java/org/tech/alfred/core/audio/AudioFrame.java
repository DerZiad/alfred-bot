package org.tech.alfred.core.audio;

import java.util.Objects;

/**
 * A chunk of raw PCM audio.
 *
 * <p>{@code samples} is little-endian signed PCM matching {@link AudioFormatSpec}.
 * Frames are typically 20-100ms wide for streaming use cases.
 */
public record AudioFrame(byte[] samples, AudioFormatSpec format) {
    public AudioFrame {
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(format, "format");
    }
}

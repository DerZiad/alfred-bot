package org.tech.alfred.core.audio;

/**
 * Provider-neutral audio format descriptor.
 *
 * <p>Whisper expects 16 kHz, mono, 16-bit PCM. We standardize on that
 * across the pipeline to avoid resampling.
 */
public record AudioFormatSpec(
        int sampleRateHz,
        int channels,
        int bitsPerSample
) {
    public static final AudioFormatSpec WHISPER_DEFAULT =
            new AudioFormatSpec(16_000, 1, 16);
}

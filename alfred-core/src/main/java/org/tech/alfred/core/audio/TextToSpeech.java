package org.tech.alfred.core.audio;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Text-to-speech SPI.
 *
 * <p>{@link #synthesize(String)} returns a complete WAV-encoded buffer.
 * {@link #stream(String)} streams audio frames as they are generated -
 * useful for low-latency playback of long responses.
 */
public interface TextToSpeech {

    Mono<byte[]> synthesize(String text);

    Flux<AudioFrame> stream(String text);
}

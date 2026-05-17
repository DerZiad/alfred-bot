package org.tech.alfred.core.wake;

import reactor.core.publisher.Flux;

import org.tech.alfred.core.audio.AudioFrame;

/**
 * Wake-word detection SPI ("Hey Alfred").
 *
 * <p>Implementations consume a continuous audio stream and emit an event
 * each time the wake word is detected.
 */
public interface WakeWordDetector {

    Flux<WakeEvent> listen(Flux<AudioFrame> frames);

    record WakeEvent(String phrase, double confidence, long timestampMillis) {}
}

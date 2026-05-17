package org.tech.alfred.core.audio;

import reactor.core.publisher.Flux;

/**
 * Microphone (or other source) audio capture SPI.
 *
 * <p>Capture is lifecycle-managed: callers {@link #start()} to receive a
 * {@link Flux} of frames, and {@link #stop()} to release the hardware.
 */
public interface AudioCapture {

    Flux<AudioFrame> start();

    void stop();

    boolean isCapturing();
}

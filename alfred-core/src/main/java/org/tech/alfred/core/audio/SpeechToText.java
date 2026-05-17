package org.tech.alfred.core.audio;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Speech-to-text SPI. Two operating modes:
 * <ul>
 *   <li>{@link #transcribe(byte[], AudioFormatSpec)} - one-shot: full buffer in,
 *       single transcript out (Whisper file mode).</li>
 *   <li>{@link #transcribeStream(Flux)} - streaming: progressive partial
 *       transcripts as audio frames arrive (Whisper streaming mode or VAD-gated).</li>
 * </ul>
 */
public interface SpeechToText {

    Mono<Transcript> transcribe(byte[] pcm, AudioFormatSpec format);

    Flux<Transcript> transcribeStream(Flux<AudioFrame> frames);
}

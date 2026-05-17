package org.tech.alfred.audio.tts;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.tech.alfred.audio.config.AlfredAudioProperties;
import org.tech.alfred.core.audio.AudioFrame;
import org.tech.alfred.core.audio.TextToSpeech;

/**
 * Piper TTS adapter (stub).
 *
 * <p>Will exec the piper binary: {@code piper --model voice.onnx --output_file out.wav}
 * and pipe the resulting WAV bytes back through the SPI.
 */
public class PiperTextToSpeech implements TextToSpeech {

    private final AlfredAudioProperties props;

    public PiperTextToSpeech(AlfredAudioProperties props) {
        this.props = props;
    }

    @Override
    public Mono<byte[]> synthesize(String text) {
        // TODO: exec piper, capture WAV stdout.
        return Mono.just(new byte[0]);
    }

    @Override
    public Flux<AudioFrame> stream(String text) {
        // TODO: stream WAV-frame chunks for low-latency playback.
        return Flux.empty();
    }
}

package org.tech.alfred.audio.stt;

import java.time.Duration;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.tech.alfred.audio.config.AlfredAudioProperties;
import org.tech.alfred.core.audio.AudioFormatSpec;
import org.tech.alfred.core.audio.AudioFrame;
import org.tech.alfred.core.audio.SpeechToText;
import org.tech.alfred.core.audio.Transcript;

/**
 * Whisper-based {@link SpeechToText} adapter.
 *
 * <p><b>Phase 1 (this class):</b> stub. Wire to whisper.cpp via a child process
 * (write WAV to temp file, exec `whisper-cli -m <model> -f <wav> -nt -of json`,
 * parse JSON). See {@code WhisperProcessRunner} (TODO) for the impl.
 *
 * <p><b>Phase 2:</b> swap in whisper-jni for in-process inference (no spawn).
 */
public class WhisperSpeechToText implements SpeechToText {

    private final AlfredAudioProperties props;

    public WhisperSpeechToText(AlfredAudioProperties props) {
        this.props = props;
    }

    @Override
    public Mono<Transcript> transcribe(byte[] pcm, AudioFormatSpec format) {
        // TODO: write PCM to a temp WAV, exec whisper.cpp, parse output.
        return Mono.just(new Transcript("", 0.0, Duration.ZERO));
    }

    @Override
    public Flux<Transcript> transcribeStream(Flux<AudioFrame> frames) {
        // TODO: VAD-gated buffering + chunked Whisper calls.
        return Flux.empty();
    }
}

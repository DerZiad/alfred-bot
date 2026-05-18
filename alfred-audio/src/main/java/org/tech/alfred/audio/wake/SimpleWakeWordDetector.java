package org.tech.alfred.audio.wake;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import reactor.core.publisher.Flux;

import org.tech.alfred.core.audio.AudioFrame;
import org.tech.alfred.core.audio.SpeechToText;
import org.tech.alfred.core.wake.WakeWordDetector;

/**
 * Naive wake-word detector: pipes mic audio through Whisper, watches for the
 * configured phrase. Cheap to ship but uses more CPU than a dedicated
 * keyword spotter.
 *
 * <p>Phase 2: replace with Porcupine or openWakeWord (small ONNX model) for
 * always-on listening at &lt;5% CPU.
 */
public class SimpleWakeWordDetector implements WakeWordDetector {

    private final SpeechToText stt;
    private final String phrase;

    public SimpleWakeWordDetector(SpeechToText stt, String phrase) {
        this.stt = stt;
        this.phrase = phrase.toLowerCase(Locale.ROOT);
    }

    @Override
    public Flux<WakeEvent> listen(Flux<AudioFrame> frames) {

        return frames
            .bufferTimeout(100, Duration.ofSeconds(3))
            .flatMap(chunk -> {

                byte[] pcm = merge(chunk);

                return stt.transcribe(
                    pcm,
                    chunk.get(0).format()
                );
            })
            .filter(t ->
                t.text()
                    .toLowerCase(Locale.ROOT)
                    .contains(phrase)
            )
            .map(t ->
                new WakeEvent(
                    phrase,
                    t.confidence(),
                    System.currentTimeMillis()
                )
            );
    }

    private byte[] merge(List<AudioFrame> frames) {

        ByteArrayOutputStream out =
            new ByteArrayOutputStream();

        try {

            for (AudioFrame frame : frames) {
                out.write(frame.samples());
            }

        } catch (IOException ignored) {}

        return out.toByteArray();
    }
}

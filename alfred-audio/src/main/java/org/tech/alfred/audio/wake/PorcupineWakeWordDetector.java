package org.tech.alfred.audio.wake;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicReference;

import ai.picovoice.porcupine.Porcupine;
import ai.picovoice.porcupine.PorcupineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.tech.alfred.core.audio.AudioFrame;
import org.tech.alfred.core.wake.WakeWordDetector;

/**
 * Always-on wake-word detector backed by Picovoice
 * <a href="https://picovoice.ai/platform/porcupine/">Porcupine</a> (v3.x).
 *
 * <p>Porcupine wants 16 kHz mono 16-bit PCM in frames of exactly
 * {@code Porcupine#getFrameLength()} samples (typically 512). Our
 * mic capture emits ~50ms frames sized however
 * {@link org.tech.alfred.audio.capture.MicrophoneCapture} configured them,
 * so we accumulate samples in a rolling buffer and call {@code process}
 * once per full frame.
 *
 * <p>This detector always loads a {@code .ppn} keyword file from disk.
 * To use the built-in {@code JARVIS} keyword, download
 * {@code jarvis_<platform>.ppn} from
 * <a href="https://github.com/Picovoice/porcupine/tree/master/resources/keyword_files">
 * the Porcupine repo</a> (or any custom keyword from
 * <a href="https://console.picovoice.ai/">console.picovoice.ai</a>) and
 * point {@code alfred.audio.wake-keyword-path} at it.
 */
public final class PorcupineWakeWordDetector implements WakeWordDetector {

    private static final Logger log = LoggerFactory.getLogger(PorcupineWakeWordDetector.class);

    private final String accessKey;
    private final String keywordPath;
    private final String label;
    private final float sensitivity;

    private final AtomicReference<Porcupine> instance = new AtomicReference<>();

    public PorcupineWakeWordDetector(String accessKey,
                                     String keywordPath,
                                     String label,
                                     float sensitivity) {
        if (keywordPath == null || keywordPath.isBlank()) {
            throw new IllegalArgumentException(
                    "Porcupine requires a keyword .ppn path. Set "
                  + "alfred.audio.wake-keyword-path or download "
                  + "jarvis_<platform>.ppn from "
                  + "https://github.com/Picovoice/porcupine/tree/master/resources/keyword_files");
        }
        this.accessKey = accessKey;
        this.keywordPath = keywordPath;
        this.label = (label == null || label.isBlank()) ? "wake" : label;
        this.sensitivity = sensitivity;
    }

    private Porcupine init() throws PorcupineException {
        // Porcupine 3.x Builder API: setKeywordPaths is the universal entry
        // point. Built-in keywords also live as .ppn files on disk.
        return new Porcupine.Builder()
                .setAccessKey(accessKey)
                .setKeywordPaths(new String[] { keywordPath })
                .setSensitivities(new float[] { sensitivity })
                .build();
    }

    @Override
    public Flux<WakeEvent> listen(Flux<AudioFrame> frames) {
        return Flux.<WakeEvent>create(sink -> {
            Porcupine porcupine;
            try {
                porcupine = init();
                instance.set(porcupine);
            } catch (PorcupineException e) {
                log.error("Failed to initialize Porcupine", e);
                sink.error(e);
                return;
            }
            int frameLength = porcupine.getFrameLength();
            short[] buffer = new short[frameLength];
            int[] writeIdx = { 0 };  // boxed for lambda mutation

            sink.onDispose(() -> {
                Porcupine p = instance.getAndSet(null);
                if (p != null) {
                    try { p.delete(); } catch (Exception ignored) {}
                }
            });

            frames.subscribe(frame -> {
                byte[] pcm = frame.samples();
                ShortReader r = new ShortReader(pcm);
                while (r.hasNext()) {
                    buffer[writeIdx[0]++] = r.next();
                    if (writeIdx[0] >= frameLength) {
                        writeIdx[0] = 0;
                        try {
                            int kw = porcupine.process(buffer);
                            if (kw >= 0) {
                                sink.next(new WakeEvent(label, 1.0,
                                        System.currentTimeMillis()));
                            }
                        } catch (PorcupineException e) {
                            log.warn("Porcupine.process failed", e);
                        }
                    }
                }
            }, sink::error, sink::complete);
        });
    }

    /** Tiny iterator over signed 16-bit LE PCM bytes. */
    private static final class ShortReader {
        private final ByteBuffer bb;
        ShortReader(byte[] pcm) {
            this.bb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        }
        boolean hasNext() { return bb.remaining() >= 2; }
        short next() { return bb.getShort(); }
    }
}

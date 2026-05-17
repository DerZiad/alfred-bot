package org.tech.alfred.audio.capture;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.tech.alfred.core.audio.AudioCapture;
import org.tech.alfred.core.audio.AudioFormatSpec;
import org.tech.alfred.core.audio.AudioFrame;

/**
 * {@link AudioCapture} using the JDK's {@code javax.sound.sampled} API.
 *
 * <p>Frames are emitted on the boundedElastic scheduler so blocking reads from
 * the TargetDataLine do not pin the reactor event loop.
 */
public class MicrophoneCapture implements AudioCapture {

    private static final Logger log = LoggerFactory.getLogger(MicrophoneCapture.class);
    private static final int FRAME_MILLIS = 50;

    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private volatile TargetDataLine line;

    @Override
    public Flux<AudioFrame> start() {
        if (!capturing.compareAndSet(false, true)) {
            return Flux.error(new IllegalStateException("Capture already started"));
        }
        AudioFormatSpec spec = AudioFormatSpec.WHISPER_DEFAULT;
        AudioFormat fmt = new AudioFormat(
                spec.sampleRateHz(), spec.bitsPerSample(), spec.channels(), true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);

        return Flux.<AudioFrame>create(sink -> {
            try {
                line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(fmt);
                line.start();

                int frameBytes = (spec.sampleRateHz() * spec.bitsPerSample() / 8) * FRAME_MILLIS / 1000;
                byte[] buf = new byte[frameBytes];
                while (capturing.get() && !sink.isCancelled()) {
                    int read = line.read(buf, 0, buf.length);
                    if (read > 0) {
                        byte[] copy = new byte[read];
                        System.arraycopy(buf, 0, copy, 0, read);
                        sink.next(new AudioFrame(copy, spec));
                    }
                }
                sink.complete();
            } catch (LineUnavailableException e) {
                sink.error(e);
            } finally {
                stop();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public void stop() {
        if (capturing.compareAndSet(true, false) && line != null) {
            try {
                line.stop();
                line.close();
            } catch (Exception e) {
                log.warn("Failed to close mic line cleanly", e);
            }
        }
    }

    @Override
    public boolean isCapturing() {
        return capturing.get();
    }
}

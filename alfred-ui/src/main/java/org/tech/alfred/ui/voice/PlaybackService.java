package org.tech.alfred.ui.voice;

import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicReference;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Plays WAV-encoded audio bytes through the default output device.
 * Used for TTS responses from Piper. Exposes a {@link #levelProperty()}
 * that updates 60 times per second from the in-flight playback so the
 * spectrum bars dance with the actual voice.
 *
 * <p>We use {@code javax.sound.sampled} directly (not JavaFX Media)
 * because Piper outputs raw 22050 Hz mono WAV which the Java Sound
 * pipeline handles without a temp file.
 */
@Component
public class PlaybackService {

    private static final Logger log = LoggerFactory.getLogger(PlaybackService.class);

    private final DoubleProperty level = new SimpleDoubleProperty(0);
    private final AtomicReference<SourceDataLine> activeLine = new AtomicReference<>();

    public DoubleProperty levelProperty() { return level; }

    /**
     * Play the given WAV bytes; completes when playback finishes.
     */
    public Mono<Void> play(byte[] wav) {
        if (wav == null || wav.length == 0) return Mono.empty();
        return Mono.<Void>create(sink -> {
            try (AudioInputStream in =
                         AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {

                SourceDataLine line = AudioSystem.getSourceDataLine(in.getFormat());
                line.open(in.getFormat());
                line.start();
                activeLine.set(line);

                byte[] buf = new byte[4096];
                int read;
                while ((read = in.read(buf)) != -1) {
                    line.write(buf, 0, read);
                    updateLevel(buf, read);
                }
                line.drain();
                line.stop();
                line.close();
                activeLine.set(null);
                setLevel(0);
                sink.success();
            } catch (Exception e) {
                log.error("Playback failed", e);
                setLevel(0);
                sink.error(e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public void stop() {
        SourceDataLine line = activeLine.getAndSet(null);
        if (line != null) {
            try {
                line.stop();
                line.close();
            } catch (Exception e) {
                log.warn("stop() failed", e);
            }
        }
        setLevel(0);
    }

    private void updateLevel(byte[] buf, int len) {
        if (len < 2) return;
        long sumSq = 0;
        int n = len / 2;
        for (int i = 0; i < n; i++) {
            int lo = buf[2 * i] & 0xff;
            int hi = buf[2 * i + 1];
            int s = (hi << 8) | lo;
            sumSq += (long) s * s;
        }
        double rms = Math.sqrt((double) sumSq / n) / 32768.0;
        setLevel(Math.min(1.0, rms * 2.0));
    }

    private void setLevel(double v) {
        if (Platform.isFxApplicationThread()) level.set(v);
        else Platform.runLater(() -> level.set(v));
    }
}

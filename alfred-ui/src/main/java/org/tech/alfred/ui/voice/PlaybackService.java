package org.tech.alfred.ui.voice;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.tech.alfred.audio.config.AlfredAudioProperties;

/**
 * Plays WAV-encoded audio bytes through a configured (or default) output
 * device. Used for TTS responses from Piper. Exposes a {@link #levelProperty()}
 * that updates from in-flight playback so the spectrum bars dance with
 * the actual voice.
 *
 * <p>The output device can be pinned via
 * {@code alfred.audio.output-device} (case-insensitive substring of the
 * mixer name, e.g. {@code "Logitech G432"} or {@code "Headphones"}).
 * On startup we enumerate every output mixer and log it so you know
 * what's available.
 */
@Component
public class PlaybackService {

    private static final Logger log = LoggerFactory.getLogger(PlaybackService.class);

    private final DoubleProperty level = new SimpleDoubleProperty(0);
    private final AtomicReference<SourceDataLine> activeLine = new AtomicReference<>();
    private final String deviceName;

    public PlaybackService(AlfredAudioProperties props) {
        this.deviceName = (props.outputDevice() == null || props.outputDevice().isBlank())
                ? null : props.outputDevice();
        logAvailableDevices();
    }

    public DoubleProperty levelProperty() { return level; }

    /**
     * Play the given WAV bytes; completes when playback finishes.
     */
    public Mono<Void> play(byte[] wav) {
        if (wav == null || wav.length == 0) return Mono.empty();
        return Mono.<Void>create(sink -> {
            try (AudioInputStream in =
                         AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {

                AudioFormat fmt = in.getFormat();
                SourceDataLine line = openLine(fmt);
                line.open(fmt);
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

    // ----------------------------------------------------------------------
    // Device selection
    // ----------------------------------------------------------------------

    private SourceDataLine openLine(AudioFormat fmt) throws Exception {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);

        // Pinned by config?
        if (deviceName != null) {
            for (Mixer.Info mInfo : AudioSystem.getMixerInfo()) {
                if (!mInfo.getName().toLowerCase().contains(deviceName.toLowerCase())) continue;
                Mixer mixer = AudioSystem.getMixer(mInfo);
                if (!mixer.isLineSupported(info)) continue;
                try {
                    SourceDataLine l = (SourceDataLine) mixer.getLine(info);
                    log.info(">>> SPEAKER IN USE: '{}' (pinned via alfred.audio.output-device)",
                            mInfo.getName());
                    return l;
                } catch (Exception e) {
                    log.warn("Pinned speaker '{}' failed to open ({}); falling back.",
                            mInfo.getName(), e.getMessage());
                }
            }
            log.warn("No mixer matched output-device='{}'. Falling back to system default.",
                    deviceName);
        }

        // Walk mixers and pick the first one that supports the format,
        // so we can name it in the log.
        for (Mixer.Info mInfo : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(mInfo);
            if (!mixer.isLineSupported(info)) continue;
            try {
                SourceDataLine l = (SourceDataLine) mixer.getLine(info);
                log.info(">>> SPEAKER IN USE: '{}' (system default — first mixer that "
                        + "supports the TTS format)", mInfo.getName());
                return l;
            } catch (Exception e) {
                log.debug("Mixer '{}' rejected ({}); trying next.",
                        mInfo.getName(), e.getMessage());
            }
        }

        // Last resort.
        SourceDataLine l = AudioSystem.getSourceDataLine(fmt);
        log.warn(">>> SPEAKER IN USE: <unnamed> (fell through to "
                + "AudioSystem.getSourceDataLine)");
        return l;
    }

    /**
     * Logs every mixer that exposes a SourceDataLine. Run once at
     * construction so users can see in their logs what's available.
     */
    private void logAvailableDevices() {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, null);
        List<String> outputs = new ArrayList<>();
        for (Mixer.Info mInfo : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(mInfo);
            if (mixer.getSourceLineInfo().length == 0) continue;
            boolean supports = mixer.isLineSupported(info);
            outputs.add(String.format("  - %s  [supports SourceDataLine: %s]",
                    mInfo.getName(), supports));
        }
        if (outputs.isEmpty()) {
            log.warn("No audio output devices found.");
        } else {
            log.info("Available audio output devices ({}):\n{}",
                    outputs.size(), String.join("\n", outputs));
        }
    }

    // ----------------------------------------------------------------------
    // Level metering
    // ----------------------------------------------------------------------

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

package org.tech.alfred.audio.capture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
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
 * <p>On startup we enumerate every {@link Mixer} that exposes a
 * {@link TargetDataLine} (microphone-capable inputs) and log them.
 * The selected device can be filtered by name via the {@code deviceName}
 * substring — handy on Windows where the default capture device is
 * sometimes the wrong one (e.g. a virtual line-in instead of the real
 * USB headset).
 *
 * <p>Frames are emitted on the {@code boundedElastic} scheduler so the
 * blocking {@code line.read} calls never pin the reactor event loop.
 */
public class MicrophoneCapture implements AudioCapture {

    private static final Logger log = LoggerFactory.getLogger(MicrophoneCapture.class);
    private static final int FRAME_MILLIS = 50;

    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private final String deviceName;
    private volatile TargetDataLine line;

    public MicrophoneCapture() {
        this(null);
    }

    /**
     * @param deviceName case-insensitive substring of the mixer name to prefer.
     *                   {@code null} or blank = use the system default.
     */
    public MicrophoneCapture(String deviceName) {
        this.deviceName = (deviceName == null || deviceName.isBlank()) ? null : deviceName;
        logAvailableDevices();
    }

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
                line = openLine(info, fmt);
                line.start();
                log.info("Microphone capture started: format={} buffer={}B",
                        line.getFormat(), line.getBufferSize());

                int frameBytes = (spec.sampleRateHz() * spec.bitsPerSample() / 8) * FRAME_MILLIS / 1000;
                byte[] buf = new byte[frameBytes];
                long framesEmitted = 0;
                while (capturing.get() && !sink.isCancelled()) {
                    int read = line.read(buf, 0, buf.length);
                    if (read > 0) {
                        byte[] copy = new byte[read];
                        System.arraycopy(buf, 0, copy, 0, read);
                        sink.next(new AudioFrame(copy, spec));
                        framesEmitted++;
                        // Heartbeat every ~5s so silent mics are obvious in logs.
                        if (framesEmitted % 100 == 0) {
                            log.debug("Mic alive: {} frames emitted", framesEmitted);
                        }
                    }
                }
                sink.complete();
            } catch (LineUnavailableException e) {
                log.error("Microphone line unavailable. Check OS mic permissions and that "
                        + "another app isn't holding the device.", e);
                sink.error(e);
            } catch (IllegalArgumentException e) {
                log.error("No microphone supports {} Hz / {} ch / {}-bit signed PCM. "
                        + "Available inputs are logged above.",
                        spec.sampleRateHz(), spec.channels(), spec.bitsPerSample(), e);
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

    // ----------------------------------------------------------------------
    // Internals
    // ----------------------------------------------------------------------

    private TargetDataLine openLine(DataLine.Info info, AudioFormat fmt)
            throws LineUnavailableException {
        // If the user pinned a specific device by name, try that first.
        if (deviceName != null) {
            for (Mixer.Info mInfo : AudioSystem.getMixerInfo()) {
                if (!mInfo.getName().toLowerCase().contains(deviceName.toLowerCase())) continue;
                Mixer mixer = AudioSystem.getMixer(mInfo);
                if (!mixer.isLineSupported(info)) continue;
                try {
                    TargetDataLine l = (TargetDataLine) mixer.getLine(info);
                    l.open(fmt);
                    log.info(">>> MIC IN USE: '{}' (pinned via alfred.audio.input-device)",
                            mInfo.getName());
                    return l;
                } catch (Exception e) {
                    log.warn("Pinned device '{}' failed to open ({}); falling back.",
                            mInfo.getName(), e.getMessage());
                }
            }
            log.warn("No mixer matched device-name='{}'. Falling back to system default.",
                    deviceName);
        }

        // System default path — walk the mixer list so we can name the one we end up on.
        for (Mixer.Info mInfo : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(mInfo);
            if (!mixer.isLineSupported(info)) continue;
            try {
                TargetDataLine l = (TargetDataLine) mixer.getLine(info);
                l.open(fmt);
                log.info(">>> MIC IN USE: '{}' (system default — first mixer that supports "
                        + "16kHz mono 16-bit PCM)", mInfo.getName());
                return l;
            } catch (Exception e) {
                log.debug("Mixer '{}' supports the format but failed to open ({}); trying next.",
                        mInfo.getName(), e.getMessage());
            }
        }
        // Last resort — generic global line; we can't name it.
        TargetDataLine l = (TargetDataLine) AudioSystem.getLine(info);
        l.open(fmt);
        log.warn(">>> MIC IN USE: <unnamed> (fell through to AudioSystem.getLine — "
                + "no mixer accepted the format directly)");
        return l;
    }

    /**
     * Logs every mixer that exposes a TargetDataLine, plus whether it
     * supports our 16 kHz mono 16-bit format. Run once at construction
     * so users can see in their logs exactly what their system reports.
     */
    private void logAvailableDevices() {
        AudioFormatSpec spec = AudioFormatSpec.WHISPER_DEFAULT;
        AudioFormat fmt = new AudioFormat(
                spec.sampleRateHz(), spec.bitsPerSample(), spec.channels(), true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);

        List<String> inputs = new ArrayList<>();
        for (Mixer.Info mInfo : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(mInfo);
            boolean hasInputs = mixer.getTargetLineInfo().length > 0;
            if (!hasInputs) continue;
            boolean supports = mixer.isLineSupported(info);
            inputs.add(String.format("  - %s  [supports 16kHz mono 16-bit: %s]",
                    mInfo.getName(), supports));
        }
        if (inputs.isEmpty()) {
            log.warn("No audio input devices found. Check OS microphone permissions.");
        } else {
            log.info("Available audio input devices ({}):\n{}",
                    inputs.size(), String.join("\n", inputs));
        }
    }
}

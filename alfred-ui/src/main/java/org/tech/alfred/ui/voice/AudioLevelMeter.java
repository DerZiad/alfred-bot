package org.tech.alfred.ui.voice;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import reactor.core.publisher.Flux;

import org.tech.alfred.core.audio.AudioFrame;

/**
 * Computes a smoothed RMS level (0..1) from incoming PCM frames and
 * exposes it as a JavaFX {@link DoubleProperty}. The HUD's
 * {@link org.tech.alfred.ui.hud.Waveform} and
 * {@link org.tech.alfred.ui.hud.AlfredCore} bind to this property.
 *
 * <p>Smoothing is a one-pole low-pass at ~10 Hz so the visualization
 * doesn't strobe on each 50ms frame.
 */
public final class AudioLevelMeter {

    private final DoubleProperty level = new SimpleDoubleProperty(0);
    private double smoothed = 0;

    public DoubleProperty levelProperty() { return level; }

    /**
     * Subscribe to the given audio Flux as a side-effect. Returns the
     * same Flux for fluent chaining ({@code mic.share().transform(meter::tap)}).
     */
    public Flux<AudioFrame> tap(Flux<AudioFrame> frames) {
        return frames.doOnNext(this::accept);
    }

    public void accept(AudioFrame frame) {
        double rms = computeRms(frame.samples());
        // One-pole IIR smoothing.
        smoothed = 0.7 * smoothed + 0.3 * rms;
        double v = Math.min(1.0, smoothed * 1.5);
        if (Platform.isFxApplicationThread()) level.set(v);
        else {
            final double vv = v;
            Platform.runLater(() -> level.set(vv));
        }
    }

    /** Compute normalized RMS of signed 16-bit little-endian PCM. */
    private static double computeRms(byte[] samples) {
        if (samples.length < 2) return 0;
        long sumSq = 0;
        int n = samples.length / 2;
        for (int i = 0; i < n; i++) {
            int lo = samples[2 * i] & 0xff;
            int hi = samples[2 * i + 1];
            int s = (hi << 8) | lo;
            sumSq += (long) s * s;
        }
        double meanSq = (double) sumSq / n;
        return Math.sqrt(meanSq) / 32768.0;
    }
}

package org.tech.alfred.ui.animation;

import javafx.animation.AnimationTimer;
import javafx.scene.Node;

/**
 * Drives a node's {@code scale} (and optionally opacity) along a sine
 * curve. Used by {@link org.tech.alfred.ui.hud.AlfredCore} to give the
 * central orb its slow breathing pulse - and to amplify that pulse with
 * live mic level during {@code LISTENING}.
 *
 * <p>The frequency in Hz is mutable so the pipeline can speed it up
 * (e.g. THINKING) or slow it down (SLEEP) without recreating the timer.
 */
public final class PulseAnimation {

    private final Node node;
    private final double baseScale;
    private final double amplitude;
    private volatile double frequencyHz;
    private volatile double externalLevel;
    private final AnimationTimer timer;
    private long startNanos = -1L;

    public PulseAnimation(Node node, double baseScale, double amplitude, double frequencyHz) {
        this.node = node;
        this.baseScale = baseScale;
        this.amplitude = amplitude;
        this.frequencyHz = frequencyHz;
        this.timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (startNanos < 0) startNanos = now;
                double t = (now - startNanos) / 1_000_000_000.0;
                double s = Math.sin(2 * Math.PI * frequencyHz * t);
                double scale = baseScale + amplitude * (0.5 + 0.5 * s) + 0.35 * externalLevel;
                node.setScaleX(scale);
                node.setScaleY(scale);
            }
        };
    }

    public void play() { timer.start(); }
    public void stop() { timer.stop(); }

    /** Set the breathing frequency. Effect is smooth (no reset). */
    public void setFrequencyHz(double hz) { this.frequencyHz = hz; }

    /**
     * External input (0..1) added to the pulse - typically the live
     * audio RMS from the mic or TTS playback.
     */
    public void setExternalLevel(double level) {
        this.externalLevel = Math.max(0, Math.min(1, level));
    }
}

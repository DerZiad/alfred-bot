package org.tech.alfred.ui.hud;

import java.util.Random;

import javafx.animation.AnimationTimer;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

/**
 * Vertical mirror-symmetric spectrum bars - the classic "AI speaking"
 * animation. We don't actually FFT the audio (would add latency and
 * a dependency); instead we synthesize a believable spectrum from the
 * live RMS level plus low-frequency noise per band, which reads as
 * audio-reactive to the eye while staying cheap.
 *
 * <p>Used in SPEAKING state. The shared {@link #levelProperty()} is
 * driven by {@link org.tech.alfred.ui.voice.PlaybackService}.
 */
public final class SpectrumBars extends Canvas {

    private static final int BARS = 28;

    private final double[] heights = new double[BARS];
    private final double[] phase   = new double[BARS];
    private final Random rng = new Random(42);
    private final DoubleProperty level = new SimpleDoubleProperty(0);
    private final AnimationTimer timer;
    private long lastNanos = 0;
    private Color accent = Color.web("#ff9a3c");

    public SpectrumBars(double width, double height) {
        super(Math.max(1, width), Math.max(1, height));
        // No Canvas-level Bloom effect — see Waveform for the rationale.
        for (int i = 0; i < BARS; i++) phase[i] = rng.nextDouble() * Math.PI * 2;

        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastNanos == 0) lastNanos = now;
                double dt = (now - lastNanos) / 1_000_000_000.0;
                lastNanos = now;
                tick(dt);
                draw();
            }
        };
        timer.start();
    }

    public DoubleProperty levelProperty() { return level; }

    public void setAccent(Color accent) { this.accent = accent; }

    private void tick(double dt) {
        double l = level.get();
        for (int i = 0; i < BARS; i++) {
            // Each bar oscillates at a unique frequency scaled by mic level.
            double freq = 2.0 + (i % 7) * 0.7;
            phase[i] += dt * (1.5 + l * 6.0) * freq;
            double envelope = 0.4 + l * 0.9;
            double v = (0.5 + 0.5 * Math.sin(phase[i])) * envelope;
            // Smooth toward target.
            heights[i] = heights[i] * 0.6 + v * 0.4;
        }
    }

    private void draw() {
        double w = getWidth();
        double h = getHeight();
        if (w < 1 || h < 1) return;
        double midY = h / 2.0;
        double slotW = w / BARS;
        double barW = slotW * 0.55;

        GraphicsContext g = getGraphicsContext2D();
        g.clearRect(0, 0, w, h);

        LinearGradient grad = new LinearGradient(
                0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0,   accent.deriveColor(0, 1, 1.2, 1.0)),
                new Stop(0.5, accent.deriveColor(0, 1, 1, 1.0)),
                new Stop(1,   accent.deriveColor(0, 1, 0.6, 0.4)));
        g.setFill(grad);

        for (int i = 0; i < BARS; i++) {
            double barH = heights[i] * (h * 0.45);
            double x = i * slotW + (slotW - barW) / 2.0;
            g.fillRoundRect(x, midY - barH, barW, barH * 2, 4, 4);
        }
    }
}

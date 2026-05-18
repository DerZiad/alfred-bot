package org.tech.alfred.ui.hud;

import java.util.Arrays;

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
 * Live audio waveform rendered to a {@link Canvas}. A circular buffer
 * of recent RMS samples is scrolled left-to-right each frame.
 *
 * <p>Used in the LISTENING state to show Alfred is really hearing the
 * mic. The same component, driven from {@link #levelProperty()}, also
 * provides a fallback in SPEAKING if {@link SpectrumBars} isn't desired.
 */
public final class Waveform extends Canvas {

    private static final int SAMPLES = 240;

    private final double[] ring = new double[SAMPLES];
    private int writeIdx = 0;
    private final DoubleProperty level = new SimpleDoubleProperty(0);
    private final AnimationTimer timer;
    private long lastNanos = 0;
    private Color color = Color.web("#00d4ff");

    public Waveform(double width, double height) {
        super(Math.max(1, width), Math.max(1, height));
        // Skipping setEffect(new Bloom(...)) — the JavaFX prism renderer
        // NPEs on Canvas+effect when the canvas is briefly 0-sized during
        // layout. The gradient stroke + center-fade is already plenty.
        Arrays.fill(ring, 0);

        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // Sample at ~60Hz from the level property.
                if (now - lastNanos < 16_000_000L) return;
                lastNanos = now;
                ring[writeIdx] = level.get();
                writeIdx = (writeIdx + 1) % SAMPLES;
                draw();
            }
        };
        timer.start();
    }

    public DoubleProperty levelProperty() { return level; }

    public void setAccent(Color color) {
        this.color = color;
    }

    private void draw() {
        double w = getWidth();
        double h = getHeight();
        if (w < 1 || h < 1) return;
        double midY = h / 2.0;
        GraphicsContext g = getGraphicsContext2D();
        g.clearRect(0, 0, w, h);

        // Center axis - very subtle.
        g.setStroke(color.deriveColor(0, 1, 1, 0.15));
        g.setLineWidth(0.5);
        g.strokeLine(0, midY, w, midY);

        // Gradient stroke for the waveform.
        LinearGradient grad = new LinearGradient(
                0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0,   color.deriveColor(0, 1, 1, 0.0)),
                new Stop(0.2, color.deriveColor(0, 1, 1, 0.9)),
                new Stop(0.8, color.deriveColor(0, 1, 1, 0.9)),
                new Stop(1,   color.deriveColor(0, 1, 1, 0.0)));
        g.setStroke(grad);
        g.setLineWidth(1.6);

        g.beginPath();
        for (int i = 0; i < SAMPLES; i++) {
            int idx = (writeIdx + i) % SAMPLES;
            double v = ring[idx];
            double x = i * w / (SAMPLES - 1);
            double y = midY - v * (h * 0.45);
            if (i == 0) g.moveTo(x, y); else g.lineTo(x, y);
        }
        g.stroke();

        // Mirror lower half for symmetry.
        g.beginPath();
        for (int i = 0; i < SAMPLES; i++) {
            int idx = (writeIdx + i) % SAMPLES;
            double v = ring[idx];
            double x = i * w / (SAMPLES - 1);
            double y = midY + v * (h * 0.45);
            if (i == 0) g.moveTo(x, y); else g.lineTo(x, y);
        }
        g.stroke();
    }
}

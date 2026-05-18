package org.tech.alfred.ui.hud;

import java.util.Random;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import org.tech.alfred.ui.state.AssistantState;

/**
 * Ambient particle field that drifts across the background. Adds depth
 * to the otherwise flat dark canvas and reinforces the "alive AI"
 * feel. Particles drift radially outward from the center, fade in and
 * out, and respawn near the core.
 *
 * <p>State-aware: density and brightness scale up during active states.
 *
 * <p>Note on rendering: we deliberately avoid the {@code Bloom} effect on
 * this canvas. With a JavaFX effect attached, the prism renderer
 * allocates an RTTexture sized to the canvas; if the canvas is 0×0 for
 * a single frame during initial layout the texture creation NPEs. The
 * particles look fine with additive transparency alone.
 */
public final class ParticleField extends Canvas {

    private static final int MAX_PARTICLES = 140;
    /** D3D RTTexture has a per-GPU max (typically ≥ 8192); clamp here. */
    private static final double MAX_DIM = 4096;

    /**
     * Clamp incoming sizes so we never hand JavaFX a NaN, infinite, negative,
     * or absurdly large dimension. Any of those crash the prism renderer.
     */
    private static double sanitize(double v) {
        if (Double.isNaN(v) || v <= 0) return 1;
        if (v > MAX_DIM) return MAX_DIM;
        return v;
    }

    private static final class P {
        double x, y, vx, vy, life, maxLife, size;
    }

    private final P[] particles = new P[MAX_PARTICLES];
    private final Random rng = new Random();
    private final AnimationTimer timer;
    private long lastNanos = 0;
    private volatile double intensity = 0.4;
    private volatile Color tint = Color.web("#00d4ff");

    public ParticleField(double width, double height) {
        super(sanitize(width), sanitize(height));
        for (int i = 0; i < particles.length; i++) {
            particles[i] = new P();
            respawn(particles[i], width, height);
            // Stagger initial lifetimes so they don't all die together.
            particles[i].life = rng.nextDouble() * particles[i].maxLife;
        }
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastNanos == 0) lastNanos = now;
                double dt = (now - lastNanos) / 1_000_000_000.0;
                lastNanos = now;
                update(dt);
                draw();
            }
        };
        timer.start();
    }

    /**
     * Track a parent's size with a safety clamp. We deliberately don't
     * use {@code bindBidirectional} or {@code widthProperty().bind(...)}:
     * during initial layout the parent is 0×0 for an instant, which
     * would briefly leave the canvas at 0 and crash the renderer.
     */
    public void followSize(javafx.beans.value.ObservableValue<? extends Number> w,
                           javafx.beans.value.ObservableValue<? extends Number> h) {
        w.addListener((obs, o, n) -> setWidth(sanitize(n.doubleValue())));
        h.addListener((obs, o, n) -> setHeight(sanitize(n.doubleValue())));
        // Apply current value too.
        setWidth(sanitize(w.getValue().doubleValue()));
        setHeight(sanitize(h.getValue().doubleValue()));
    }

    public void onStateChanged(AssistantState state) {
        switch (state) {
            case SLEEP     -> { intensity = 0.35; tint = Color.web("#00d4ff"); }
            case WAKING    -> { intensity = 1.2;  tint = Color.web("#00d4ff"); }
            case LISTENING -> { intensity = 0.7;  tint = Color.web("#00d4ff"); }
            case THINKING  -> { intensity = 1.0;  tint = Color.web("#a78bfa"); }
            case SPEAKING  -> { intensity = 0.9;  tint = Color.web("#ff9a3c"); }
            case ERROR     -> { intensity = 1.4;  tint = Color.web("#ff3b3b"); }
        }
    }

    private void respawn(P p, double w, double h) {
        double cx = w / 2.0, cy = h / 2.0;
        double angle = rng.nextDouble() * Math.PI * 2;
        double radius = 30 + rng.nextDouble() * 80;
        p.x = cx + Math.cos(angle) * radius;
        p.y = cy + Math.sin(angle) * radius;
        double speed = 8 + rng.nextDouble() * 22;
        p.vx = Math.cos(angle) * speed;
        p.vy = Math.sin(angle) * speed;
        p.maxLife = 3.0 + rng.nextDouble() * 4.0;
        p.life = 0;
        p.size = 0.6 + rng.nextDouble() * 1.6;
    }

    private void update(double dt) {
        double w = getWidth();
        double h = getHeight();
        if (w < 1 || h < 1) return;
        for (P p : particles) {
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.life += dt;
            if (p.life >= p.maxLife
                    || p.x < -10 || p.x > w + 10
                    || p.y < -10 || p.y > h + 10) {
                respawn(p, w, h);
            }
        }
    }

    private void draw() {
        double w = getWidth();
        double h = getHeight();
        if (w < 1 || h < 1) return;
        GraphicsContext g = getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        for (P p : particles) {
            double alpha = Math.max(0, 1.0 - Math.abs(0.5 - p.life / p.maxLife) * 2.0);
            alpha *= intensity;
            g.setFill(tint.deriveColor(0, 1, 1, alpha));
            double r = p.size;
            g.fillOval(p.x - r, p.y - r, r * 2, r * 2);
        }
    }
}

package org.tech.alfred.ui.animation;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Continuously rotates a node. The rotation speed is exposed so the
 * pipeline can accelerate rings during {@code THINKING} and slow them
 * back down for {@code SPEAKING}.
 *
 * <p>Each instance owns one {@link RotateTransition}; speed changes
 * stop and restart it with a new duration. We avoid {@code AnimationTimer}
 * here because RotateTransition is GPU-accelerated and cheaper for the
 * common steady-state case.
 */
public final class RingRotator {

    private final RotateTransition rotation;

    public RingRotator(Node node, Duration period, boolean clockwise) {
        this.rotation = new RotateTransition(period, node);
        rotation.setByAngle(clockwise ? 360 : -360);
        rotation.setCycleCount(Animation.INDEFINITE);
        rotation.setInterpolator(Interpolator.LINEAR);
    }

    public void play() {
        rotation.play();
    }

    public void stop() {
        rotation.stop();
    }

    /** Adjust rotation period without resetting current angle. */
    public void setPeriod(Duration period) {
        double currentAngle = rotation.getNode().getRotate();
        boolean wasPlaying = rotation.getStatus() == Animation.Status.RUNNING;
        rotation.stop();
        rotation.setDuration(period);
        rotation.getNode().setRotate(currentAngle);
        if (wasPlaying) rotation.play();
    }
}

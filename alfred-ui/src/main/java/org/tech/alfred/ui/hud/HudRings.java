package org.tech.alfred.ui.hud;

import java.util.List;

import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

import org.tech.alfred.ui.animation.RingRotator;
import org.tech.alfred.ui.state.AssistantState;

/**
 * Three concentric arc rings that rotate independently. Each is an open
 * arc (not a full circle), so the rotation is obvious. Together they
 * read as a HUD targeting reticle.
 *
 * <p>The pipeline accelerates them during {@code THINKING} for "scanning"
 * feel, then drops them back to a calm idle speed.
 */
public final class HudRings extends StackPane {

    private final List<Arc> arcs;
    private final List<RingRotator> rotators;

    public HudRings(double outerDiameter) {
        setPickOnBounds(false);

        Arc r1 = ring(outerDiameter * 1.00, 220, 1.6);
        Arc r2 = ring(outerDiameter * 0.78, 280, 1.2);
        Arc r3 = ring(outerDiameter * 0.58, 160, 0.9);
        this.arcs = List.of(r1, r2, r3);

        getChildren().addAll(r1, r2, r3);

        rotators = List.of(
                new RingRotator(r1, Duration.seconds(18), true),
                new RingRotator(r2, Duration.seconds(12), false),
                new RingRotator(r3, Duration.seconds(7), true));
        rotators.forEach(RingRotator::play);

        getStyleClass().add("hud-rings");
        applyTint(Color.web("#00d4ff"));
    }

    private static Arc ring(double diameter, double sweep, double strokeWidth) {
        Arc arc = new Arc(0, 0, diameter / 2.0, diameter / 2.0, 0, sweep);
        arc.setType(ArcType.OPEN);
        arc.setFill(Color.TRANSPARENT);
        arc.setStrokeLineCap(StrokeLineCap.ROUND);
        arc.setStrokeWidth(strokeWidth);
        arc.getStyleClass().add("hud-ring");
        return arc;
    }

    public void onStateChanged(AssistantState state) {
        switch (state) {
            case SLEEP    -> setSpeeds(40, 32, 22);
            case WAKING   -> setSpeeds(8, 6, 4);
            case LISTENING -> setSpeeds(18, 12, 7);
            case THINKING -> setSpeeds(4, 2.8, 1.8);
            case SPEAKING -> setSpeeds(10, 7, 5);
            case ERROR    -> setSpeeds(60, 60, 60);
        }
        switch (state) {
            case THINKING -> applyTint(Color.web("#a78bfa"));
            case SPEAKING -> applyTint(Color.web("#ff9a3c"));
            case ERROR    -> applyTint(Color.web("#ff3b3b"));
            default       -> applyTint(Color.web("#00d4ff"));
        }
    }

    private void setSpeeds(double s1, double s2, double s3) {
        rotators.get(0).setPeriod(Duration.seconds(s1));
        rotators.get(1).setPeriod(Duration.seconds(s2));
        rotators.get(2).setPeriod(Duration.seconds(s3));
    }

    private void applyTint(Color color) {
        for (Arc a : arcs) {
            a.setStroke(color);
        }
    }
}

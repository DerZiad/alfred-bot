package org.tech.alfred.ui.hud;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeType;
import javafx.util.Duration;

import org.tech.alfred.ui.animation.RingRotator;
import org.tech.alfred.ui.state.AssistantState;

/**
 * Multi-layered HUD reticle: thin tick-mark ring on the outside, two
 * arc rings rotating in opposite directions, a dashed segment ring, and
 * a thin static halo. Replicates the cinematic "iron-man HUD" look.
 *
 * <p>Each rotating layer has its own {@link RingRotator}; the pipeline
 * accelerates them during {@code THINKING} and slows them for
 * {@code SPEAKING}.
 */
public final class HudRings extends StackPane {

    private final Arc arcOuter;
    private final Arc arcInner;
    private final Arc dashedRing;
    private final Circle staticHalo;
    private final List<Line> tickMarks = new ArrayList<>();

    private final List<RingRotator> rotators;

    public HudRings(double outerDiameter) {
        setPickOnBounds(false);

        // ---- outer rotating arc (slow, big sweep) ----
        arcOuter = ring(outerDiameter * 1.00, 260, 1.4);

        // ---- inner rotating arc (faster, smaller sweep, opposite direction) ----
        arcInner = ring(outerDiameter * 0.82, 200, 1.6);

        // ---- dashed segment ring (medium speed, full circle dashed) ----
        dashedRing = ring(outerDiameter * 0.66, 360, 1.0);
        dashedRing.getStrokeDashArray().addAll(2.0, 8.0);

        // ---- static thin halo just outside the core ----
        staticHalo = new Circle(outerDiameter * 0.40);
        staticHalo.setFill(Color.TRANSPARENT);
        staticHalo.setStroke(Color.web("#00d4ff", 0.55));
        staticHalo.setStrokeWidth(0.8);
        staticHalo.setStrokeType(StrokeType.OUTSIDE);

        // ---- tick marks around the outermost circumference ----
        buildTickMarks(outerDiameter * 1.06, outerDiameter * 1.10, 64);

        getChildren().add(staticHalo);
        getChildren().addAll(tickMarks);
        getChildren().addAll(dashedRing, arcInner, arcOuter);

        rotators = List.of(
                new RingRotator(arcOuter,   Duration.seconds(22), true),
                new RingRotator(arcInner,   Duration.seconds(14), false),
                new RingRotator(dashedRing, Duration.seconds(40), true));
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

    private void buildTickMarks(double innerRadius, double outerRadius, int count) {
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI * i) / count;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            // Every 4th tick is longer/brighter.
            boolean major = i % 4 == 0;
            double rInner = innerRadius;
            double rOuter = major ? outerRadius : (innerRadius + (outerRadius - innerRadius) * 0.55);
            Line l = new Line(cos * rInner, sin * rInner, cos * rOuter, sin * rOuter);
            l.setStroke(Color.web("#00d4ff", major ? 0.85 : 0.45));
            l.setStrokeWidth(major ? 1.1 : 0.6);
            l.setStrokeLineCap(StrokeLineCap.ROUND);
            tickMarks.add(l);
        }
    }

    public void onStateChanged(AssistantState state) {
        switch (state) {
            case SLEEP     -> setSpeeds(45, 32, 60);
            case WAKING    -> setSpeeds(8, 6, 12);
            case LISTENING -> setSpeeds(22, 14, 40);
            case THINKING  -> setSpeeds(5, 3.2, 10);
            case SPEAKING  -> setSpeeds(14, 9, 22);
            case ERROR     -> setSpeeds(70, 70, 70);
        }
        Color tint = switch (state) {
            case THINKING -> Color.web("#a78bfa");
            case SPEAKING -> Color.web("#ff9a3c");
            case ERROR    -> Color.web("#ff3b3b");
            default       -> Color.web("#00d4ff");
        };
        applyTint(tint);
    }

    private void setSpeeds(double s1, double s2, double s3) {
        rotators.get(0).setPeriod(Duration.seconds(s1));
        rotators.get(1).setPeriod(Duration.seconds(s2));
        rotators.get(2).setPeriod(Duration.seconds(s3));
    }

    private void applyTint(Color color) {
        arcOuter.setStroke(color);
        arcInner.setStroke(color);
        dashedRing.setStroke(color.deriveColor(0, 1, 1, 0.7));
        staticHalo.setStroke(color.deriveColor(0, 1, 1, 0.55));
        // Tick marks keep their per-element alpha but pick up the hue.
        for (int i = 0; i < tickMarks.size(); i++) {
            Line l = tickMarks.get(i);
            boolean major = i % 4 == 0;
            l.setStroke(color.deriveColor(0, 1, 1, major ? 0.85 : 0.45));
        }
    }
}

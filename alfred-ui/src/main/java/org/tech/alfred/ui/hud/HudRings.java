package org.tech.alfred.ui.hud;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeType;
import javafx.util.Duration;

import org.tech.alfred.ui.animation.RingRotator;
import org.tech.alfred.ui.state.AssistantState;

/**
 * Multi-layered HUD reticle: a fixed ring of tick marks on the outside,
 * two rotating "open-arc" rings, a dashed segment ring, and a thin
 * static halo. Together they read as a sci-fi targeting reticle.
 *
 * <p>Implementation note: the "arc that rotates" effect is faked with
 * a full {@link Circle} whose stroke uses a single big dash + matching
 * gap. Drawing a real {@link javafx.scene.shape.Arc} works, but a
 * partial-sweep Arc has an asymmetric bounding box — {@code RotateTransition}
 * then pivots around the bounds-center rather than the geometric center,
 * making the arc visibly wobble off-axis. A full Circle has square,
 * symmetric bounds, so rotation pivots cleanly through (0,0).
 *
 * <p>All radial elements share a single {@link Group} so the {@link StackPane}
 * lays them out as one unit instead of centering each shape individually
 * (which would collapse the tick marks into the middle).
 */
public final class HudRings extends StackPane {

    private final Circle ringOuter;
    private final Circle ringInner;
    private final Circle dashedRing;
    private final Circle staticHalo;
    private final List<Line> tickMarks = new ArrayList<>();
    private final List<RingRotator> rotators;

    public HudRings(double outerDiameter) {
        setPickOnBounds(false);

        // ---- outer rotating "arc": full Circle, 260° dash + 100° gap ----
        ringOuter = openArcRing(outerDiameter * 1.00, 260, 1.4);

        // ---- inner rotating "arc": 200° dash + 160° gap, opposite direction ----
        ringInner = openArcRing(outerDiameter * 0.82, 200, 1.6);

        // ---- dashed segment ring: full circle with many small dashes ----
        dashedRing = new Circle(outerDiameter * 0.66 / 2.0);
        dashedRing.setFill(Color.TRANSPARENT);
        dashedRing.setStrokeWidth(1.0);
        dashedRing.setStrokeLineCap(StrokeLineCap.ROUND);
        dashedRing.getStrokeDashArray().addAll(2.0, 8.0);
        dashedRing.getStyleClass().add("hud-ring");

        // ---- static thin halo just outside the core ----
        staticHalo = new Circle(outerDiameter * 0.40);
        staticHalo.setFill(Color.TRANSPARENT);
        staticHalo.setStroke(Color.web("#00d4ff", 0.55));
        staticHalo.setStrokeWidth(0.8);
        staticHalo.setStrokeType(StrokeType.OUTSIDE);

        // ---- tick marks around the outside ----
        buildTickMarks(outerDiameter * 0.54, outerDiameter * 0.58, 64);

        // All radial geometry in one managed Group so coordinates stay shared
        // and the StackPane lays out the whole composition as a single unit.
        Group radials = new Group();
        radials.setPickOnBounds(false);
        radials.getChildren().add(staticHalo);
        radials.getChildren().addAll(tickMarks);
        radials.getChildren().addAll(dashedRing, ringInner, ringOuter);
        getChildren().add(radials);

        rotators = List.of(
                new RingRotator(ringOuter,  Duration.seconds(22), true),
                new RingRotator(ringInner,  Duration.seconds(14), false),
                new RingRotator(dashedRing, Duration.seconds(40), true));
        rotators.forEach(RingRotator::play);

        getStyleClass().add("hud-rings");
        applyTint(Color.web("#00d4ff"));
    }

    /**
     * Build a Circle that draws as a single open arc covering the given
     * sweep (in degrees), with the rest of the circumference as a gap.
     * Rotating the Circle gives the same visual as rotating an Arc, but
     * without the off-axis wobble caused by Arc's asymmetric bounds.
     */
    private static Circle openArcRing(double diameter, double sweepDegrees, double strokeWidth) {
        double radius = diameter / 2.0;
        double circumference = 2 * Math.PI * radius;
        double dashLen = circumference * (sweepDegrees / 360.0);
        double gapLen = circumference - dashLen;

        Circle c = new Circle(radius);
        c.setFill(Color.TRANSPARENT);
        c.setStrokeWidth(strokeWidth);
        c.setStrokeLineCap(StrokeLineCap.ROUND);
        c.getStrokeDashArray().addAll(dashLen, gapLen);
        c.getStyleClass().add("hud-ring");
        return c;
    }

    private void buildTickMarks(double innerRadius, double outerRadius, int count) {
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI * i) / count;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
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
        ringOuter.setStroke(color);
        ringInner.setStroke(color);
        dashedRing.setStroke(color.deriveColor(0, 1, 1, 0.7));
        staticHalo.setStroke(color.deriveColor(0, 1, 1, 0.55));
        for (int i = 0; i < tickMarks.size(); i++) {
            Line l = tickMarks.get(i);
            boolean major = i % 4 == 0;
            l.setStroke(color.deriveColor(0, 1, 1, major ? 0.85 : 0.45));
        }
    }
}

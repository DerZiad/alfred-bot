package org.tech.alfred.ui.hud;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeType;
import javafx.util.Duration;

/**
 * Full-screen boot animation. Sequenced overlay that plays during the
 * {@code WAKING} state and self-removes when complete.
 *
 * <p>Choreography:
 * <ol>
 *   <li>Concentric rings expand from the center, fading as they grow.</li>
 *   <li>"ALFRED ONLINE" text fades in line-by-line like a system console.</li>
 *   <li>The overlay fades out, handing the scene to LISTENING mode.</li>
 * </ol>
 */
public final class BootSequence extends StackPane {

    private final Runnable onComplete;
    private final SequentialTransition timeline;

    public BootSequence(Runnable onComplete) {
        this.onComplete = onComplete;
        setMouseTransparent(true);
        getStyleClass().add("boot-overlay");
        setAlignment(Pos.CENTER);

        Circle r1 = expandingRing();
        Circle r2 = expandingRing();
        Circle r3 = expandingRing();

        VBox bootText = new VBox(4);
        bootText.setAlignment(Pos.CENTER);
        Label l1 = bootLine("> INITIALISING ALFRED OS v4.0.6");
        Label l2 = bootLine("> NEURAL CORE ONLINE");
        Label l3 = bootLine("> VOICE INTERFACE: ACTIVE");
        Label l4 = bootLine("> AT YOUR SERVICE, SIR.");
        bootText.getChildren().addAll(l1, l2, l3, l4);

        getChildren().addAll(r1, r2, r3, bootText);

        // Stagger ring expansions.
        ParallelTransition rings = new ParallelTransition(
                expand(r1, 0),
                expand(r2, 180),
                expand(r3, 360));

        SequentialTransition lines = new SequentialTransition(
                appear(l1, 0),
                appear(l2, 220),
                appear(l3, 220),
                appear(l4, 260));

        ParallelTransition body = new ParallelTransition(rings, lines);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(600), this);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        PauseTransition hold = new PauseTransition(Duration.millis(300));

        timeline = new SequentialTransition(body, hold, fadeOut);
        timeline.setOnFinished(e -> {
            setVisible(false);
            if (this.onComplete != null) this.onComplete.run();
        });
    }

    public void play() {
        setOpacity(1);
        setVisible(true);
        timeline.playFromStart();
    }

    private static Circle expandingRing() {
        Circle c = new Circle(8);
        c.setFill(Color.TRANSPARENT);
        c.setStroke(Color.web("#00d4ff"));
        c.setStrokeWidth(1.4);
        c.setStrokeType(StrokeType.OUTSIDE);
        c.setOpacity(0);
        return c;
    }

    private static ParallelTransition expand(Circle c, double delayMs) {
        Timeline radius = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(c.radiusProperty(), 8, Interpolator.EASE_OUT)),
                new KeyFrame(Duration.millis(1100),
                        new KeyValue(c.radiusProperty(), 480, Interpolator.EASE_OUT)));
        Timeline opacity = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(c.opacityProperty(), 0, Interpolator.LINEAR)),
                new KeyFrame(Duration.millis(120),
                        new KeyValue(c.opacityProperty(), 0.85, Interpolator.EASE_OUT)),
                new KeyFrame(Duration.millis(1100),
                        new KeyValue(c.opacityProperty(), 0.0, Interpolator.EASE_IN)));
        ParallelTransition pt = new ParallelTransition(radius, opacity);
        pt.setDelay(Duration.millis(delayMs));
        return pt;
    }

    private static Label bootLine(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("boot-line");
        l.setOpacity(0);
        return l;
    }

    private static ParallelTransition appear(Label l, double delayMs) {
        l.setScaleX(0.96);
        l.setScaleY(0.96);
        FadeTransition fade = new FadeTransition(Duration.millis(240), l);
        fade.setFromValue(0);
        fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(260), l);
        scale.setToX(1.0);
        scale.setToY(1.0);
        ParallelTransition pt = new ParallelTransition(fade, scale);
        pt.setDelay(Duration.millis(delayMs));
        return pt;
    }
}

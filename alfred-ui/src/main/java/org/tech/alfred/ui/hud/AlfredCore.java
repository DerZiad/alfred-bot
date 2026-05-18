package org.tech.alfred.ui.hud;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.Bloom;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;

import org.tech.alfred.ui.animation.PulseAnimation;
import org.tech.alfred.ui.state.AssistantState;

/**
 * The central glowing disc — Alfred's "heart". A stack of concentric
 * radial-gradient circles plus a center text panel showing
 * "ALFRED / ONLINE". The combination produces a multi-layered sci-fi
 * glow that reads as "alive".
 *
 * <p>Reacts to:
 * <ul>
 *   <li>State: hue shift cyan → orange across SLEEP/LISTENING/THINKING/SPEAKING,
 *       intensity ramp on WAKING. Center text changes to the current state name.</li>
 *   <li>{@link #levelProperty()}: live audio RMS (0..1) modulates pulse
 *       amplitude so the core visibly breathes with the user's voice.</li>
 * </ul>
 */
public final class AlfredCore extends StackPane {

    private static final Color CYAN = Color.web("#00d4ff");
    private static final Color ORANGE = Color.web("#ff9a3c");
    private static final Color VIOLET = Color.web("#a78bfa");

    private final Circle outerHalo;
    private final Circle midGlow;
    private final Circle innerDisc;
    private final Circle innerRim;
    private final Circle hotspot;

    private final Label titleLabel;
    private final Label statusLabel;

    private final PulseAnimation pulse;
    private final DoubleProperty level = new SimpleDoubleProperty(0);

    public AlfredCore(double diameter) {
        setPickOnBounds(false);

        // ----- glow stack (back to front) -----
        outerHalo = new Circle(diameter * 0.95);
        midGlow   = new Circle(diameter * 0.65);
        innerDisc = new Circle(diameter * 0.42);
        innerRim  = new Circle(diameter * 0.42);
        hotspot   = new Circle(diameter * 0.12);

        outerHalo.setEffect(new GaussianBlur(38));
        midGlow.setEffect(new GaussianBlur(16));
        innerDisc.setEffect(new Bloom(0.45));
        hotspot.setEffect(new Bloom(0.85));

        // Additive blending on the upper layers creates the bright bloom feel.
        midGlow.setBlendMode(BlendMode.ADD);
        innerDisc.setBlendMode(BlendMode.ADD);
        hotspot.setBlendMode(BlendMode.ADD);

        // Crisp rim around the inner disc (no blur — keeps it readable).
        innerRim.setFill(Color.TRANSPARENT);
        innerRim.setStroke(CYAN);
        innerRim.setStrokeWidth(1.4);

        // ----- centered "ALFRED / ONLINE" text -----
        titleLabel = new Label("ALFRED");
        titleLabel.getStyleClass().add("alfred-core-title");

        statusLabel = new Label("ONLINE");
        statusLabel.getStyleClass().add("alfred-core-status");

        VBox textStack = new VBox(2, titleLabel, statusLabel);
        textStack.setAlignment(Pos.CENTER);
        textStack.setMouseTransparent(true);

        getChildren().addAll(outerHalo, midGlow, innerDisc, innerRim, hotspot, textStack);
        StackPane.setAlignment(textStack, Pos.CENTER);

        applyPalette(CYAN);

        pulse = new PulseAnimation(this, 1.0, 0.035, 0.4);
        pulse.play();

        level.addListener((obs, o, n) -> pulse.setExternalLevel(n.doubleValue()));
    }

    public DoubleProperty levelProperty() { return level; }

    public void onStateChanged(AssistantState state) {
        switch (state) {
            case SLEEP -> {
                applyPalette(CYAN.deriveColor(0, 1, 0.5, 0.7));
                pulse.setFrequencyHz(0.25);
                statusLabel.setText("STANDBY");
            }
            case WAKING -> {
                applyPalette(CYAN);
                pulse.setFrequencyHz(1.6);
                statusLabel.setText("BOOTING");
            }
            case LISTENING -> {
                applyPalette(CYAN);
                pulse.setFrequencyHz(0.7);
                statusLabel.setText("ONLINE");
            }
            case THINKING -> {
                applyPalette(VIOLET);
                pulse.setFrequencyHz(1.2);
                statusLabel.setText("THINKING");
            }
            case SPEAKING -> {
                applyPalette(ORANGE);
                pulse.setFrequencyHz(0.9);
                statusLabel.setText("SPEAKING");
            }
            case ERROR -> {
                applyPalette(Color.web("#ff3b3b"));
                statusLabel.setText("ERROR");
            }
        }
    }

    private void applyPalette(Color accent) {
        outerHalo.setFill(radial(accent.deriveColor(0, 1, 1, 0.12),
                                  accent.deriveColor(0, 1, 1, 0.0)));
        midGlow.setFill(radial(accent.deriveColor(0, 1, 1.1, 0.55),
                                accent.deriveColor(0, 1, 1, 0.0)));
        // Inner disc: dark center fading to accent edge — gives the
        // mockup's "glowing portal" look.
        innerDisc.setFill(radial(Color.web("#0a1a2e", 0.95),
                                   accent.deriveColor(0, 1, 1.2, 0.6)));
        innerRim.setStroke(accent.deriveColor(0, 1, 1.15, 0.9));
        hotspot.setFill(radial(Color.WHITE,
                                Color.WHITE.deriveColor(0, 1, 1, 0.0)));
    }

    private static RadialGradient radial(Color inner, Color outer) {
        return new RadialGradient(
                0, 0, 0.5, 0.5, 0.5, true,
                CycleMethod.NO_CYCLE,
                new Stop(0, inner), new Stop(1, outer));
    }
}

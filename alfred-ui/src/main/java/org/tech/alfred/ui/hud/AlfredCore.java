package org.tech.alfred.ui.hud;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.Bloom;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;

import org.tech.alfred.ui.animation.PulseAnimation;
import org.tech.alfred.ui.state.AssistantState;

/**
 * The central glowing orb - Alfred's "heart". A stack of concentric
 * radial-gradient circles, each with its own bloom. The combination
 * produces a soft, multi-layered sci-fi glow that reads as "alive".
 *
 * <p>Reacts to:
 * <ul>
 *   <li>State: hue shift cyan-&gt;orange across SLEEP/LISTENING/THINKING/SPEAKING,
 *       intensity ramp on WAKING.</li>
 *   <li>{@link #levelProperty()}: live audio RMS (0..1) modulates pulse
 *       amplitude so the core visibly breathes with the user's voice.</li>
 * </ul>
 */
public final class AlfredCore extends StackPane {

    private static final Color CYAN = Color.web("#00d4ff");
    private static final Color ORANGE = Color.web("#ff7a18");
    private static final Color VIOLET = Color.web("#7c3aed");

    private final Circle outerHalo;
    private final Circle midGlow;
    private final Circle innerCore;
    private final Circle hotspot;

    private final PulseAnimation pulse;
    private final DoubleProperty level = new SimpleDoubleProperty(0);

    public AlfredCore(double diameter) {
        setPickOnBounds(false);

        outerHalo = new Circle(diameter * 0.95);
        midGlow   = new Circle(diameter * 0.55);
        innerCore = new Circle(diameter * 0.32);
        hotspot   = new Circle(diameter * 0.12);

        applyPalette(CYAN);

        outerHalo.setEffect(new GaussianBlur(40));
        midGlow.setEffect(new GaussianBlur(18));
        innerCore.setEffect(new Bloom(0.6));
        hotspot.setEffect(new Bloom(0.85));

        // Additive blending on the upper layers creates the bright bloom feel.
        midGlow.setBlendMode(BlendMode.ADD);
        innerCore.setBlendMode(BlendMode.ADD);
        hotspot.setBlendMode(BlendMode.ADD);

        getChildren().addAll(outerHalo, midGlow, innerCore, hotspot);

        pulse = new PulseAnimation(this, 1.0, 0.04, 0.4);
        pulse.play();

        level.addListener((obs, o, n) -> pulse.setExternalLevel(n.doubleValue()));
    }

    public DoubleProperty levelProperty() { return level; }

    /**
     * Update visuals when the assistant state changes. Called from
     * {@link HudView}'s state listener on the FX thread.
     */
    public void onStateChanged(AssistantState state) {
        switch (state) {
            case SLEEP -> {
                applyPalette(CYAN.deriveColor(0, 1, 0.5, 0.6));
                pulse.setFrequencyHz(0.25);
            }
            case WAKING -> {
                applyPalette(CYAN);
                pulse.setFrequencyHz(1.6);
            }
            case LISTENING -> {
                applyPalette(CYAN);
                pulse.setFrequencyHz(0.7);
            }
            case THINKING -> {
                applyPalette(VIOLET);
                pulse.setFrequencyHz(1.2);
            }
            case SPEAKING -> {
                applyPalette(ORANGE);
                pulse.setFrequencyHz(0.9);
            }
            case ERROR -> applyPalette(Color.web("#ff3b3b"));
        }
    }

    private void applyPalette(Color accent) {
        outerHalo.setFill(radial(accent.deriveColor(0, 1, 1, 0.10),
                                  accent.deriveColor(0, 1, 1, 0.0)));
        midGlow.setFill(radial(accent.deriveColor(0, 1, 1.1, 0.55),
                                accent.deriveColor(0, 1, 1, 0.0)));
        innerCore.setFill(radial(Color.WHITE,
                                  accent.deriveColor(0, 1, 1.4, 0.85)));
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

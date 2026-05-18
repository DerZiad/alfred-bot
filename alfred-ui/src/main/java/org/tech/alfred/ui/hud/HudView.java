package org.tech.alfred.ui.hud;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.tech.alfred.ui.state.AssistantState;
import org.tech.alfred.ui.state.AssistantStateMachine;

/**
 * Root composition of every visible cinematic HUD element. Lays out:
 *
 * <pre>
 *   +----------------------------------------------------+
 *   |              [ particle field background ]        |
 *   |                                                    |
 *   |   [STATUS panel]    [ rings + core ]   [TRANSCRIPT]|
 *   |                       [ waveform ]                 |
 *   |                       [ spectrum ]                 |
 *   |   [HINTS panel]                        [CLOCK]     |
 *   +----------------------------------------------------+
 *   [        boot sequence overlay on top         ]
 * </pre>
 *
 * <p>Subscribes to the {@link AssistantStateMachine} once on construction
 * and propagates every transition to the relevant components.
 */
public final class HudView extends StackPane {

    private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AssistantStateMachine stateMachine;

    private final ParticleField particles;
    private final AlfredCore core;
    private final HudRings rings;
    private final Waveform waveform;
    private final SpectrumBars spectrum;
    @SuppressWarnings("unused") private final HolographicPanel statusPanel;
    @SuppressWarnings("unused") private final HolographicPanel hintsPanel;
    @SuppressWarnings("unused") private final HolographicPanel clockPanel;
    private final TranscriptPanel transcript;
    private final BootSequence boot;

    private final Label statusValue;
    private final Label clockValue;

    public HudView(AssistantStateMachine stateMachine, Runnable onWakeComplete) {
        this.stateMachine = stateMachine;
        getStyleClass().add("hud-root");
        setPadding(new Insets(20));

        // --- background ---
        particles = new ParticleField(1280, 800);
        // Use a clamped listener (not bind) so the canvas is never 0×0,
        // which would crash the prism renderer.
        particles.followSize(widthProperty(), heightProperty());
        particles.setMouseTransparent(true);

        // --- center stack: rings + core + reactive viz ---
        StackPane centerStack = new StackPane();
        centerStack.setMouseTransparent(true);
        rings = new HudRings(520);
        core = new AlfredCore(240);

        waveform = new Waveform(420, 90);
        waveform.setMouseTransparent(true);
        spectrum = new SpectrumBars(420, 90);
        spectrum.setMouseTransparent(true);
        spectrum.setVisible(false);
        waveform.setVisible(false);

        VBox vizColumn = new VBox(8, waveform, spectrum);
        vizColumn.setAlignment(Pos.CENTER);
        vizColumn.setTranslateY(170);
        vizColumn.setMouseTransparent(true);

        centerStack.getChildren().addAll(rings, core, vizColumn);

        // --- side panels ---
        statusPanel = new HolographicPanel("STATUS");
        statusValue = statusPanel.addLine("SLEEPING");
        statusPanel.addLine("Say \"Jarvis\" to wake.");

        hintsPanel = new HolographicPanel("CONTROLS");
        hintsPanel.addLine("Wake word:  \"Jarvis\"");
        hintsPanel.addLine("Manual wake:  SPACE");
        hintsPanel.addLine("Sleep:  ESC");

        clockPanel = new HolographicPanel("CHRONO");
        clockValue = clockPanel.addLine(LocalDateTime.now().format(CLOCK_FMT));
        clockPanel.addLine("Local AI Core 4.0.6");

        transcript = new TranscriptPanel();
        transcript.setPrefWidth(360);
        transcript.setMaxHeight(Double.MAX_VALUE);

        HolographicPanel transcriptPanel = new HolographicPanel("CONVERSATION");
        VBox.setVgrow(transcript, Priority.ALWAYS);
        transcriptPanel.getChildren().add(transcript);
        transcriptPanel.setPrefWidth(380);

        VBox leftColumn = new VBox(16, statusPanel, hintsPanel);
        leftColumn.setMaxWidth(280);
        leftColumn.setAlignment(Pos.TOP_LEFT);

        VBox rightColumn = new VBox(16, clockPanel, transcriptPanel);
        rightColumn.setAlignment(Pos.TOP_RIGHT);
        VBox.setVgrow(transcriptPanel, Priority.ALWAYS);
        rightColumn.setMaxWidth(380);

        // --- arrange with anchor pane ---
        AnchorPane anchor = new AnchorPane();
        anchor.setMouseTransparent(true);

        AnchorPane.setLeftAnchor(leftColumn, 24.0);
        AnchorPane.setTopAnchor(leftColumn, 24.0);
        AnchorPane.setRightAnchor(rightColumn, 24.0);
        AnchorPane.setTopAnchor(rightColumn, 24.0);
        AnchorPane.setBottomAnchor(rightColumn, 24.0);

        StackPane.setAlignment(centerStack, Pos.CENTER);

        anchor.getChildren().addAll(leftColumn, rightColumn);

        // --- boot overlay ---
        boot = new BootSequence(onWakeComplete);
        boot.setVisible(false);

        getChildren().addAll(particles, centerStack, anchor, boot);

        // --- listen to state ---
        stateMachine.stateProperty().addListener((obs, prev, next) -> applyState(next));
        applyState(stateMachine.current());

        // --- clock tick ---
        new AnimationTimer() {
            long lastSec = -1;
            @Override
            public void handle(long now) {
                long sec = now / 1_000_000_000L;
                if (sec != lastSec) {
                    lastSec = sec;
                    clockValue.setText(LocalDateTime.now().format(CLOCK_FMT));
                }
            }
        }.start();
    }

    public AlfredCore core() { return core; }
    public Waveform waveform() { return waveform; }
    public SpectrumBars spectrum() { return spectrum; }
    public TranscriptPanel transcript() { return transcript; }
    public ParticleField particles() { return particles; }

    /** Trigger the WAKING boot animation. Call after state transitions to WAKING. */
    public void playBootSequence() {
        Platform.runLater(boot::play);
    }

    private void applyState(AssistantState state) {
        core.onStateChanged(state);
        rings.onStateChanged(state);
        particles.onStateChanged(state);

        switch (state) {
            case SLEEP -> {
                statusValue.setText("SLEEPING");
                waveform.setVisible(false);
                spectrum.setVisible(false);
            }
            case WAKING -> {
                statusValue.setText("WAKING UP…");
                waveform.setVisible(false);
                spectrum.setVisible(false);
            }
            case LISTENING -> {
                statusValue.setText("LISTENING");
                waveform.setVisible(true);
                spectrum.setVisible(false);
            }
            case THINKING -> {
                statusValue.setText("THINKING");
                waveform.setVisible(false);
                spectrum.setVisible(false);
            }
            case SPEAKING -> {
                statusValue.setText("SPEAKING");
                waveform.setVisible(false);
                spectrum.setVisible(true);
            }
            case ERROR -> {
                statusValue.setText("ERROR");
                waveform.setVisible(false);
                spectrum.setVisible(false);
            }
        }
    }
}

package org.tech.alfred.ui.view;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;

import org.tech.alfred.ui.bus.AssistantEvent;
import org.tech.alfred.ui.bus.EventBus;
import org.tech.alfred.ui.hud.HudView;
import org.tech.alfred.ui.state.AssistantState;
import org.tech.alfred.ui.state.AssistantStateMachine;
import org.tech.alfred.ui.voice.PlaybackService;
import org.tech.alfred.ui.voice.VoicePipeline;

/**
 * Slim controller for {@code main.fxml}. Most behavior lives in the HUD
 * components and the {@link VoicePipeline}; this class is just the glue:
 * mount the HUD into the FXML container, wire keyboard shortcuts, route
 * pipeline events into the transcript panel.
 *
 * <p>Keyboard shortcuts (global to the scene):
 * <ul>
 *   <li>SPACE  - wake Alfred</li>
 *   <li>ESC    - put Alfred to sleep</li>
 *   <li>ENTER  - send the contents of the typing fallback (TODO: text input field)</li>
 * </ul>
 */
@Component
public class MainController {

    @FXML private StackPane rootContainer;

    private final AssistantStateMachine fsm;
    private final VoicePipeline pipeline;
    private final EventBus bus;
    private final PlaybackService playback;
    private HudView hud;

    public MainController(AssistantStateMachine fsm,
                          VoicePipeline pipeline,
                          EventBus bus,
                          PlaybackService playback) {
        this.fsm = fsm;
        this.pipeline = pipeline;
        this.bus = bus;
        this.playback = playback;
    }

    @FXML
    public void initialize() {
        hud = new HudView(fsm, pipeline::onBootComplete);
        rootContainer.getChildren().setAll(hud);

        // Bind audio-reactive nodes to live levels. The core and waveform
        // react to the live mic (user speaking); the spectrum reacts to
        // Alfred's TTS playback to avoid feedback from speakers.
        hud.core().levelProperty().bind(pipeline.levelMeter().levelProperty());
        hud.waveform().levelProperty().bind(pipeline.levelMeter().levelProperty());
        hud.spectrum().levelProperty().bind(playback.levelProperty());

        // Trigger boot animation on entering WAKING.
        fsm.stateProperty().addListener((obs, prev, next) -> {
            if (next == AssistantState.WAKING) {
                hud.playBootSequence();
            }
        });

        // Route bus events into the transcript panel.
        bus.stream().subscribe(event -> {
            switch (event) {
                case AssistantEvent.UserUtterance u ->
                        hud.transcript().appendUser(u.text());
                case AssistantEvent.AssistantToken t ->
                        hud.transcript().appendAssistantToken(t.token());
                case AssistantEvent.AssistantResponseComplete ignored ->
                        hud.transcript().sealStream();
                default -> {}
            }
        });

        // Keyboard shortcuts - installed once the scene exists.
        rootContainer.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                scene.setOnKeyPressed(e -> {
                    if (e.getCode() == KeyCode.SPACE) {
                        pipeline.requestWake("keyboard");
                    } else if (e.getCode() == KeyCode.ESCAPE) {
                        pipeline.requestSleep("keyboard");
                    }
                });
                Platform.runLater(rootContainer::requestFocus);
            }
        });

        // Start mic + wake detection AFTER the HUD is wired so the WAKING
        // listener is in place before any wake event can fire.
        pipeline.start();
    }

    @PreDestroy
    public void shutdown() {
        pipeline.stop();
    }
}

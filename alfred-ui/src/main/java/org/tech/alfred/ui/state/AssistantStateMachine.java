package org.tech.alfred.ui.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Central FSM for Alfred's UI. Wrapped in a JavaFX {@link ObjectProperty}
 * so HUD components can {@code addListener} and react reactively to state
 * changes without polling.
 *
 * <p>All state writes are marshalled onto the JavaFX Application Thread
 * because property listeners may mutate the scene graph. Callers from
 * Reactor schedulers can call {@link #request(AssistantState)} freely; the
 * marshalling happens here.
 *
 * <p>Transitions are not strictly gated (any -&gt; any is allowed) - the
 * pipeline orchestrator is responsible for sensible sequencing. We log
 * every transition for observability.
 */
@Component
public class AssistantStateMachine {

    private static final Logger log = LoggerFactory.getLogger(AssistantStateMachine.class);

    private final ObjectProperty<AssistantState> state =
            new SimpleObjectProperty<>(AssistantState.SLEEP);

    public ReadOnlyObjectProperty<AssistantState> stateProperty() {
        return state;
    }

    public AssistantState current() {
        return state.get();
    }

    /**
     * Request a transition. Safe to call from any thread - the actual
     * property write hops to the FX thread.
     */
    public void request(AssistantState next) {
        if (Platform.isFxApplicationThread()) {
            apply(next);
        } else {
            Platform.runLater(() -> apply(next));
        }
    }

    private void apply(AssistantState next) {
        AssistantState prev = state.get();
        if (prev == next) return;
        log.debug("state: {} -> {}", prev, next);
        state.set(next);
    }
}

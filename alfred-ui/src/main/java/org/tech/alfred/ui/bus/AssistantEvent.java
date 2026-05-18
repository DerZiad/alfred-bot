package org.tech.alfred.ui.bus;

import org.tech.alfred.ui.state.AssistantState;

/**
 * Cross-component signals emitted on the {@link EventBus}. Sealed so the
 * compiler enforces exhaustive handling in switch expressions.
 *
 * <p>State transitions go through {@link org.tech.alfred.ui.state.AssistantStateMachine}
 * directly (they're a property, not an event). The bus carries
 * <em>discrete moments</em> the HUD wants to react to: a token arriving,
 * a transcript finalizing, an error.
 */
public sealed interface AssistantEvent
        permits AssistantEvent.WakeRequested,
        AssistantEvent.SleepRequested,
        AssistantEvent.UserUtterance,
        AssistantEvent.AssistantToken,
        AssistantEvent.AssistantResponseComplete,
        AssistantEvent.StateChanged,
        AssistantEvent.Error {

    /** User asked Alfred to wake (wake-word, keyboard shortcut, or button). */
    record WakeRequested(String source) implements AssistantEvent {}

    /** User asked Alfred to go to sleep. */
    record SleepRequested(String source) implements AssistantEvent {}

    /** Final transcript of the user's utterance, ready to send to the LLM. */
    record UserUtterance(String text) implements AssistantEvent {}

    /** A single streamed token from the LLM. */
    record AssistantToken(String token) implements AssistantEvent {}

    /** LLM stream complete; full assembled text included. */
    record AssistantResponseComplete(String fullText) implements AssistantEvent {}

    /** Mirror of state-machine transitions for components that prefer events. */
    record StateChanged(AssistantState from, AssistantState to) implements AssistantEvent {}

    /** Something went wrong somewhere in the pipeline. */
    record Error(String stage, Throwable cause) implements AssistantEvent {}
}

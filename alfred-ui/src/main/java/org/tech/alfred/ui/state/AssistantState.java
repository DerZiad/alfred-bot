package org.tech.alfred.ui.state;

/**
 * The high-level states Alfred can be in. The HUD observes the
 * {@link AssistantStateMachine} and re-skins itself on every transition:
 * different glow intensities, which panels are visible, which animations
 * are running.
 *
 * <p>Transitions are driven by the {@link org.tech.alfred.ui.voice.VoicePipeline}
 * and by user input (keyboard fallback, wake word). The state graph:
 *
 * <pre>
 *   SLEEP
 *     | wake event ("jarvis", manual press, "wake up alfred")
 *     v
 *   WAKING -- 1.2s boot sequence --> LISTENING
 *
 *   LISTENING
 *     | end-of-utterance (VAD silence)
 *     v
 *   THINKING
 *     | first LLM token
 *     v
 *   SPEAKING
 *     | TTS playback complete
 *     v
 *   LISTENING (loops until "go to sleep" or manual sleep)
 *
 *   any -> ERROR on unrecoverable failure -> SLEEP after 3s
 * </pre>
 */
public enum AssistantState {

    /** Idle. Low-power ambient glow, mic muted (wake-word detector still on). */
    SLEEP,

    /** Boot animation playing. Rings expanding, particles igniting, HUD assembling. */
    WAKING,

    /** Microphone active, waveform visualized, waiting for the user to speak. */
    LISTENING,

    /** LLM working. Concentric rings spinning, scan lines, transcript shows ellipsis. */
    THINKING,

    /** TTS playing back. Spectrum bars dance, core pulse synced to audio level. */
    SPEAKING,

    /** Unrecoverable error. Red-shifted UI for 3s, then auto-returns to SLEEP. */
    ERROR;

    public boolean isActive() {
        return this != SLEEP && this != ERROR;
    }
}

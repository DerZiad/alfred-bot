package org.tech.alfred.ui.voice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.tech.alfred.core.audio.AudioCapture;
import org.tech.alfred.core.audio.AudioFormatSpec;
import org.tech.alfred.core.audio.AudioFrame;
import org.tech.alfred.core.audio.SpeechToText;
import org.tech.alfred.core.audio.TextToSpeech;
import org.tech.alfred.core.audio.Transcript;
import org.tech.alfred.core.chat.ChatRequest;
import org.tech.alfred.core.chat.ChatService;
import org.tech.alfred.core.chat.Message;
import org.tech.alfred.core.memory.MemoryEntry;
import org.tech.alfred.core.memory.MemoryStore;
import org.tech.alfred.core.wake.WakeWordDetector;
import org.tech.alfred.ui.bus.AssistantEvent;
import org.tech.alfred.ui.bus.EventBus;
import org.tech.alfred.ui.conversation.ConversationState;
import org.tech.alfred.ui.state.AssistantState;
import org.tech.alfred.ui.state.AssistantStateMachine;

/**
 * The single brain of Alfred's runtime - orchestrates the wake-word →
 * STT → LLM → TTS chain, drives the state machine, and publishes
 * events for the HUD.
 *
 * <p>One {@link AudioCapture} subscription is shared (via {@code .publish().refCount()})
 * across three consumers:
 * <ol>
 *   <li>{@link WakeWordDetector} - always on in SLEEP.</li>
 *   <li>{@link AudioLevelMeter} - always on (drives waveform/core pulse).</li>
 *   <li>Utterance buffer - active only in LISTENING.</li>
 * </ol>
 *
 * <p>The pipeline is reactive end-to-end; every transition is logged and
 * pushed onto the {@link AssistantStateMachine} and {@link EventBus}.
 */
@Component
public class VoicePipeline {

    private static final Logger log = LoggerFactory.getLogger(VoicePipeline.class);

    private final AudioCapture capture;
    private final WakeWordDetector wakeDetector;
    private final SpeechToText stt;
    private final TextToSpeech tts;
    private final ChatService chat;
    private final MemoryStore memory;
    private final ConversationState conversation;
    private final AssistantStateMachine fsm;
    private final EventBus bus;
    private final PlaybackService playback;
    private final AudioLevelMeter levelMeter;

    private final double silenceThreshold;
    private final long silenceMillis;
    private final long maxUtteranceMillis;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<Disposable> wakeSub = new AtomicReference<>();
    private final AtomicReference<Disposable> levelSub = new AtomicReference<>();
    private final AtomicReference<Disposable> utteranceSub = new AtomicReference<>();
    private final AtomicReference<Flux<AudioFrame>> sharedMic = new AtomicReference<>();

    public VoicePipeline(
            AudioCapture capture,
            WakeWordDetector wakeDetector,
            SpeechToText stt,
            TextToSpeech tts,
            ChatService chat,
            MemoryStore memory,
            ConversationState conversation,
            AssistantStateMachine fsm,
            EventBus bus,
            PlaybackService playback,
            AudioLevelMeter levelMeter,
            @Value("${alfred.voice.silence-threshold:0.012}") double silenceThreshold,
            @Value("${alfred.voice.silence-millis:900}") long silenceMillis,
            @Value("${alfred.voice.max-utterance-millis:15000}") long maxUtteranceMillis) {
        this.capture = capture;
        this.wakeDetector = wakeDetector;
        this.stt = stt;
        this.tts = tts;
        this.chat = chat;
        this.memory = memory;
        this.conversation = conversation;
        this.fsm = fsm;
        this.bus = bus;
        this.playback = playback;
        this.levelMeter = levelMeter;
        this.silenceThreshold = silenceThreshold;
        this.silenceMillis = silenceMillis;
        this.maxUtteranceMillis = maxUtteranceMillis;
    }

    public AudioLevelMeter levelMeter() { return levelMeter; }

    /** Start the always-on mic + wake detection. Idempotent. */
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        log.info("Starting voice pipeline");

        Flux<AudioFrame> mic = capture.start()
                .publish()
                .refCount(1, Duration.ofSeconds(2))
                .doOnError(e -> {
                    log.error("Mic capture failed", e);
                    bus.publish(new AssistantEvent.Error("microphone", e));
                    fsm.request(AssistantState.ERROR);
                });
        sharedMic.set(mic);

        // 1) level meter (always)
        levelSub.set(mic.subscribe(levelMeter::accept,
                e -> log.warn("Level meter error", e)));

        // 2) wake word (always)
        wakeSub.set(wakeDetector.listen(mic)
                .subscribe(this::onWakeEvent,
                        e -> log.warn("Wake detector error", e)));
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        log.info("Stopping voice pipeline");
        disposeSafe(wakeSub.getAndSet(null));
        disposeSafe(levelSub.getAndSet(null));
        disposeSafe(utteranceSub.getAndSet(null));
        capture.stop();
        playback.stop();
    }

    /** Manual wake (e.g. SPACE key). */
    public void requestWake(String source) {
        log.info("Wake requested via {}", source);
        bus.publish(new AssistantEvent.WakeRequested(source));
        beginWaking();
    }

    /** Manual sleep (e.g. ESC key). */
    public void requestSleep(String source) {
        log.info("Sleep requested via {}", source);
        bus.publish(new AssistantEvent.SleepRequested(source));
        playback.stop();
        disposeSafe(utteranceSub.getAndSet(null));
        fsm.request(AssistantState.SLEEP);
    }

    /** Send a typed message bypassing STT (keyboard fallback). */
    public void sendTypedMessage(String text) {
        if (text == null || text.isBlank()) return;
        if (fsm.current() == AssistantState.SLEEP) {
            // Wake up first.
            beginWaking();
        }
        handleUserUtterance(text.trim());
    }

    // -------------------------------------------------------------------
    // Internal transitions
    // -------------------------------------------------------------------

    private void onWakeEvent(WakeWordDetector.WakeEvent event) {
        if (fsm.current() != AssistantState.SLEEP) return;
        log.info("Wake word detected: {} (conf={})", event.phrase(), event.confidence());
        bus.publish(new AssistantEvent.WakeRequested("wake-word"));
        beginWaking();
    }

    private void beginWaking() {
        if (fsm.current() != AssistantState.SLEEP) return;
        fsm.request(AssistantState.WAKING);
        // The HUD plays the boot animation; it calls back via VoicePipeline#onBootComplete.
    }

    /** Called by the UI when the boot animation finishes. */
    public void onBootComplete() {
        fsm.request(AssistantState.LISTENING);
        beginListening();
    }

    private void beginListening() {
        disposeSafe(utteranceSub.getAndSet(null));
        Flux<AudioFrame> mic = sharedMic.get();
        if (mic == null) {
            log.warn("Cannot listen - mic not started");
            return;
        }
        long startNanos = System.nanoTime();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        AtomicReference<AudioFormatSpec> spec = new AtomicReference<>(AudioFormatSpec.WHISPER_DEFAULT);
        AtomicReference<Long> lastSpeechNanos = new AtomicReference<>(System.nanoTime());

        Disposable sub = mic
                .takeWhile(frame -> {
                    long elapsed = (System.nanoTime() - startNanos) / 1_000_000L;
                    if (elapsed > maxUtteranceMillis) return false;
                    double level = levelMeter.levelProperty().get();
                    if (level > silenceThreshold) {
                        lastSpeechNanos.set(System.nanoTime());
                    }
                    long sinceSpeech = (System.nanoTime() - lastSpeechNanos.get()) / 1_000_000L;
                    return sinceSpeech < silenceMillis;
                })
                .doOnNext(frame -> {
                    spec.set(frame.format());
                    try {
                        buffer.write(frame.samples());
                    } catch (IOException ignored) {
                        // ByteArrayOutputStream never throws.
                    }
                })
                .doOnComplete(() -> {
                    byte[] pcm = buffer.toByteArray();
                    if (pcm.length < spec.get().sampleRateHz() / 4) {
                        // <250ms - probably noise, return to listening.
                        log.debug("Utterance too short ({} bytes), re-listening", pcm.length);
                        beginListening();
                        return;
                    }
                    transcribeAndRespond(pcm, spec.get());
                })
                .subscribe(f -> {}, e -> {
                    log.error("Utterance buffer error", e);
                    fsm.request(AssistantState.LISTENING);
                });
        utteranceSub.set(sub);
    }

    private void transcribeAndRespond(byte[] pcm, AudioFormatSpec spec) {
        fsm.request(AssistantState.THINKING);
        stt.transcribe(pcm, spec)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        this::onTranscript,
                        e -> {
                            log.error("STT failed", e);
                            bus.publish(new AssistantEvent.Error("stt", e));
                            fsm.request(AssistantState.LISTENING);
                            beginListening();
                        });
    }

    private void onTranscript(Transcript transcript) {
        String text = transcript.text() == null ? "" : transcript.text().trim();
        if (text.isBlank()) {
            log.debug("Empty transcript, returning to LISTENING");
            fsm.request(AssistantState.LISTENING);
            beginListening();
            return;
        }
        log.info("USER: {}", text);
        // Recognize sleep command.
        if (text.toLowerCase().matches(".*\\b(go to sleep|shut down|stand down)\\b.*")) {
            requestSleep("voice");
            return;
        }
        handleUserUtterance(text);
    }

    private void handleUserUtterance(String text) {
        bus.publish(new AssistantEvent.UserUtterance(text));

        UUID convId = conversation.conversationId();
        Message userMsg = Message.user(text);
        memory.save(new MemoryEntry(convId, userMsg, java.time.Instant.now()));

        fsm.request(AssistantState.THINKING);

        StringBuilder full = new StringBuilder();
        ChatRequest req = ChatRequest.of(List.of(userMsg));
        chat.stream(req)
                .doOnNext(token -> {
                    full.append(token);
                    if (fsm.current() == AssistantState.THINKING) {
                        // First token arrived - hand the user to SPEAKING (handled below post-TTS).
                        // We keep THINKING here; SPEAKING starts when TTS playback begins.
                    }
                    bus.publish(new AssistantEvent.AssistantToken(token));
                })
                .doOnComplete(() -> {
                    String response = full.toString();
                    log.info("ALFRED: {}", response);
                    memory.save(new MemoryEntry(convId,
                            Message.assistant(response),
                            java.time.Instant.now()));
                    bus.publish(new AssistantEvent.AssistantResponseComplete(response));
                    speakAndContinue(response);
                })
                .doOnError(e -> {
                    log.error("Chat error", e);
                    bus.publish(new AssistantEvent.Error("chat", e));
                    fsm.request(AssistantState.LISTENING);
                    beginListening();
                })
                .subscribe();
    }

    private void speakAndContinue(String text) {
        fsm.request(AssistantState.SPEAKING);
        tts.synthesize(text)
                .flatMap(playback::play)
                .doFinally(sig -> {
                    if (fsm.current() != AssistantState.SLEEP) {
                        fsm.request(AssistantState.LISTENING);
                        beginListening();
                    }
                })
                .subscribe(v -> {}, e -> {
                    log.error("TTS playback failed", e);
                    bus.publish(new AssistantEvent.Error("tts", e));
                });
    }

    private static void disposeSafe(Disposable d) {
        if (d != null && !d.isDisposed()) {
            try { d.dispose(); } catch (Exception ignored) {}
        }
    }
}

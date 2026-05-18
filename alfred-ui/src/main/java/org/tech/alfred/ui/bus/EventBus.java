package org.tech.alfred.ui.bus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * In-process pub/sub for {@link AssistantEvent}s. Backed by a Reactor
 * {@link Sinks.Many#multicast() multicast sink} so multiple subscribers
 * (HUD components, transcript panel, logging) can fan out from a single
 * producer.
 *
 * <p>{@code onBackpressureBuffer} is intentional: HUD subscribers run on
 * the JavaFX thread and may briefly pause; we don't want a slow frame to
 * drop user tokens. The buffer is large enough for several seconds of
 * streaming text.
 */
@Component
public class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private final Sinks.Many<AssistantEvent> sink =
            Sinks.many().multicast().onBackpressureBuffer(1024, false);

    public void publish(AssistantEvent event) {
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            log.warn("EventBus drop {} -> {}", event.getClass().getSimpleName(), result);
        }
    }

    public Flux<AssistantEvent> stream() {
        return sink.asFlux();
    }

    /**
     * Convenience for components only interested in a single event type.
     */
    public <E extends AssistantEvent> Flux<E> stream(Class<E> type) {
        return sink.asFlux().ofType(type);
    }
}

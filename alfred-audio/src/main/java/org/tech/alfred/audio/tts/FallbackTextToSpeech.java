package org.tech.alfred.audio.tts;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.tech.alfred.core.audio.AudioFrame;
import org.tech.alfred.core.audio.TextToSpeech;

/**
 * Tries a list of {@link TextToSpeech} delegates in order; the first
 * one that returns non-empty WAV bytes wins. Lets us prefer Piper (neural,
 * British butler) when it's installed, and fall through to the Windows
 * built-in SAPI engine when it isn't, so Alfred always has a voice.
 */
public class FallbackTextToSpeech implements TextToSpeech {

    private static final Logger log = LoggerFactory.getLogger(FallbackTextToSpeech.class);

    private final List<TextToSpeech> delegates;

    public FallbackTextToSpeech(List<TextToSpeech> delegates) {
        this.delegates = List.copyOf(delegates);
        log.info("TTS chain: {}",
                delegates.stream().map(d -> d.getClass().getSimpleName()).toList());
    }

    @Override
    public Mono<byte[]> synthesize(String text) {
        return tryNext(text, 0);
    }

    private Mono<byte[]> tryNext(String text, int idx) {
        if (idx >= delegates.size()) {
            return Mono.just(new byte[0]);
        }
        TextToSpeech d = delegates.get(idx);
        return d.synthesize(text)
                .flatMap(bytes -> bytes.length > 0
                        ? Mono.just(bytes)
                        : tryNext(text, idx + 1))
                .onErrorResume(err -> {
                    log.warn("TTS delegate {} failed ({}); trying next.",
                            d.getClass().getSimpleName(), err.getMessage());
                    return tryNext(text, idx + 1);
                });
    }

    @Override
    public Flux<AudioFrame> stream(String text) {
        return Flux.empty();
    }
}

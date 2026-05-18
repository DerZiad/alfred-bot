package org.tech.alfred.audio.tts;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.tech.alfred.audio.config.AlfredAudioProperties;
import org.tech.alfred.core.audio.AudioFrame;
import org.tech.alfred.core.audio.TextToSpeech;

/**
 * {@link TextToSpeech} backed by <a href="https://github.com/rhasspy/piper">Piper</a>.
 *
 * <p>We invoke the piper binary in stdin → stdout WAV mode:
 *
 * <pre>{@code
 *   echo "<text>" | piper --model voice.onnx --output_file -
 * }</pre>
 *
 * <p>Piper writes a complete WAV header plus audio to stdout, which we
 * read into a byte array. The byte array is then handed to
 * {@code PlaybackService}, which streams it through
 * {@code javax.sound.sampled}.
 *
 * <p>Process I/O happens on {@code boundedElastic} to keep the reactor
 * scheduler free.
 */
public class PiperTextToSpeech implements TextToSpeech {

    private static final Logger log = LoggerFactory.getLogger(PiperTextToSpeech.class);

    private final AlfredAudioProperties props;

    public PiperTextToSpeech(AlfredAudioProperties props) {
        this.props = props;
    }

    @Override
    public Mono<byte[]> synthesize(String text) {
        if (text == null || text.isBlank()) return Mono.just(new byte[0]);
        return Mono.fromCallable(() -> doSynth(text))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private byte[] doSynth(String text) throws IOException, InterruptedException {
        Path tmp = Files.createTempFile("alfred-tts-", ".wav");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    props.piperBinary(),
                    "--model", props.piperVoice(),
                    "--output_file", tmp.toAbsolutePath().toString())
                    .redirectErrorStream(true);
            Process proc = pb.start();
            try (OutputStream stdin = proc.getOutputStream()) {
                stdin.write(text.getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            }
            // Drain stdout/stderr to prevent the child from blocking on a full pipe.
            try (InputStream stdout = proc.getInputStream()) {
                byte[] chunk = new byte[8192];
                while (stdout.read(chunk) != -1) { /* discard logs */ }
            }
            if (!proc.waitFor(30, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                throw new IOException("piper timed out");
            }
            int code = proc.exitValue();
            if (code != 0) {
                throw new IOException("piper exited " + code);
            }
            byte[] wav = Files.readAllBytes(tmp);
            log.debug("piper synthesized {} bytes for {} chars", wav.length, text.length());
            return wav;
        } finally {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    @Override
    public Flux<AudioFrame> stream(String text) {
        // Frame-streaming is not used by the current pipeline (PlaybackService
        // plays the whole WAV via a SourceDataLine). Implementing it would
        // chunk Piper's stdout while it's still being written.
        return Flux.empty();
    }
}

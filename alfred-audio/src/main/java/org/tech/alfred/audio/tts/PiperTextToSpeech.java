package org.tech.alfred.audio.tts;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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
 * <p>We invoke the piper binary with {@code --model voice.onnx --output_file <tmp>.wav}
 * and feed the text on stdin. Piper writes a WAV file we then load into memory.
 *
 * <p>The binary path from {@code alfred.audio.piper-binary} is resolved
 * forgivingly: if it doesn't exist, we walk a handful of common install
 * locations (zips often nest an extra folder when extracted) and pick
 * the first {@code piper.exe} / {@code piper} we find. The resolved
 * path is logged once for transparency.
 */
public class PiperTextToSpeech implements TextToSpeech {

    private static final Logger log = LoggerFactory.getLogger(PiperTextToSpeech.class);

    private final AlfredAudioProperties props;
    private volatile String resolvedBinary;
    private volatile boolean resolveFailed;

    public PiperTextToSpeech(AlfredAudioProperties props) {
        this.props = props;
        // Try once at startup so the log line is visible in boot output.
        this.resolvedBinary = resolveBinary(props.piperBinary());
        if (resolvedBinary != null) {
            log.info(">>> PIPER IN USE: {}", resolvedBinary);
        } else {
            log.warn("Could not locate piper.exe. TTS will fail. Set "
                    + "alfred.audio.piper-binary to the full path of your "
                    + "piper executable, or drop the binary at one of: {}",
                    candidatePaths(props.piperBinary()));
            resolveFailed = true;
        }
    }

    @Override
    public Mono<byte[]> synthesize(String text) {
        if (text == null || text.isBlank()) return Mono.just(new byte[0]);
        return Mono.fromCallable(() -> doSynth(text))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private byte[] doSynth(String text) throws IOException, InterruptedException {
        if (resolvedBinary == null) {
            // One more attempt — user might have just installed it.
            resolvedBinary = resolveBinary(props.piperBinary());
            if (resolvedBinary == null) {
                throw new IOException("piper binary not found. Tried: "
                        + candidatePaths(props.piperBinary()));
            }
            log.info(">>> PIPER IN USE: {} (resolved on first call)", resolvedBinary);
            resolveFailed = false;
        }
        Path tmp = Files.createTempFile("alfred-tts-", ".wav");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    resolvedBinary,
                    "--model", props.piperVoice(),
                    "--output_file", tmp.toAbsolutePath().toString())
                    .redirectErrorStream(true);
            Process proc = pb.start();
            try (OutputStream stdin = proc.getOutputStream()) {
                stdin.write(text.getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            }
            // Drain stdout/stderr to prevent the child from blocking on a full pipe.
            StringBuilder captured = new StringBuilder();
            try (InputStream stdout = proc.getInputStream()) {
                byte[] chunk = new byte[8192];
                int read;
                while ((read = stdout.read(chunk)) != -1) {
                    if (captured.length() < 8000) {
                        captured.append(new String(chunk, 0, read, StandardCharsets.UTF_8));
                    }
                }
            }
            if (!proc.waitFor(30, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                throw new IOException("piper timed out");
            }
            int code = proc.exitValue();
            if (code != 0) {
                log.error("piper exited {}.\nVoice model: {}\nCaptured output:\n{}",
                        code, props.piperVoice(), captured);
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
        // Frame-streaming not used by the current pipeline.
        return Flux.empty();
    }

    // ----------------------------------------------------------------------
    // Binary resolution
    // ----------------------------------------------------------------------

    /**
     * Returns the first existing candidate from the configured path and a
     * handful of common install locations. {@code null} if none exist.
     */
    private static String resolveBinary(String configured) {
        for (String candidate : candidatePaths(configured)) {
            Path p = Paths.get(candidate);
            if (Files.isRegularFile(p) && Files.isExecutable(p)) {
                return p.toAbsolutePath().toString();
            }
        }
        return null;
    }

    private static List<String> candidatePaths(String configured) {
        List<String> list = new ArrayList<>();
        if (configured != null && !configured.isBlank()) {
            list.add(configured);
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String exe = windows ? "piper.exe" : "piper";
        // Common Windows install nests
        list.add("C:/Tools/piper/" + exe);
        list.add("C:/Tools/piper/piper/" + exe);
        list.add("C:/Tools/piper_windows_amd64/" + exe);
        list.add("C:/Program Files/piper/" + exe);
        // *nix
        list.add("/usr/local/bin/piper");
        list.add("/usr/bin/piper");
        list.add("/opt/piper/piper");
        // Per-user
        String home = System.getProperty("user.home", "");
        if (!home.isBlank()) {
            list.add(home.replace('\\', '/') + "/.alfred/piper/" + exe);
            list.add(home.replace('\\', '/') + "/piper/" + exe);
        }
        return list;
    }
}

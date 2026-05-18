package org.tech.alfred.audio.stt;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.tech.alfred.audio.config.AlfredAudioProperties;
import org.tech.alfred.core.audio.AudioFormatSpec;
import org.tech.alfred.core.audio.AudioFrame;
import org.tech.alfred.core.audio.SpeechToText;
import org.tech.alfred.core.audio.Transcript;

/**
 * {@link SpeechToText} backed by whisper.cpp. The implementation execs
 * the {@code whisper-cli} binary against a temp WAV file:
 *
 * <pre>{@code
 *   whisper-cli -m <model.bin> -f <utterance.wav> --no-timestamps --output-txt
 * }</pre>
 *
 * <p>The PCM buffer is wrapped in a RIFF/WAV header before being written
 * (whisper.cpp can't read raw PCM). All process I/O runs on the
 * {@code boundedElastic} scheduler so it never pins the reactor loop.
 *
 * <p>For pure streaming partial transcripts we'd switch to whisper-jni
 * or whisper.cpp's streaming mode - left as a future enhancement; the
 * VoicePipeline currently uses VAD-gated buffering, then a single
 * {@link #transcribe(byte[], AudioFormatSpec)} call.
 */
public class WhisperSpeechToText implements SpeechToText {

    private static final Logger log = LoggerFactory.getLogger(WhisperSpeechToText.class);
    private static final Pattern CLEAN = Pattern.compile("\\[[^\\]]*\\]");

    private final AlfredAudioProperties props;

    public WhisperSpeechToText(AlfredAudioProperties props) {
        this.props = props;
    }

    @Override
    public Mono<Transcript> transcribe(byte[] pcm, AudioFormatSpec format) {
        return Mono.fromCallable(() -> doTranscribe(pcm, format))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Transcript doTranscribe(byte[] pcm, AudioFormatSpec format) throws IOException {
        Path tmpDir = Files.createTempDirectory("alfred-stt-");
        Path wav = tmpDir.resolve("utterance.wav");
        writeWav(wav, pcm, format);
        long started = System.nanoTime();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    props.whisperBinary(),
                    "-m", props.whisperModel(),
                    "-f", wav.toAbsolutePath().toString(),
                    "--no-timestamps",
                    "--no-prints",
                    "-l", "en")
                    .redirectErrorStream(true);
            Process proc = pb.start();
            String out = new String(proc.getInputStream().readAllBytes());
            int code = proc.waitFor();
            log.debug("whisper.cpp exited {} after {}ms", code,
                    (System.nanoTime() - started) / 1_000_000);
            if (code != 0) {
                log.warn("whisper.cpp non-zero exit: {}\n{}", code, out);
                return new Transcript("", 0.0,
                        Duration.ofMillis(durationMillis(pcm, format)));
            }
            String text = cleanup(out);
            return new Transcript(text, 1.0,
                    Duration.ofMillis(durationMillis(pcm, format)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Transcript("", 0.0, Duration.ZERO);
        } finally {
            try { Files.deleteIfExists(wav); Files.deleteIfExists(tmpDir); }
            catch (IOException ignored) {}
        }
    }

    @Override
    public Flux<Transcript> transcribeStream(Flux<AudioFrame> frames) {
        // For now we don't stream partials - VoicePipeline buffers a full
        // utterance and calls transcribe() once. Returning empty keeps
        // the interface contract intact.
        return Flux.empty();
    }

    private static String cleanup(String out) {
        // whisper-cli prints the transcript on its own lines after the
        // dotted-progress block. We take everything after the last
        // blank line, strip [tags], and trim.
        String[] parts = out.split("\\R\\R");
        String tail = parts[parts.length - 1];
        Matcher m = CLEAN.matcher(tail);
        return m.replaceAll("").trim();
    }

    private static long durationMillis(byte[] pcm, AudioFormatSpec f) {
        long samples = pcm.length / (f.bitsPerSample() / 8) / Math.max(1, f.channels());
        return (samples * 1000L) / f.sampleRateHz();
    }

    /** Write raw 16-bit LE PCM as a minimal RIFF/WAVE file. */
    private static void writeWav(Path target, byte[] pcm, AudioFormatSpec f) throws IOException {
        int byteRate = f.sampleRateHz() * f.channels() * f.bitsPerSample() / 8;
        int blockAlign = f.channels() * f.bitsPerSample() / 8;
        int dataLen = pcm.length;
        int riffLen = 36 + dataLen;

        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes());
        header.putInt(riffLen);
        header.put("WAVE".getBytes());
        header.put("fmt ".getBytes());
        header.putInt(16);                       // PCM subchunk size
        header.putShort((short) 1);              // PCM format
        header.putShort((short) f.channels());
        header.putInt(f.sampleRateHz());
        header.putInt(byteRate);
        header.putShort((short) blockAlign);
        header.putShort((short) f.bitsPerSample());
        header.put("data".getBytes());
        header.putInt(dataLen);

        try (OutputStream os = Files.newOutputStream(target)) {
            os.write(header.array());
            os.write(pcm);
        }
    }
}

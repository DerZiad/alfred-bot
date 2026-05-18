package org.tech.alfred.audio.tts;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.tech.alfred.core.audio.AudioFrame;
import org.tech.alfred.core.audio.TextToSpeech;

/**
 * Windows-only {@link TextToSpeech} that drives the built-in
 * <a href="https://learn.microsoft.com/en-us/dotnet/api/system.speech.synthesis">
 * System.Speech.Synthesis</a> SAPI engine via a short PowerShell
 * one-shot. No external binary required — works on every Windows 10/11
 * install out of the box.
 *
 * <p>Auto-selects an {@code en-GB-*} voice when one is installed
 * (typically "Microsoft Hazel Desktop") so Alfred sounds British when
 * possible; otherwise falls back to the system default voice (David /
 * Zira on most US installs).
 *
 * <p>This is intended as a fallback for when Piper isn't available.
 * Piper produces noticeably better neural voices — install it later and
 * the audio quality jumps without code changes.
 */
public class WindowsSapiTextToSpeech implements TextToSpeech {

    private static final Logger log = LoggerFactory.getLogger(WindowsSapiTextToSpeech.class);

    /** PowerShell script template — text and wav path filled in at runtime. */
    private static final String SCRIPT = """
            $ErrorActionPreference = 'Stop'
            Add-Type -AssemblyName System.Speech
            $s = New-Object System.Speech.Synthesis.SpeechSynthesizer
            try {
                $gb = $s.GetInstalledVoices() | Where-Object {
                    $_.Enabled -and $_.VoiceInfo.Culture.Name -like 'en-GB*'
                } | Select-Object -First 1
                if ($gb) { $s.SelectVoice($gb.VoiceInfo.Name) }
            } catch {}
            $s.Rate = 0
            $text = Get-Content -Raw -Encoding UTF8 -Path '%s'
            $s.SetOutputToWaveFile('%s')
            $s.Speak($text)
            $s.Dispose()
            """;

    public static boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    @Override
    public Mono<byte[]> synthesize(String text) {
        if (text == null || text.isBlank()) return Mono.just(new byte[0]);
        return Mono.fromCallable(() -> doSynth(text))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private byte[] doSynth(String text) throws IOException, InterruptedException {
        Path tmpDir = Files.createTempDirectory("alfred-sapi-");
        Path txt = tmpDir.resolve("text.txt");
        Path wav = tmpDir.resolve("out.wav");
        Path ps1 = tmpDir.resolve("speak.ps1");

        Files.writeString(txt, text, StandardCharsets.UTF_8);
        String script = String.format(SCRIPT,
                txt.toAbsolutePath().toString().replace("\\", "\\\\"),
                wav.toAbsolutePath().toString().replace("\\", "\\\\"));
        try (Writer w = new OutputStreamWriter(
                Files.newOutputStream(ps1), StandardCharsets.UTF_8)) {
            w.write(script);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-File", ps1.toAbsolutePath().toString())
                    .redirectErrorStream(true);
            Process proc = pb.start();
            byte[] captured = proc.getInputStream().readAllBytes();
            if (!proc.waitFor(30, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                throw new IOException("SAPI TTS timed out");
            }
            int code = proc.exitValue();
            if (code != 0) {
                throw new IOException("SAPI TTS exited " + code + ":\n"
                        + new String(captured, StandardCharsets.UTF_8));
            }
            byte[] wavBytes = Files.readAllBytes(wav);
            log.debug("SAPI synthesized {} bytes for {} chars", wavBytes.length, text.length());
            return wavBytes;
        } finally {
            try { Files.deleteIfExists(txt); } catch (IOException ignored) {}
            try { Files.deleteIfExists(wav); } catch (IOException ignored) {}
            try { Files.deleteIfExists(ps1); } catch (IOException ignored) {}
            try { Files.deleteIfExists(tmpDir); } catch (IOException ignored) {}
        }
    }

    @Override
    public Flux<AudioFrame> stream(String text) {
        return Flux.empty();
    }
}

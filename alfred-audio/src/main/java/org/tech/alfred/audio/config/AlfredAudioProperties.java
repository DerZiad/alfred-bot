package org.tech.alfred.audio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code alfred.audio.*} configuration.
 *
 * @param whisperBinary       Path to the whisper.cpp `main` binary (or `whisper-cli`).
 * @param whisperModel        Path to the .bin Whisper model file (e.g. ggml-base.en.bin).
 * @param piperBinary         Path to the Piper TTS binary.
 * @param piperVoice          Path to a Piper voice .onnx file.
 * @param wakeWord            Display label for the wake word (informational).
 * @param porcupineAccessKey  Picovoice access key (https://console.picovoice.ai).
 *                            Falls back to env var {@code PORCUPINE_ACCESS_KEY} if blank.
 * @param wakeKeywordPath     Path to a Porcupine .ppn file (built-in or custom).
 *                            REQUIRED to enable Porcupine; otherwise we fall back
 *                            to the whisper-based detector.
 * @param wakeSensitivity     Detection sensitivity 0..1 (higher = more false positives).
 * @param inputDevice         Optional case-insensitive substring of the mic mixer name
 *                            to prefer (e.g. "Headset", "USB"). Blank = system default.
 * @param outputDevice        Optional case-insensitive substring of the speaker mixer
 *                            name to prefer for TTS playback. Blank = system default.
 */
@ConfigurationProperties(prefix = "alfred.audio")
public record AlfredAudioProperties(
        String whisperBinary,
        String whisperModel,
        String piperBinary,
        String piperVoice,
        String wakeWord,
        String porcupineAccessKey,
        String wakeKeywordPath,
        Float wakeSensitivity,
        String inputDevice,
        String outputDevice
) {
    public AlfredAudioProperties {
        if (wakeWord == null || wakeWord.isBlank()) wakeWord = "jarvis";
        if (wakeSensitivity == null) wakeSensitivity = 0.6f;
    }

    /** Resolve the access key, falling back to environment if config is blank. */
    public String resolvePorcupineAccessKey() {
        if (porcupineAccessKey != null && !porcupineAccessKey.isBlank()) {
            return porcupineAccessKey;
        }
        String env = System.getenv("PORCUPINE_ACCESS_KEY");
        return env == null ? "" : env;
    }

    /** True iff both access key and keyword-path are configured. */
    public boolean isPorcupineReady() {
        String key = resolvePorcupineAccessKey();
        return key != null && !key.isBlank()
                && wakeKeywordPath != null && !wakeKeywordPath.isBlank();
    }
}

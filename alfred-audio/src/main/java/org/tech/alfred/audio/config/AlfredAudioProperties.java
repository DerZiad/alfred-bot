package org.tech.alfred.audio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code alfred.audio.*} configuration.
 *
 * @param whisperBinary  Path to the whisper.cpp `main` binary (or `whisper-cli`).
 * @param whisperModel   Path to the .bin Whisper model file (e.g. ggml-base.en.bin).
 * @param piperBinary    Path to the Piper TTS binary.
 * @param piperVoice     Path to a Piper voice .onnx file.
 * @param wakeWord       Phrase that activates the assistant.
 */
@ConfigurationProperties(prefix = "alfred.audio")
public record AlfredAudioProperties(
        String whisperBinary,
        String whisperModel,
        String piperBinary,
        String piperVoice,
        String wakeWord
) {
    public AlfredAudioProperties {
        if (wakeWord == null || wakeWord.isBlank()) {
            wakeWord = "hey alfred";
        }
    }
}

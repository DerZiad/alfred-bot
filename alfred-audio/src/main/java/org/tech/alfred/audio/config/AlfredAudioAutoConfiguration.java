package org.tech.alfred.audio.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

import org.tech.alfred.audio.capture.MicrophoneCapture;
import org.tech.alfred.audio.stt.WhisperSpeechToText;
import org.tech.alfred.audio.tts.FallbackTextToSpeech;
import org.tech.alfred.audio.tts.PiperTextToSpeech;
import org.tech.alfred.audio.tts.WindowsSapiTextToSpeech;
import org.tech.alfred.audio.wake.PorcupineWakeWordDetector;
import org.tech.alfred.audio.wake.SimpleWakeWordDetector;
import org.tech.alfred.core.audio.AudioCapture;
import org.tech.alfred.core.audio.SpeechToText;
import org.tech.alfred.core.audio.TextToSpeech;
import org.tech.alfred.core.wake.WakeWordDetector;

/**
 * Auto-configures the audio adapters: mic capture, whisper.cpp STT,
 * Piper TTS, and Porcupine wake-word.
 *
 * <p>Porcupine activates only when BOTH a Picovoice access key and a
 * keyword {@code .ppn} path are configured. Without either, we fall
 * back to the legacy {@link SimpleWakeWordDetector} (Whisper-based)
 * so the app still boots in development.
 */
@AutoConfiguration
@EnableConfigurationProperties(AlfredAudioProperties.class)
public class AlfredAudioAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AlfredAudioAutoConfiguration.class);

    @Bean
    public AudioCapture audioCapture(AlfredAudioProperties props) {
        return new MicrophoneCapture(props.inputDevice());
    }

    @Bean
    public SpeechToText speechToText(AlfredAudioProperties props) {
        return new WhisperSpeechToText(props);
    }

    @Bean
    public TextToSpeech textToSpeech(AlfredAudioProperties props) {
        // Try Piper first (high-quality neural British butler).
        // Fall back to the built-in Windows SAPI engine so Alfred has a
        // voice even before Piper is installed.
        List<TextToSpeech> chain = new ArrayList<>();
        chain.add(new PiperTextToSpeech(props));
        if (WindowsSapiTextToSpeech.isSupported()) {
            chain.add(new WindowsSapiTextToSpeech());
            log.info("Windows detected — SAPI TTS available as fallback for Piper.");
        }
        return new FallbackTextToSpeech(chain);
    }

    @Bean
    public WakeWordDetector wakeWordDetector(SpeechToText stt, AlfredAudioProperties props) {
        if (!props.isPorcupineReady()) {
            log.warn("Porcupine not fully configured (need both "
                    + "alfred.audio.porcupine-access-key and "
                    + "alfred.audio.wake-keyword-path). Falling back to "
                    + "whisper-based wake-word detection.");
            return new SimpleWakeWordDetector(stt, props.wakeWord());
        }
        log.info("Wake-word detector: Porcupine (label='{}', keyword-path={})",
                props.wakeWord(), props.wakeKeywordPath());
        return new PorcupineWakeWordDetector(
                props.resolvePorcupineAccessKey(),
                props.wakeKeywordPath(),
                props.wakeWord(),
                props.wakeSensitivity());
    }
}

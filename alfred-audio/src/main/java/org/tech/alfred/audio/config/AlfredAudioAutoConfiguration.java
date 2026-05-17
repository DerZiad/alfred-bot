package org.tech.alfred.audio.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import org.tech.alfred.audio.capture.MicrophoneCapture;
import org.tech.alfred.audio.stt.WhisperSpeechToText;
import org.tech.alfred.audio.tts.PiperTextToSpeech;
import org.tech.alfred.audio.wake.SimpleWakeWordDetector;
import org.tech.alfred.core.audio.AudioCapture;
import org.tech.alfred.core.audio.SpeechToText;
import org.tech.alfred.core.audio.TextToSpeech;
import org.tech.alfred.core.wake.WakeWordDetector;

@AutoConfiguration
@EnableConfigurationProperties(AlfredAudioProperties.class)
public class AlfredAudioAutoConfiguration {

    @Bean
    public AudioCapture audioCapture() {
        return new MicrophoneCapture();
    }

    @Bean
    public SpeechToText speechToText(AlfredAudioProperties props) {
        return new WhisperSpeechToText(props);
    }

    @Bean
    public TextToSpeech textToSpeech(AlfredAudioProperties props) {
        return new PiperTextToSpeech(props);
    }

    @Bean
    public WakeWordDetector wakeWordDetector(SpeechToText stt, AlfredAudioProperties props) {
        return new SimpleWakeWordDetector(stt, props.wakeWord());
    }
}

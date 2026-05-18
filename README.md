# ALFRED

A cinematic, local-first desktop AI assistant. Java 21, Spring Boot 4, Spring AI, Ollama, JavaFX — wrapped in a JARVIS-style HUD: pulsing AI core, rotating HUD rings, live waveform, holographic side panels, and a British-butler personality voice.

## What it feels like

- Idle: low-energy cyan glow, ambient particles, "SLEEPING".
- Say **"Jarvis"** (or press **SPACE**): rings expand, boot lines stream, HUD assembles, status flips to **LISTENING**.
- Speak: the waveform breathes with your voice, the AI core pulses with the mic level.
- Pause: VAD detects silence, status flips to **THINKING**, rings accelerate, transcript shows the ellipsis.
- Alfred replies: status flips to **SPEAKING**, the British voice plays, the spectrum bars dance to the audio.
- Say "go to sleep" or press **ESC** to return to standby.

## Why local-first?

Latency, privacy, offline capability. Alfred talks to a model running on your machine via [Ollama](https://ollama.com), transcribes speech with [whisper.cpp](https://github.com/ggerganov/whisper.cpp), speaks back with [Piper](https://github.com/rhasspy/piper), and detects wake words with [Porcupine](https://picovoice.ai/platform/porcupine/). Nothing leaves the device by default.

## Architecture at a glance

```
+--------------------------------------------------------------+
|                          alfred-ui                           |   <- JavaFX + executable
|                                                              |
|  state/      AssistantState  +  AssistantStateMachine        |
|  bus/        EventBus  +  AssistantEvent (sealed)            |
|  hud/        AlfredCore  HudRings  Waveform  SpectrumBars    |
|              ParticleField  HolographicPanel  TranscriptPanel|
|              BootSequence  HudView                           |
|  voice/      VoicePipeline  AudioLevelMeter  PlaybackService |
|  animation/  PulseAnimation  RingRotator                     |
|  view/       MainController (slim glue)                      |
+--------------------------------------------------------------+
        |             |              |          |
        v             v              v          v
   alfred-ai     alfred-audio   alfred-memory  alfred-tools
   (Spring AI    (Porcupine,    (SQLite store, (Registry +
   + Ollama)     whisper.cpp,    pgvector      built-in
                 Piper, mic)     later)        tools)
        \             \              /          /
         \             \            /          /
          ----->     alfred-core    <----------
                  (domain + SPIs only)
```

`alfred-core` knows nothing about Spring, JavaFX, or any vendor. Every concrete provider is an adapter behind an SPI — swap models, swap STT engines, swap memory backends without touching the rest.

### Assistant state machine

```
            wake word / SPACE / button
   SLEEP ─────────────────────────────► WAKING (1.2s boot anim)
     ▲                                    │
     │ ESC / "go to sleep"                ▼
     │                                LISTENING ◄────────┐
     │                                    │              │
     │                          VAD silence detected     │
     │                                    ▼              │
     │                                THINKING           │
     │                                    │              │
     │                          first LLM token          │
     │                                    ▼              │
     │                                SPEAKING ──────────┘
     │                                                playback done
     └─────────── any state ◄── ERROR ──── auto-reset after 3s
```

## Prerequisites

| Need          | Get it                                                                         |
| ------------- | ------------------------------------------------------------------------------ |
| JDK 21+       | Adoptium, BellSoft, Oracle, etc.                                              |
| Maven 3.9+    | https://maven.apache.org/download.cgi                                          |
| Ollama        | https://ollama.com/download — then `ollama pull llama3.1`                      |
| whisper.cpp   | Build from source, drop `whisper-cli` somewhere + a `ggml-*.bin` model         |
| Piper         | https://github.com/rhasspy/piper/releases + a voice `.onnx`                    |
| Porcupine key | Free non-commercial: https://console.picovoice.ai/                             |

### Recommended Piper voice for the British butler

```
en_GB-alan-medium      (medium quality, ~60MB)
```
Download both `.onnx` and `.onnx.json` from https://github.com/rhasspy/piper/blob/master/VOICES.md and drop them in `~/.alfred/voices/`.

### Wake word — Porcupine setup

Porcupine 3.x always loads a keyword from a `.ppn` file on disk. Two ways to get one:

**Option A — built-in "Jarvis" (recommended).**
Download the `.ppn` for your platform from
https://github.com/Picovoice/porcupine/tree/master/resources/keyword_files
(use `jarvis_windows.ppn`, `jarvis_mac.ppn`, or `jarvis_linux.ppn`).

**Option B — custom "Alfred" or any phrase.**
1. Visit https://console.picovoice.ai/
2. Train a custom keyword for your platform
3. Download the resulting `.ppn` file

Then point `alfred.audio.wake-keyword-path` at it.

**Without a .ppn file**, Alfred falls back to a whisper-based detector that listens for the literal `wake-word` phrase — works but is heavier on CPU.

## Configure

Edit `alfred-ui/src/main/resources/application.yml` (paths are the only required edits):

```yaml
alfred:
  audio:
    whisper-binary: /usr/local/bin/whisper-cli
    whisper-model:  ${user.home}/.alfred/models/ggml-base.en.bin
    piper-binary:   /usr/local/bin/piper
    piper-voice:    ${user.home}/.alfred/voices/en_GB-alan-medium.onnx
    porcupine-access-key: ${PORCUPINE_ACCESS_KEY:}     # or paste key here
    wake-word: "jarvis"                                # display label
    wake-keyword-path:                                 # optional: path/to/alfred.ppn
```

You can keep the access key out of source by setting an env var:

```bash
export PORCUPINE_ACCESS_KEY=xxxxxxxxxxxx
```

## Build & run

```bash
# from the repo root
mvn -q install                              # build all modules
mvn -q -pl alfred-ui spring-boot:run        # run the desktop app
# or:
mvn -q -pl alfred-ui javafx:run             # via the JavaFX plugin
```

Default LLM is `llama3.1`. Override:

```bash
mvn -q -pl alfred-ui spring-boot:run -Dspring-boot.run.arguments=--alfred.ai.default-model=qwen2.5
```

## Keyboard shortcuts

| Key   | Action                |
| ----- | --------------------- |
| SPACE | Wake Alfred           |
| ESC   | Put Alfred to sleep   |

## Module reference

| Module          | Responsibility                                                                                                     |
| --------------- | ------------------------------------------------------------------------------------------------------------------ |
| `alfred-core`   | Domain records + SPIs (`ChatService`, `SpeechToText`, `TextToSpeech`, `WakeWordDetector`, `MemoryStore`, …)        |
| `alfred-ai`     | Spring AI + Ollama implementation of `ChatService`.                                                                |
| `alfred-audio`  | Mic capture (javax.sound), `WhisperSpeechToText` (whisper.cpp), `PiperTextToSpeech`, `PorcupineWakeWordDetector`.  |
| `alfred-memory` | SQLite-backed `MemoryStore` (vector recall later).                                                                 |
| `alfred-tools`  | Tool/function-calling registry + built-ins.                                                                        |
| `alfred-ui`     | JavaFX HUD, state machine, event bus, voice pipeline orchestrator. Executable module.                              |

## Design principles

1. **Local first**: anything that can run on-device should.
2. **SPI everywhere**: cross-module communication only through `alfred-core` interfaces.
3. **Reactive streams end-to-end**: tokens flow from Ollama → `ChatService` → `VoicePipeline` → `TranscriptPanel` as `Flux<String>`; the UI never blocks waiting for a full response.
4. **State as a property**: `AssistantStateMachine` exposes a JavaFX `ObjectProperty` — HUD components observe and re-skin themselves reactively without polling.
5. **Constructor injection only**. No field `@Autowired`. No `@Value` scattered through services.
6. **Records for value types**. Immutable, equals/hashCode for free.
7. **Java 21 virtual threads** for any blocking work (`spring.threads.virtual.enabled=true`).
8. **Auto-configuration over component scan** for cross-module wiring.

See `ROADMAP.md` for what comes next.

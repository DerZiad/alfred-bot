# Alfred development roadmap

Phased plan. Each phase is shippable on its own.

## Phase 0 - Foundations (this commit)

- Multi-module Maven layout
- Provider-neutral SPIs in `alfred-core`
- Ollama-backed `ChatService` (Spring AI)
- JavaFX UI with streaming text chat
- SQLite-backed conversation memory
- Tool registry + sample tool
- Mic capture + Whisper/Piper adapter stubs
- Wake-word detector skeleton

## Phase 1 - Voice loop (push-to-talk)

- Implement `WhisperSpeechToText` via whisper.cpp subprocess (PCM -> WAV -> JSON parse).
- Implement `PiperTextToSpeech` via piper subprocess (text -> WAV bytes -> `javax.sound.sampled` playback).
- Add a "hold-to-talk" button in `main.fxml` driving `AudioCapture.start/stop`.
- Wire STT output -> `ChatService` -> TTS output as a single reactive pipeline.
- Unit-test the WAV header builder; integration-test the full loop with prerecorded WAVs.

## Phase 2 - Wake-word always-on

- Replace `SimpleWakeWordDetector` with Porcupine (commercial-free for personal use) or openWakeWord.
- Daemon listener thread driven by `AudioCapture` continuous stream.
- VAD-gated segmentation so we only invoke Whisper on actual utterances.
- Visual "listening" state in the UI.

## Phase 3 - Memory and RAG

- Add `embeddings` table to SQLite store + a `nomic-embed-text` adapter.
- `RetrievalAugmentedChatService` decorator that pulls top-k semantic matches into the prompt.
- Optional pgvector adapter (selected by profile) for shared multi-device setups.

## Phase 4 - Tool calling

- Translate `ToolSpec` -> Spring AI function definitions.
- Tool-call loop in `OllamaChatService`: parse tool calls, dispatch through `ToolRegistry`, feed `ToolResult` back to the model.
- Add safe built-ins: read clipboard, screenshot region, list files in a folder, open URL.

## Phase 5 - Plugins

- Define a plugin descriptor (JSON in a JAR's `META-INF/alfred-plugin.json`).
- Discovery: scan a `~/.alfred/plugins` directory at startup, load each JAR into an isolated `URLClassLoader`, register its `Tool` and `ChatService` beans.
- Plugin permissions UI (whitelist tools per plugin).

## Phase 6 - Multi-agent

- `Agent` SPI in `alfred-core`: a named persona with its own system prompt and tool subset.
- Coordinator agent that can delegate to specialist agents (research, code, scheduling).
- UI: tabs / sidebar showing per-agent threads.

## Phase 7 - Distribution

- `jlink` + `jpackage` to produce native installers (.dmg, .msi, .deb).
- Bundle bundled Ollama / whisper.cpp / Piper or, smarter, prompt to install them on first run.
- Auto-update channel (signed releases).

## Phase 8 - Observability

- Micrometer metrics: tokens/sec, STT latency, TTS latency, tool-call success rate.
- Optional local Prometheus + Grafana dashboard.

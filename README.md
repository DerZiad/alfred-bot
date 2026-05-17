# Alfred

A local-first desktop AI assistant built with Java 21, Spring Boot 4, Spring AI, Ollama, and JavaFX.

## Why local-first?

Latency, privacy, offline capability. Alfred talks to a model running on your machine via [Ollama](https://ollama.com), transcribes speech with [whisper.cpp](https://github.com/ggerganov/whisper.cpp), and speaks back with [Piper](https://github.com/rhasspy/piper). Nothing leaves the device by default.

## Architecture at a glance

```
+-----------------------------------------------------+
|                     alfred-ui                       |   <- JavaFX + executable
|  AlfredApplication  JavaFxLauncher  MainController  |
+-----------------------------------------------------+
        |             |              |          |
        v             v              v          v
   alfred-ai     alfred-audio   alfred-memory  alfred-tools
   (Spring AI    (Whisper STT,  (SQLite store, (Registry +
   + Ollama)     Piper TTS,     pgvector       built-in
                 mic capture,   later)         tools)
                 wake word)
        \             \              /          /
         \             \            /          /
          ----->     alfred-core    <----------
                  (domain + SPIs only)
```

`alfred-core` knows nothing about Spring, JavaFX, or any vendor. Every concrete provider is an adapter behind an SPI - swap models, swap STT engines, swap memory backends without touching the rest.

## Prerequisites

- JDK 21+
- Maven 3.9+
- [Ollama](https://ollama.com/download) running locally (`ollama serve`) with a model pulled, e.g. `ollama pull llama3.1`
- (optional, for voice) whisper.cpp binary + a `ggml-*.bin` model
- (optional, for voice) Piper binary + a voice `.onnx`

Paths to the Whisper/Piper binaries and models are configured in `alfred-ui/src/main/resources/application.yml` under `alfred.audio.*`.

## Build & run

```bash
# From the repo root
mvn -q install                              # build all modules
mvn -q -pl alfred-ui spring-boot:run        # run the desktop app
# or:
mvn -q -pl alfred-ui javafx:run             # via the JavaFX plugin
```

Default model is `llama3.1`. Override with `--alfred.ai.default-model=qwen2.5` or in `application.yml`.

## Module reference

| Module          | Responsibility                                          | Depends on            |
| --------------- | ------------------------------------------------------- | --------------------- |
| `alfred-core`   | Domain records + SPIs. Zero framework deps.             | reactor-core, slf4j   |
| `alfred-ai`     | Spring AI + Ollama implementation of `ChatService`.     | alfred-core, Spring AI|
| `alfred-audio`  | Mic capture, Whisper STT, Piper TTS, wake-word.         | alfred-core           |
| `alfred-memory` | SQLite-backed `MemoryStore` (vector recall later).      | alfred-core, sqlite   |
| `alfred-tools`  | Tool/function-calling registry + built-ins.             | alfred-core           |
| `alfred-ui`     | JavaFX app + Spring Boot bootstrap. Executable.         | all of the above      |

Each non-UI module is an auto-configuration: presence on the classpath is enough to activate it. This lets us ship "headless Alfred" (no UI module) for tests, CI, or alternative front-ends (CLI, REST, MCP server) later.

## Configuration strategy

- **Defaults** in `application.yml` (alfred-ui resources).
- **Per-OS overrides** in `application-mac.yml`, `application-win.yml`, `application-linux.yml`. Activate with `SPRING_PROFILES_ACTIVE=mac`.
- **User-local overrides** in `~/.alfred/config.yml` (loaded via `spring.config.additional-location`, to be wired in alfred-ui).
- **Typed properties** via `@ConfigurationProperties` records in each module (`AlfredAiProperties`, `AlfredAudioProperties`, `AlfredMemoryProperties`).

## Design principles

1. **Local first**: anything that can run on-device should.
2. **SPI everywhere**: no module imports a vendor type that another module also imports. Cross-module communication is through `alfred-core` interfaces.
3. **Reactive streams end-to-end**: tokens flow from Ollama -> ChatService -> Controller -> JavaFX as a `Flux<String>`, so UI never blocks waiting for a full response.
4. **Constructor injection only**. No field `@Autowired`. No `@Value` scattered through services.
5. **Records for value types**. Immutable, equals/hashCode for free, fewer footguns.
6. **Java 21 virtual threads** for any blocking work (`spring.threads.virtual.enabled=true`).
7. **Auto-configuration over component scan** for cross-module wiring, so modules are usable without the rest of the world.

See `ROADMAP.md` for what comes next.

package org.tech.alfred.ui.view;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import org.tech.alfred.core.chat.ChatRequest;
import org.tech.alfred.core.chat.ChatService;
import org.tech.alfred.core.chat.Message;
import org.tech.alfred.core.memory.MemoryEntry;
import org.tech.alfred.core.memory.MemoryStore;
import org.tech.alfred.ui.conversation.ConversationState;

/**
 * Controller for {@code main.fxml}. Wired by name (matches {@code fx:controller}).
 *
 * <p>All dependencies are constructor-injected: this is only possible because
 * {@link org.tech.alfred.ui.JavaFxLauncher} sets a Spring-backed controller
 * factory on the FXMLLoader.
 */
@Component
public class MainController {

    private final ChatService chat;
    private final MemoryStore memory;
    private final ConversationState state;

    @FXML private TextArea transcript;
    @FXML private TextField input;
    @FXML private Button sendButton;

    public MainController(ChatService chat, MemoryStore memory, ConversationState state) {
        this.chat = chat;
        this.memory = memory;
        this.state = state;
    }

    @FXML
    public void initialize() {
        input.setOnAction(e -> handleSend());
        sendButton.setOnAction(e -> handleSend());
    }

    private void handleSend() {
        String text = input.getText();
        if (text == null || text.isBlank()) return;
        input.clear();
        appendLine("You: " + text);

        Message userMsg = Message.user(text);
        UUID convId = state.conversationId();
        memory.save(new MemoryEntry(convId, userMsg, java.time.Instant.now()));

        ChatRequest req = ChatRequest.of(List.of(userMsg));
        appendLine("Alfred: ");

        StringBuilder buffer = new StringBuilder();
        chat.stream(req)
                .doOnNext(token -> Platform.runLater(() -> {
                    buffer.append(token);
                    transcript.appendText(token);
                }))
                .doOnComplete(() -> {
                    Message assistant = Message.assistant(buffer.toString());
                    memory.save(new MemoryEntry(convId, assistant, java.time.Instant.now()));
                    Platform.runLater(() -> transcript.appendText("\n\n"));
                })
                .doOnError(err -> Platform.runLater(
                        () -> transcript.appendText("\n[error: " + err.getMessage() + "]\n")))
                .subscribe();
    }

    private void appendLine(String s) {
        Platform.runLater(() -> transcript.appendText(s + "\n"));
    }
}

package org.tech.alfred.ui.hud;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Scrolling transcript of the conversation. User turns are right-aligned
 * with a cyan accent; assistant turns are left-aligned with an orange
 * accent. Streaming assistant output appends to the last bubble in
 * place so the text appears to "type itself".
 *
 * <p>Lives inside a {@link HolographicPanel} on the side of the HUD.
 */
public final class TranscriptPanel extends ScrollPane {

    private final VBox content = new VBox(8);
    private Label streamingBubble;
    private StringBuilder streamingBuffer;

    public TranscriptPanel() {
        getStyleClass().add("transcript-panel");
        content.setPadding(new Insets(8));
        setContent(content);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        setMinHeight(Region.USE_PREF_SIZE);

        // Auto-scroll to bottom whenever content height grows.
        content.heightProperty().addListener((obs, o, n) -> setVvalue(1.0));
    }

    public void appendUser(String text) {
        Platform.runLater(() -> {
            sealStream();
            Label l = bubble(text, "transcript-user");
            content.getChildren().add(l);
        });
    }

    /**
     * Append a single LLM token to the in-progress assistant bubble.
     * If no bubble is open, opens one.
     */
    public void appendAssistantToken(String token) {
        Platform.runLater(() -> {
            if (streamingBubble == null) {
                streamingBuffer = new StringBuilder();
                streamingBubble = bubble("", "transcript-assistant");
                content.getChildren().add(streamingBubble);
            }
            streamingBuffer.append(token);
            streamingBubble.setText(streamingBuffer.toString());
        });
    }

    /** Close the streaming bubble (final text becomes the new history entry). */
    public void sealStream() {
        streamingBubble = null;
        streamingBuffer = null;
    }

    public void clear() {
        Platform.runLater(() -> {
            content.getChildren().clear();
            sealStream();
        });
    }

    private Label bubble(String text, String style) {
        Label l = new Label(text);
        l.getStyleClass().addAll("transcript-bubble", style);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }
}

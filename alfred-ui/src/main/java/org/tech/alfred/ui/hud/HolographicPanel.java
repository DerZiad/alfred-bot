package org.tech.alfred.ui.hud;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * A side HUD card with the "glassmorphism" look: translucent dark fill,
 * thin glowing border, slight blur. Used to host status text, the
 * clock, and the live transcript on either side of the core.
 */
public final class HolographicPanel extends VBox {

    private final Label title;

    public HolographicPanel(String titleText) {
        getStyleClass().add("holo-panel");
        setSpacing(8);
        setPadding(new Insets(14, 18, 14, 18));

        title = new Label(titleText);
        title.getStyleClass().add("holo-panel-title");
        getChildren().add(title);
    }

    public void setTitleText(String text) {
        title.setText(text);
    }

    /** Shortcut: add a body label with the standard "data" style. */
    public Label addLine(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("holo-panel-line");
        l.setWrapText(true);
        getChildren().add(l);
        return l;
    }
}

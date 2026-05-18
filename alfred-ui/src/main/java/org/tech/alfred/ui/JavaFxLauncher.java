package org.tech.alfred.ui;

import java.io.IOException;

import org.springframework.context.ConfigurableApplicationContext;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * JavaFX {@link Application} adapter. JavaFX reflectively instantiates this
 * class with a no-arg constructor, so the Spring {@link ConfigurableApplicationContext}
 * is passed in via the static {@link #setContext(ConfigurableApplicationContext)}
 * before {@link Application#launch} is called.
 *
 * <p>The scene is sized cinematically (1440×900 by default) with the
 * window background set to deep space navy so there's no white flash on
 * boot. {@code FXMLLoader#setControllerFactory(context::getBean)} resolves
 * controllers through Spring's bean factory.
 */
public class JavaFxLauncher extends Application {

    private static ConfigurableApplicationContext context;

    static void setContext(ConfigurableApplicationContext ctx) {
        context = ctx;
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        loader.setControllerFactory(context::getBean);
        Parent root = loader.load();

        Scene scene = new Scene(root, 1440, 900, Color.web("#02060e"));
        scene.getStylesheets().add(getClass().getResource("/css/alfred.css").toExternalForm());

        stage.setTitle("ALFRED");
        stage.setScene(scene);
        stage.setMinWidth(1100);
        stage.setMinHeight(720);

        // Optional app icon - graceful if not present.
        try {
            Image icon = new Image(getClass().getResourceAsStream("/img/alfred-icon.png"));
            if (!icon.isError()) stage.getIcons().add(icon);
        } catch (Exception ignored) {
            // No icon shipped yet; the OS-default is fine.
        }

        stage.show();
    }

    @Override
    public void stop() {
        Platform.exit();
    }
}

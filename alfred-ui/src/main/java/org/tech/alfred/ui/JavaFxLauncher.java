package org.tech.alfred.ui;

import java.io.IOException;

import org.springframework.context.ConfigurableApplicationContext;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * JavaFX {@link Application} adapter. JavaFX reflectively instantiates this
 * class with a no-arg constructor, so the Spring {@link ConfigurableApplicationContext}
 * is passed in via the static {@link #setContext(ConfigurableApplicationContext)}
 * before {@link Application#launch} is called.
 *
 * <p>Crucially we set {@code FXMLLoader#setControllerFactory(context::getBean)} so
 * controllers declared in FXML are resolved through Spring's bean factory -
 * giving us full constructor injection in controllers.
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

        Scene scene = new Scene(root, 960, 640);
        scene.getStylesheets().add(getClass().getResource("/css/alfred.css").toExternalForm());

        stage.setTitle("Alfred");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        Platform.exit();
    }
}

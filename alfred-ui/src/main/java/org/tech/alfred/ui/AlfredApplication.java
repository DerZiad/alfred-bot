package org.tech.alfred.ui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

import javafx.application.Application;

/**
 * Alfred's executable entry point.
 *
 * <p>Bootstrap order:
 * <ol>
 *   <li>Spring Boot starts in {@link WebApplicationType#NONE} (no servlet container).
 *       This wires up all auto-configurations: alfred-ai, alfred-audio,
 *       alfred-memory, alfred-tools.</li>
 *   <li>The resulting {@code ApplicationContext} is handed to {@link JavaFxLauncher}
 *       via a static field (the canonical pattern - JavaFX instantiates the
 *       Application class itself via reflection, so we can't inject through
 *       its constructor).</li>
 *   <li>{@code Application.launch(JavaFxLauncher.class)} blocks until the UI
 *       closes; on exit we shut Spring down cleanly.</li>
 * </ol>
 *
 * <p>scanBasePackages spans the full {@code org.tech.alfred} root so component
 * scanning picks up downstream modules.
 *
 * <p>We exclude Spring Boot's JDBC autoconfigurations: alfred-memory configures
 * its own SQLite {@link javax.sql.DataSource} (computed path under user.home).
 * Boot's default would otherwise try to provision a HikariCP DataSource at
 * startup, fail because there's no spring.datasource.url, and crash the app
 * before alfred-memory's bean is created.
 */
@SpringBootApplication(
        scanBasePackages = "org.tech.alfred",
        exclude = {
                DataSourceAutoConfiguration.class,
                JdbcTemplateAutoConfiguration.class
        })
public class AlfredApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder()
                .sources(AlfredApplication.class)
                .web(WebApplicationType.NONE)
                .headless(false) // we need a display for JavaFX
                .run(args);

        JavaFxLauncher.setContext(context);
        try {
            Application.launch(JavaFxLauncher.class, args);
        } finally {
            context.close();
        }
    }

    private static final class SpringApplicationBuilder {
        private Class<?> source;
        private WebApplicationType webType = WebApplicationType.NONE;
        private boolean headless = true;

        SpringApplicationBuilder sources(Class<?> source) { this.source = source; return this; }
        SpringApplicationBuilder web(WebApplicationType t) { this.webType = t; return this; }
        SpringApplicationBuilder headless(boolean h) { this.headless = h; return this; }

        ConfigurableApplicationContext run(String... args) {
            SpringApplication app = new SpringApplication(source);
            app.setWebApplicationType(webType);
            app.setHeadless(headless);
            return app.run(args);
        }
    }
}

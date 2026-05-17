package org.tech.alfred.memory.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import org.sqlite.JDBC;
import org.tech.alfred.core.memory.MemoryStore;
import org.tech.alfred.memory.sqlite.SqliteMemoryStore;

@AutoConfiguration
@EnableConfigurationProperties(AlfredMemoryProperties.class)
public class AlfredMemoryAutoConfiguration {

    /**
     * Local-only SQLite DataSource.
     *
     * <p>Marked {@link Primary} so it wins over the HikariCP DataSource that Spring Boot 4
     * auto-registers whenever a JDBC driver is on the classpath. The auto-registered one
     * has no URL configured and would fail at first use; ours has the SQLite file path
     * we compute from {@code user.home}.
     *
     * <p>We also deliberately avoid HikariCP here: SQLite serializes writes anyway, so a
     * connection pool buys nothing and adds startup cost.
     */
    @Bean(name = "alfredMemoryDataSource")
    @Primary
    public DataSource alfredMemoryDataSource(AlfredMemoryProperties props) {
        props.path().getParent().toFile().mkdirs();
        SimpleDriverDataSource ds = new SimpleDriverDataSource();
        ds.setDriver(new JDBC());
        ds.setUrl("jdbc:sqlite:" + props.path());
        return ds;
    }

    /**
     * Inject by qualifier - both belt and braces alongside {@code @Primary} above.
     * Future bean consumers can disambiguate the same way without surprises.
     */
    @Bean
    public MemoryStore memoryStore(@Qualifier("alfredMemoryDataSource") DataSource ds) {
        SqliteMemoryStore store = new SqliteMemoryStore(ds);
        store.init();
        return store;
    }
}

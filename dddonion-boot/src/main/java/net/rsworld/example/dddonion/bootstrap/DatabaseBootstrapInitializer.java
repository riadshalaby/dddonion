package net.rsworld.example.dddonion.bootstrap;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/** Ensures the configured MariaDB database exists before Liquibase initializes. */
@Slf4j
public class DatabaseBootstrapInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String JDBC_MARIADB_PREFIX = "jdbc:mariadb://";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Environment env = applicationContext.getEnvironment();

        String jdbcUrl = env.getProperty("spring.liquibase.url");
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return;
        }
        if (!jdbcUrl.startsWith(JDBC_MARIADB_PREFIX)) {
            log.debug("Skipping DB bootstrap for unsupported JDBC URL: {}", jdbcUrl);
            return;
        }

        DbTarget target = parse(jdbcUrl);
        String user = firstNonBlank(env.getProperty("spring.liquibase.user"), env.getProperty("spring.r2dbc.username"));
        String password = firstNonBlank(
                env.getProperty("spring.liquibase.password"), env.getProperty("spring.r2dbc.password"), "");

        if (user == null || user.isBlank()) {
            throw new IllegalStateException("Cannot bootstrap database: missing spring.liquibase.user");
        }

        String sql = "CREATE DATABASE IF NOT EXISTS `" + target.databaseName().replace("`", "``") + "`";
        try (Connection connection = DriverManager.getConnection(target.serverJdbcUrl(), user, password);
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
            log.info("Ensured database exists: {}", target.databaseName());
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to bootstrap database '%s' using '%s'"
                            .formatted(target.databaseName(), target.serverJdbcUrl()),
                    e);
        }
    }

    static DbTarget parse(String jdbcUrl) {
        String remainder = jdbcUrl.substring(JDBC_MARIADB_PREFIX.length());
        int slashIndex = remainder.indexOf('/');
        if (slashIndex < 0) {
            throw new IllegalArgumentException("Liquibase JDBC URL must contain a database path: " + jdbcUrl);
        }

        String hostPort = remainder.substring(0, slashIndex);
        String databaseAndQuery = remainder.substring(slashIndex + 1);
        int queryIndex = databaseAndQuery.indexOf('?');

        String databaseName = queryIndex >= 0 ? databaseAndQuery.substring(0, queryIndex) : databaseAndQuery;
        if (databaseName.isBlank()) {
            throw new IllegalArgumentException("Liquibase JDBC URL must include a database name: " + jdbcUrl);
        }

        String query = queryIndex >= 0 ? databaseAndQuery.substring(queryIndex + 1) : "";
        String serverJdbcUrl = JDBC_MARIADB_PREFIX + hostPort + "/" + (query.isBlank() ? "" : "?" + query);

        return new DbTarget(serverJdbcUrl, databaseName);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    record DbTarget(String serverJdbcUrl, String databaseName) {}
}

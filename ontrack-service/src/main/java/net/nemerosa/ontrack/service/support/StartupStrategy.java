package net.nemerosa.ontrack.service.support;

import net.nemerosa.ontrack.model.support.StartupService;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class StartupStrategy implements FlywayMigrationStrategy {

    private final Logger logger = LoggerFactory.getLogger(StartupStrategy.class);

    private final ApplicationContext applicationContext;

    @Autowired
    public StartupStrategy(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void migrate(Flyway flyway) {
        // Old schema histories carry a migration type which Flyway no longer knows
        relabelSpringJdbcMigrations(flyway);

        // Migrating the database
        logger.info("Migrating the database...");
        flyway.migrate();

        // Getting the startup services
        List<StartupService> startupServices = applicationContext.getBeansOfType(StartupService.class).values().stream()
                // ... order them by starting number
                .sorted(Comparator.comparing(StartupService::startupOrder))
                // ... getting the list
                .collect(Collectors.toList());

        // Logging
        logger.info("List of startup services...");
        startupServices.forEach(startupService ->
                logger.info("[{}] {}", startupService.startupOrder(), startupService.getName())
        );

        // Startup services
        logger.info("Starting startup services...");
        startupServices.forEach(startupService -> {
            logger.info("Starting [{}] {}", startupService.startupOrder(), startupService.getName());
            startupService.start();
        });
    }

    /**
     * Old Flyway versions recorded the Java migrations as {@code SPRING_JDBC}, which the current
     * Flyway refuses: they are relabelled as {@code JDBC}, the type it gives them now (#2050). The
     * Flyway of 5.x reads {@code JDBC} too.
     */
    private void relabelSpringJdbcMigrations(Flyway flyway) {
        Configuration configuration = flyway.getConfiguration();
        String table = historyTable(configuration);
        JdbcTemplate jdbc = new JdbcTemplate(configuration.getDataSource());
        // A new installation has no schema history yet
        Boolean exists = jdbc.queryForObject("SELECT TO_REGCLASS(?) IS NOT NULL", Boolean.class, table);
        if (Boolean.TRUE.equals(exists)) {
            int count = jdbc.update("UPDATE " + table + " SET type = 'JDBC' WHERE type = 'SPRING_JDBC'");
            if (count > 0) {
                logger.info("Relabelled {} SPRING_JDBC migration(s) of {} as JDBC", count, table);
            }
        }
    }

    /**
     * Qualified name of the schema history table, quoted as Flyway quotes it.
     */
    private static String historyTable(Configuration configuration) {
        String table = quote(configuration.getTable());
        String schema = configuration.getDefaultSchema();
        if (schema == null && configuration.getSchemas().length > 0) {
            schema = configuration.getSchemas()[0];
        }
        return schema != null ? quote(schema) + "." + table : table;
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}

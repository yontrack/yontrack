package net.nemerosa.ontrack.service.support;

import net.nemerosa.ontrack.model.support.StartupService;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.ApplicationContext;
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
}

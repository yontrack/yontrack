package net.nemerosa.ontrack.service.support;

import net.nemerosa.ontrack.model.support.OntrackConfigProperties;
import net.nemerosa.ontrack.model.support.StartupService;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/**
 * Migrates the database at startup, then starts the {@link StartupService}s.
 * <p>
 * The migrations run in two phases (#2057): the expand migrations of {@code db/migration} always,
 * then the contract migrations of {@code db/contract}, according to
 * {@code ontrack.config.database.contract-migrations}. With
 * {@code ontrack.config.database.run-contract-and-exit}, the application runs both and exits, before
 * any startup service and before the web server listens.
 * <p>
 * The startup services run under one advisory lock (#2087), so that replicas starting together run
 * them one at a time: what they write must stay idempotent.
 *
 * @see ContractMigrations
 */
@Component
public class StartupStrategy implements FlywayMigrationStrategy {

    /**
     * First key of the advisory lock of the startup phase, the second being {@link #LOCK_KEY}:
     * "STRT" in ASCII — next to the locks on the trails ("TRAI"), on the blobs of the evidence
     * ("EVID"), on the runs of the jobs ("JOBR") and on the processing of the auto-versioning
     * orders ("AVQU", "AVMG").
     */
    public static final int LOCK_CLASS = 0x53545254;

    /**
     * Second key of the advisory lock of the startup phase: there is only one.
     */
    public static final int LOCK_KEY = 0;

    private final Logger logger = LoggerFactory.getLogger(StartupStrategy.class);

    private final ApplicationContext applicationContext;
    private final OntrackConfigProperties.DatabaseProperties database;
    private final List<String> contractLocations;
    private final IntConsumer exit;

    @Autowired
    public StartupStrategy(ApplicationContext applicationContext, OntrackConfigProperties ontrackConfigProperties) {
        this(applicationContext, ontrackConfigProperties.getDatabase(), List.of(ContractMigrations.LOCATION), System::exit);
    }

    /**
     * @param contractLocations Locations of the contract migrations
     * @param exit              Exits the application with a code, in the {@code run-contract-and-exit} mode
     */
    StartupStrategy(ApplicationContext applicationContext, OntrackConfigProperties.DatabaseProperties database, List<String> contractLocations, IntConsumer exit) {
        this.applicationContext = applicationContext;
        this.database = database;
        this.contractLocations = contractLocations;
        this.exit = exit;
    }

    @Override
    public void migrate(Flyway flyway) {
        // Old schema histories carry a migration type which Flyway no longer knows
        relabelSpringJdbcMigrations(flyway);

        // Only the migrations, then exiting
        if (database.getRunContractAndExit()) {
            runContractAndExit(flyway);
            return;
        }

        // Migrating the database
        migrateExpand(flyway);
        Flyway contract = ContractMigrations.flyway(flyway, contractLocations);
        if (database.getContractMigrations() == OntrackConfigProperties.ContractMigrationsMode.STARTUP) {
            migrateContract(contract);
        } else {
            List<String> pending = Arrays.stream(contract.info().pending())
                    .map(StartupStrategy::describe)
                    .toList();
            logger.info("[database] Contract migrations deferred, {} pending: {}", pending.size(), pending);
        }

        // Startup services, one replica at a time
        runStartupServices(flyway.getConfiguration().getDataSource());
    }

    /**
     * Runs the {@link StartupService}s, in order, under the advisory lock of the startup phase
     * (#2087): replicas starting together run them one at a time, the others waiting for the lock.
     * <p>
     * The lock is a session-level one, held by a connection of its own for the whole phase, and
     * released at its end, failed or not. A replica which dies holding it releases it with its
     * session.
     */
    private void runStartupServices(DataSource dataSource) {
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

        try (Connection connection = dataSource.getConnection()) {
            lock(connection);
            try {
                // Startup services
                logger.info("Starting startup services...");
                startupServices.forEach(startupService -> {
                    logger.info("Starting [{}] {}", startupService.startupOrder(), startupService.getName());
                    startupService.start();
                });
            } finally {
                unlock(connection);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("[startup] Failure on the startup lock", ex);
        }
    }

    private void lock(Connection connection) throws SQLException {
        // The lock is held by the session, not by a transaction
        connection.setAutoCommit(true);
        if (!callLockFunction(connection, "pg_try_advisory_lock")) {
            logger.info("[startup] Another instance is running its startup services, waiting for it...");
            try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_lock(?, ?)")) {
                statement.setInt(1, LOCK_CLASS);
                statement.setInt(2, LOCK_KEY);
                // pg_advisory_lock returns void: the row is read and ignored
                statement.executeQuery().close();
            }
        }
        logger.info("[startup] Startup lock acquired");
    }

    /**
     * Releases the lock. Should this fail, the session is ended instead, which releases it: back
     * in its pool, the connection would keep it.
     */
    private void unlock(Connection connection) throws SQLException {
        try {
            if (callLockFunction(connection, "pg_advisory_unlock")) {
                logger.info("[startup] Startup lock released");
            } else {
                logger.warn("[startup] The startup lock was not held when releasing it");
            }
        } catch (SQLException ex) {
            logger.error("[startup] Cannot release the startup lock, ending the session instead", ex);
            connection.abort(Runnable::run);
        }
    }

    /**
     * Calls an advisory lock function returning a boolean on the lock of the startup phase.
     */
    private static boolean callLockFunction(Connection connection, String function) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT " + function + "(?, ?)")) {
            statement.setInt(1, LOCK_CLASS);
            statement.setInt(2, LOCK_KEY);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    /**
     * Runs the pending expand migrations - none, after an upgrade whose new version has started -
     * then the pending contract migrations, and exits with 0, or with 1 when a migration fails.
     */
    private void runContractAndExit(Flyway flyway) {
        int code;
        try {
            logger.info("[database] Running the contract migrations, then exiting");
            migrateExpand(flyway);
            migrateContract(ContractMigrations.flyway(flyway, contractLocations));
            logger.info("[database] Contract migrations done, exiting");
            code = 0;
        } catch (RuntimeException ex) {
            logger.error("[database] Contract migrations failed, exiting", ex);
            code = 1;
        }
        exit.accept(code);
    }

    private void migrateExpand(Flyway flyway) {
        logger.info("Migrating the database...");
        MigrateResult result = flyway.migrate();
        logger.info("[database] Expand migrations applied: {}", result.migrationsExecuted);
    }

    private void migrateContract(Flyway contract) {
        logger.info("[database] Running the contract migrations...");
        MigrateResult result = contract.migrate();
        if (result.migrations.isEmpty()) {
            logger.info("[database] Contract migrations: none pending");
        } else {
            result.migrations.forEach(migration ->
                    logger.info("[database] Contract migration applied: V{} - {}", migration.version, migration.description)
            );
        }
    }

    private static String describe(MigrationInfo info) {
        return "V" + info.getVersion() + " - " + info.getDescription();
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

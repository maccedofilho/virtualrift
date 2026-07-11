package com.virtualrift.common.migration;

import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

public final class FlywayMigrationMain {

    private FlywayMigrationMain() {
    }

    public static void main(String[] args) {
        String databaseUrl = requiredEnvironment("DB_URL");
        String migrationUser = requiredEnvironment("DB_MIGRATION_USER");
        String migrationPassword = requiredEnvironment("DB_MIGRATION_PASSWORD");
        String runtimeUser = requiredEnvironment("DB_RUNTIME_USER");
        String migrationTable = requiredEnvironment("DB_MIGRATION_TABLE");
        String[] locations = migrationLocations(requiredEnvironment("DB_MIGRATION_LOCATIONS"));
        int connectRetries = integerEnvironment("DB_MIGRATION_CONNECT_RETRIES", 60);

        requireIdentifier("DB_MIGRATION_TABLE", migrationTable);
        requireIdentifier("DB_RUNTIME_USER", runtimeUser);

        Flyway flyway = Flyway.configure()
                .dataSource(databaseUrl, migrationUser, migrationPassword)
                .locations(locations)
                .table(migrationTable)
                .baselineOnMigrate(true)
                .connectRetries(connectRetries)
                .load();
        flyway.migrate();
        revokeRuntimeMigrationHistoryAccess(flyway, runtimeUser, migrationTable);

        System.out.printf("Database migrations completed using table %s.%n", migrationTable);
    }

    private static void revokeRuntimeMigrationHistoryAccess(Flyway flyway,
                                                             String runtimeUser,
                                                             String migrationTable) {
        String sql = "REVOKE ALL PRIVILEGES ON TABLE public.\"" + migrationTable
                + "\" FROM \"" + runtimeUser + "\"";
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not isolate Flyway history from the runtime role", exception);
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for database migration");
        }
        return value.trim();
    }

    private static int integerEnvironment(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 0) {
                throw new NumberFormatException("negative value");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(name + " must be a non-negative integer", exception);
        }
    }

    private static String[] migrationLocations(String value) {
        String[] locations = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(location -> !location.isBlank())
                .toArray(String[]::new);
        if (locations.length == 0) {
            throw new IllegalStateException("DB_MIGRATION_LOCATIONS must contain at least one location");
        }
        return locations;
    }

    private static void requireIdentifier(String name, String value) {
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalStateException(name + " must be a valid SQL identifier");
        }
    }
}

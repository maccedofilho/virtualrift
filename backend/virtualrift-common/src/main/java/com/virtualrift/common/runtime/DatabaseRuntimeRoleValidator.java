package com.virtualrift.common.runtime;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

final class DatabaseRuntimeRoleValidator {

    private static final String ROLE_AUDIT_QUERY = """
            SELECT current_user AS role_name,
                   EXISTS (
                       SELECT 1
                       FROM pg_roles candidate
                       WHERE (
                           candidate.rolsuper
                           OR candidate.rolcreatedb
                           OR candidate.rolcreaterole
                           OR candidate.rolreplication
                           OR candidate.rolbypassrls
                       )
                       AND pg_has_role(current_user, candidate.oid, 'MEMBER')
                   ) AS elevated_role,
                   has_schema_privilege(current_user, current_schema(), 'CREATE') AS schema_create,
                   has_database_privilege(current_user, current_database(), 'TEMP') AS database_temp,
                   EXISTS (
                       SELECT 1
                       FROM pg_class relation
                       JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                       WHERE namespace.nspname = current_schema()
                         AND relation.relkind IN ('r', 'p')
                         AND relation.relrowsecurity
                         AND NOT relation.relforcerowsecurity
                         AND pg_has_role(current_user, relation.relowner, 'MEMBER')
                   ) AS owns_unforced_rls_table,
                   EXISTS (
                       SELECT 1
                       FROM pg_class relation
                       JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                       WHERE namespace.nspname = current_schema()
                         AND relation.relname LIKE '%flyway_schema_history'
                         AND (
                             has_table_privilege(current_user, relation.oid, 'SELECT')
                             OR has_table_privilege(current_user, relation.oid, 'INSERT')
                             OR has_table_privilege(current_user, relation.oid, 'UPDATE')
                             OR has_table_privilege(current_user, relation.oid, 'DELETE')
                         )
                   ) AS flyway_history_access
            """;

    private final DataSource dataSource;
    private final RuntimeEnvironment environment;

    DatabaseRuntimeRoleValidator(DataSource dataSource, String runtimeEnvironmentValue) {
        this.dataSource = dataSource;
        this.environment = RuntimeEnvironment.fromValue(runtimeEnvironmentValue);
    }

    void validate() {
        if (environment.isLocal()) {
            return;
        }

        try (Connection connection = dataSource.getConnection()) {
            requirePostgreSql(connection);
            try (PreparedStatement statement = connection.prepareStatement(ROLE_AUDIT_QUERY);
                 ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("PostgreSQL did not return the current runtime role");
                }
                rejectUnsafeRole(result);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not validate the PostgreSQL runtime role", exception);
        }
    }

    private void requirePostgreSql(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName();
        if (!"PostgreSQL".equalsIgnoreCase(productName)) {
            throw new IllegalStateException(
                    "Production database must be PostgreSQL, but runtime connected to " + productName
            );
        }
    }

    private void rejectUnsafeRole(ResultSet result) throws SQLException {
        List<String> violations = new ArrayList<>();
        if (result.getBoolean("elevated_role")) {
            violations.add("belongs to an elevated PostgreSQL role");
        }
        if (result.getBoolean("schema_create")) {
            violations.add("can create objects in the application schema");
        }
        if (result.getBoolean("database_temp")) {
            violations.add("can create temporary database objects");
        }
        if (result.getBoolean("owns_unforced_rls_table")) {
            violations.add("can bypass row-level security through table ownership");
        }
        if (result.getBoolean("flyway_history_access")) {
            violations.add("can access Flyway schema history");
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Unsafe PostgreSQL runtime role " + result.getString("role_name") + ": "
                            + String.join("; ", violations)
            );
        }
    }
}

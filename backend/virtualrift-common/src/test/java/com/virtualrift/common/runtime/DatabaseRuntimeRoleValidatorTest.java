package com.virtualrift.common.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("DatabaseRuntimeRoleValidator Tests")
class DatabaseRuntimeRoleValidatorTest {

    private DataSource dataSource;
    private Connection connection;
    private ResultSet resultSet;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("role_name")).thenReturn("virtualrift_auth");
    }

    @Test
    @DisplayName("should skip database audit in local environment")
    void validate_quandoLocal_naoAbreConexao() {
        new DatabaseRuntimeRoleValidator(dataSource, "local").validate();

        verifyNoInteractions(dataSource);
    }

    @Test
    @DisplayName("should accept restricted runtime role outside local environment")
    void validate_quandoRoleRestrita_aceita() {
        DatabaseRuntimeRoleValidator validator = new DatabaseRuntimeRoleValidator(dataSource, "production");

        assertDoesNotThrow(validator::validate);
    }

    @Test
    @DisplayName("should reject schema owner as runtime role")
    void validate_quandoRolePodeCriarSchema_rejeita() throws Exception {
        when(resultSet.getBoolean("schema_create")).thenReturn(true);
        DatabaseRuntimeRoleValidator validator = new DatabaseRuntimeRoleValidator(dataSource, "production");

        IllegalStateException exception = assertThrows(IllegalStateException.class, validator::validate);

        assertTrue(exception.getMessage().contains("can create objects"));
    }

    @Test
    @DisplayName("should reject runtime access to Flyway history")
    void validate_quandoRoleAcessaFlyway_rejeita() throws Exception {
        when(resultSet.getBoolean("flyway_history_access")).thenReturn(true);
        DatabaseRuntimeRoleValidator validator = new DatabaseRuntimeRoleValidator(dataSource, "production");

        IllegalStateException exception = assertThrows(IllegalStateException.class, validator::validate);

        assertTrue(exception.getMessage().contains("Flyway schema history"));
    }

    @Test
    @DisplayName("should reject non-PostgreSQL production database")
    void validate_quandoBancoNaoPostgres_rejeita() throws Exception {
        when(connection.getMetaData().getDatabaseProductName()).thenReturn("H2");
        DatabaseRuntimeRoleValidator validator = new DatabaseRuntimeRoleValidator(dataSource, "staging");

        assertThrows(IllegalStateException.class, validator::validate);
    }
}

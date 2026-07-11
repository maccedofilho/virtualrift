package com.virtualrift.common.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseRuntimeRoleAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DatabaseRuntimeRoleAutoConfiguration.class))
            .withPropertyValues(
                    "virtualrift.runtime.environment=production",
                    "spring.flyway.enabled=false"
            );

    @Test
    void shouldRegisterValidatorForRestrictedRuntimeDataSource() {
        contextRunner
                .withBean(DataSource.class, () -> dataSource(false))
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(DatabaseRuntimeRoleValidator.class));
    }

    @Test
    void shouldFailApplicationContextForSchemaOwnerDataSource() {
        contextRunner
                .withBean(DataSource.class, () -> dataSource(true))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectEmbeddedFlywayOutsideLocalRuntime() {
        contextRunner
                .withPropertyValues("spring.flyway.enabled=true")
                .withBean(DataSource.class, () -> dataSource(false))
                .run(context -> assertThat(context).hasFailed());
    }

    private DataSource dataSource(boolean schemaCreate) {
        try {
            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            DatabaseMetaData metadata = mock(DatabaseMetaData.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            ResultSet resultSet = mock(ResultSet.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.getMetaData()).thenReturn(metadata);
            when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getString("role_name")).thenReturn("virtualrift_auth");
            when(resultSet.getBoolean("schema_create")).thenReturn(schemaCreate);
            return dataSource;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}

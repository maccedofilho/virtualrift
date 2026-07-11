package com.virtualrift.common.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnBean(DataSource.class)
public class DatabaseRuntimeRoleAutoConfiguration {

    @Bean
    DatabaseRuntimeRoleValidator databaseRuntimeRoleValidator(
            DataSource dataSource,
            @Value("${virtualrift.runtime.environment:local}") String runtimeEnvironmentValue,
            @Value("${spring.flyway.enabled:true}") boolean flywayEnabled) {
        RuntimeEnvironment environment = RuntimeEnvironment.fromValue(runtimeEnvironmentValue);
        if (!environment.isLocal() && flywayEnabled) {
            throw new IllegalStateException(
                    "spring.flyway.enabled must be false outside local runtime; use the dedicated migration init container"
            );
        }
        DatabaseRuntimeRoleValidator validator = new DatabaseRuntimeRoleValidator(
                dataSource,
                runtimeEnvironmentValue
        );
        validator.validate();
        return validator;
    }
}

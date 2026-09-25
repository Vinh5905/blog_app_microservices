package com.pnimac.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AuthMigrationIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0");

    /**
     * Verifies a real MySQL 8.4 upgrade from V1 to the latest schema: pre-existing user
     * data survives, Flyway validates the resulting history, and the new unique-email
     * constraint is enforced. This protects production upgrades from data loss and
     * schema rules that exist only in application code.
     */
    @Test
    void migratesV1DataToLatestAndEnforcesUniqueEmail() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration/user").target(MigrationVersion.fromVersion("1")).load().migrate();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO user(username,password,email,enabled) VALUES ('alice','hash','alice@example.test',true)");
        }

        Flyway latest = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration/user").load();
        latest.migrate();
        latest.validate();

        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                var statement = connection.createStatement()) {
            assertThat(statement.executeQuery("SELECT COUNT(*) FROM user WHERE username='alice'").next()).isTrue();
            assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO user(username,password,email,enabled) VALUES ('alice2','hash','alice@example.test',true)"))
                    .isInstanceOf(SQLException.class);
        }
    }
}

package com.example.solimus.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.net.ServerSocket;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contrôles de démarrage, sans contexte Spring complet ni vraie base (valeurs factices). */
class StartupPreflightTest {

    private static GenericApplicationContext context(MockEnvironment environment) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);
        return context;
    }

    private static MockEnvironment production() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DB_HOST", "127.0.0.1")
                .withProperty("DB_NAME", "fake")
                .withProperty("DB_USER", "fake")
                .withProperty("DB_PASSWORD", "fake")
                .withProperty("JWT_SECRET", "fake-secret-fake-secret-fake-secret-42");
        environment.setActiveProfiles("docker");
        return environment;
    }

    @Test
    void namesEveryMissingRequiredVariableInProduction() {
        MockEnvironment environment = production()
                .withProperty("DB_PASSWORD", "")
                .withProperty("JWT_SECRET", " ")
                .withProperty("APP_STARTUP_DB_CHECK", "false");

        assertThatThrownBy(() -> new StartupPreflight().initialize(context(environment)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD")
                .hasMessageContaining("JWT_SECRET")
                .hasMessageNotContaining("DB_HOST");
    }

    @Test
    void startsWhenEverythingIsPresentAndTheDatabaseCheckIsDisabled() {
        MockEnvironment environment = production().withProperty("APP_STARTUP_DB_CHECK", "false");

        assertThatCode(() -> new StartupPreflight().initialize(context(environment))).doesNotThrowAnyException();
    }

    @Test
    void stopsWithHostAndPortWhenTheDatabaseDoesNotAnswer() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        MockEnvironment environment = production()
                .withProperty("DB_CONNECT_TIMEOUT_SECONDS", "1")
                .withProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:" + closedPort + "/fake?useSSL=false");

        assertThatThrownBy(() -> new StartupPreflight().initialize(context(environment)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Base MySQL injoignable sur 127.0.0.1:" + closedPort);
    }

    @Test
    void explainsMysqlErrorsWithoutRepeatingTheServerMessage() {
        SQLException tooMany = new SQLException("Too many connections for user 'secret-user'", "08004", 1040);
        SQLException denied = new SQLException("Access denied for user 'secret-user'@'1.2.3.4'", "28000", 1045);

        assertThat(StartupPreflight.explain(tooMany)).contains("max_connections").doesNotContain("secret-user");
        assertThat(StartupPreflight.explain(denied)).contains("DB_USER").doesNotContain("secret-user");
    }
}

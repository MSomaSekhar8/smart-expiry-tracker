package com.pantrytracker;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Startup validation must fail with clear messages and must NEVER echo the
 * received DB_URL when it could carry embedded credentials.
 */
class PantryTrackerApplicationTest {

    private Map<String, String> validEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("DB_URL", "jdbc:postgresql://dbhost:5432/postgres");
        env.put("DB_PASSWORD", "pw");
        return env;
    }

    @Test
    void missingDbUrlFailsWithClearError() {
        Map<String, String> env = validEnv();
        env.remove("DB_URL");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> PantryTrackerApplication.validateRequiredEnv(env));

        assertTrue(ex.getMessage().contains("DB_URL is not configured"));
    }

    @Test
    void nonJdbcUrlFailsWithGenericErrorWithoutEchoingTheValue() {
        Map<String, String> env = validEnv();
        env.put("DB_URL", "postgresql://dbhost:5432/postgres");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> PantryTrackerApplication.validateRequiredEnv(env));

        assertTrue(ex.getMessage().contains("jdbc:postgresql:"));
        assertTrue(!ex.getMessage().contains("postgresql://dbhost"));
    }

    @Test
    void malformedUrlWithCredentialsNeverLeaksUsernameOrPassword() {
        Map<String, String> env = validEnv();
        env.put("DB_URL", "jdbc:postgresqlx://username:SuperSecretPassword@dbhost:5432/postgres");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> PantryTrackerApplication.validateRequiredEnv(env));

        assertTrue(!ex.getMessage().contains("username"));
        assertTrue(!ex.getMessage().contains("SuperSecretPassword"));
    }

    @Test
    void malformedUrlWithAnotherSecretNeverLeaksIt() {
        Map<String, String> env = validEnv();
        env.put("DB_URL", "jdbc:postgresqlz://postgres:VerySecret123@dbhost:5432/postgres");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> PantryTrackerApplication.validateRequiredEnv(env));

        assertTrue(!ex.getMessage().contains("VerySecret123"));
        assertTrue(!ex.getMessage().contains("dbhost"));
    }

    @Test
    void validJdbcPostgresUrlPasses() {
        assertDoesNotThrow(() -> PantryTrackerApplication.validateRequiredEnv(validEnv()));
    }

    @Test
    void credentialBearingUrlWithValidPrefixStillPassesFormatValidation() {
        Map<String, String> env = validEnv();
        env.put("DB_URL", "jdbc:postgresql://username:SomePassword@dbhost:5432/postgres");

        assertDoesNotThrow(() -> PantryTrackerApplication.validateRequiredEnv(env));
    }

    @Test
    void missingDbPasswordFailsWithClearError() {
        Map<String, String> env = validEnv();
        env.remove("DB_PASSWORD");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> PantryTrackerApplication.validateRequiredEnv(env));

        assertTrue(ex.getMessage().contains("DB_PASSWORD is not configured"));
    }
}
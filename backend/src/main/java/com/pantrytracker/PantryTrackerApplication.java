package com.pantrytracker;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point. Configuration comes from environment variables (see the
 * project README). Required variables are validated here — before Spring
 * starts — so a misconfigured deployment fails with a clear message instead
 * of a cryptic Hikari/Flyway error later in startup.
 */
@SpringBootApplication
@EnableScheduling
public class PantryTrackerApplication {

    private static final String JDBC_PREFIX = "jdbc:postgresql:";

    public static void main(String[] args) {
        validateRequiredEnv();
        SpringApplication.run(PantryTrackerApplication.class, args);
    }

    private static void validateRequiredEnv() {
        validateRequiredEnv(System.getenv());
    }

    /**
     * Package-private for startup-validation tests.
     */
    static void validateRequiredEnv(Map<String, String> env) {
        String dbUrl = env.get("DB_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            throw new IllegalStateException(
                    "DB_URL is not configured. Set DB_URL to a JDBC PostgreSQL URL, e.g. "
                            + "jdbc:postgresql://<host>:5432/postgres?sslmode=require");
        }
        if (!dbUrl.startsWith(JDBC_PREFIX)) {
            // Deliberately no "Received:" echo: a malformed URL may contain
            // embedded credentials (e.g. jdbc:postgresqlx://user:pass@host...),
            // which must never surface in logs or exceptions.
            throw new IllegalStateException(
                    "DB_URL must be a JDBC PostgreSQL URL starting with '" + JDBC_PREFIX + "'. "
                            + "For Supabase, the connection string postgresql://... must be prefixed "
                            + "with jdbc:, e.g. jdbc:postgresql://<host>:5432/postgres?sslmode=require");
        }
        String dbPassword = env.get("DB_PASSWORD");
        if (dbPassword == null || dbPassword.isBlank()) {
            throw new IllegalStateException(
                    "DB_PASSWORD is not configured. Set DB_PASSWORD (e.g. via your deployment "
                            + "platform's secret store). The app refuses to start without it.");
        }
    }
}
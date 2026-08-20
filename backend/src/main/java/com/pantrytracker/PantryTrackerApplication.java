package com.pantrytracker;

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
        String dbUrl = System.getenv("DB_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            throw new IllegalStateException(
                    "DB_URL is not configured. Set DB_URL to a JDBC PostgreSQL URL, e.g. "
                            + "jdbc:postgresql://<host>:5432/postgres?sslmode=require");
        }
        if (!dbUrl.startsWith(JDBC_PREFIX)) {
            throw new IllegalStateException(
                    "DB_URL must be a JDBC PostgreSQL URL starting with '" + JDBC_PREFIX + "'. "
                            + "Received: '" + preview(dbUrl) + "'. For Supabase, the connection string "
                            + "postgresql://... must be prefixed with jdbc:, e.g. "
                            + "jdbc:postgresql://<host>:5432/postgres?sslmode=require");
        }
        String dbPassword = System.getenv("DB_PASSWORD");
        if (dbPassword == null || dbPassword.isBlank()) {
            throw new IllegalStateException(
                    "DB_PASSWORD is not configured. Set DB_PASSWORD (e.g. via your deployment "
                            + "platform's secret store). The app refuses to start without it.");
        }
    }

    private static String preview(String value) {
        int end = Math.min(value.length(), 48);
        return end < value.length() ? value.substring(0, end) + "..." : value;
    }
}
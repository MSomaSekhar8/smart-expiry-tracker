package com.pantrytracker.notification;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.lang.reflect.Field;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

/**
 * Regression test for the production bug where Hibernate sent the
 * {@code NotificationType} enum as VARCHAR into the PostgreSQL named enum column
 * {@code notifications.type} (type {@code notification_type}).
 *
 * <p>This project has no database-backed test infrastructure (no Testcontainers,
 * no embedded Postgres), so this test verifies the entity mapping configuration
 * itself — the exact annotations that were wrong — plus that the Java enum
 * values match the PostgreSQL enum definition from V1__init.sql
 * ({@code create type notification_type as enum ('EXPIRING_SOON', 'EXPIRED')}).
 */
class NotificationTypeMappingTest {

    private final Field typeField = typeField();

    @Test
    void typeUsesPostgresNamedEnumJdbcType() {
        JdbcTypeCode jdbcTypeCode = typeField.getAnnotation(JdbcTypeCode.class);
        assertThat(jdbcTypeCode).isNotNull();
        assertThat(jdbcTypeCode.value()).isEqualTo(SqlTypes.NAMED_ENUM);
    }

    @Test
    void typeColumnNamesThePostgresEnumType() {
        Column column = typeField.getAnnotation(Column.class);
        assertThat(column).isNotNull();
        assertThat(column.columnDefinition()).isEqualTo("notification_type");
        assertThat(column.name()).isEqualTo("type");
        assertThat(column.nullable()).isFalse();
    }

    @Test
    void typeUsesStringEnumTypeNameAsFallback() {
        Enumerated enumerated = typeField.getAnnotation(Enumerated.class);
        assertThat(enumerated).isNotNull();
        assertThat(enumerated.value()).isEqualTo(EnumType.STRING);
    }

    @Test
    void javaEnumValuesMatchPostgresEnumValues() {
        assertThat(NotificationType.values())
                .containsExactly(NotificationType.EXPIRING_SOON, NotificationType.EXPIRED);
    }

    private static Field typeField() {
        try {
            Field field = Notification.class.getDeclaredField("type");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ex) {
            throw new AssertionError("Notification entity has no 'type' field", ex);
        }
    }
}
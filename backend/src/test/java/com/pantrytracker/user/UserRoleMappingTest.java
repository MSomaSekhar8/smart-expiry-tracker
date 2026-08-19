package com.pantrytracker.user;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.lang.reflect.Field;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

/**
 * Regression test for the production bug where Hibernate sent the {@code UserRole}
 * enum as VARCHAR into the PostgreSQL named enum column {@code users.role}
 * (type {@code user_role}).
 *
 * <p>This project has no database-backed test infrastructure (no Testcontainers,
 * no embedded Postgres), so this test verifies the entity mapping configuration
 * itself — the exact annotations that were wrong — plus that the Java enum
 * values match the PostgreSQL enum definition from V1__init.sql
 * ({@code create type user_role as enum ('USER', 'ADMIN')}).
 *
 * <p>Real round-trip persistence against Supabase Postgres is verified manually
 * via register/login (see the fix report).
 */
class UserRoleMappingTest {

    private final Field roleField = roleField();

    @Test
    void roleUsesPostgresNamedEnumJdbcType() {
        JdbcTypeCode jdbcTypeCode = roleField.getAnnotation(JdbcTypeCode.class);
        assertThat(jdbcTypeCode).isNotNull();
        assertThat(jdbcTypeCode.value()).isEqualTo(SqlTypes.NAMED_ENUM);
    }

    @Test
    void roleColumnNamesThePostgresEnumType() {
        Column column = roleField.getAnnotation(Column.class);
        assertThat(column).isNotNull();
        assertThat(column.columnDefinition()).isEqualTo("user_role");
        assertThat(column.name()).isEqualTo("role");
        assertThat(column.nullable()).isFalse();
    }

    @Test
    void roleUsesStringEnumTypeNameAsFallback() {
        Enumerated enumerated = roleField.getAnnotation(Enumerated.class);
        assertThat(enumerated).isNotNull();
        assertThat(enumerated.value()).isEqualTo(EnumType.STRING);
    }

    @Test
    void javaEnumValuesMatchPostgresEnumValues() {
        assertThat(UserRole.values()).containsExactly(UserRole.USER, UserRole.ADMIN);
    }

    @Test
    void roleDefaultsToUser() throws Exception {
        Object defaultValue = roleField.get(new User());
        assertThat(defaultValue).isEqualTo(UserRole.USER);
    }

    private static Field roleField() {
        try {
            Field field = User.class.getDeclaredField("role");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ex) {
            throw new AssertionError("User entity has no 'role' field", ex);
        }
    }
}
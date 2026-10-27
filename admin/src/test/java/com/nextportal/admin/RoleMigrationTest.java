package com.nextportal.admin;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.*;

class RoleMigrationTest {
    @Test void upgradesExistingAdminAndUserWithoutLosingAccounts() throws Exception {
        var url = "jdbc:h2:mem:role-migration;MODE=MariaDB;DB_CLOSE_DELAY=-1";
        // H2's IN check constraint retains its creating session; keep that connection alive through the upgrade.
        var dataSource = new SingleConnectionDataSource(url, "sa", "", true);
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            Flyway.configure().dataSource(dataSource).target("1").load().migrate();
            statement.executeUpdate("""
                INSERT INTO portal_member (email, name, password_hash, role, status, created_at) VALUES
                  ('admin@example.com', 'admin', 'preserved-admin-hash', 'ADMIN', 'ACTIVE', CURRENT_TIMESTAMP),
                  ('user@example.com', 'user', 'preserved-user-hash', 'USER', 'DISABLED', CURRENT_TIMESTAMP)
                """);
            Flyway.configure().dataSource(dataSource).load().migrate();
            try (var rows = statement.executeQuery("SELECT m.email, m.password_hash, m.status, m.auth_version, r.role_code FROM portal_member m JOIN portal_member_role r ON r.member_id = m.id ORDER BY m.id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("role_code")).isEqualTo("ADMIN");
                assertThat(rows.getString("password_hash")).isEqualTo("preserved-admin-hash");
                assertThat(rows.getLong("auth_version")).isEqualTo(1);
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("role_code")).isEqualTo("USER");
                assertThat(rows.getString("password_hash")).isEqualTo("preserved-user-hash");
                assertThat(rows.getString("status")).isEqualTo("DISABLED");
                assertThat(rows.next()).isFalse();
            }
        } finally { dataSource.destroy(); }
    }
}

package com.lingxi.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway 迁移骨架测试：验证 V1/V2 脚本在全新 H2（MODE=MySQL）上可完整执行，
 * 且可重复执行（幂等）。同时校验新表与章节软删除列已创建。
 */
class FlywayMigrationTest {

    private static String freshUrl(String name) {
        return "jdbc:h2:mem:" + name + "_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    }

    private Flyway flyway(String url) {
        return Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load();
    }

    @Test
    void migratesFreshDatabase() throws Exception {
        String url = freshUrl("lingxi_fresh");
        MigrateResult result = flyway(url).migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isPositive();

        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement st = conn.createStatement()) {
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = st.executeQuery(
                    "select table_name from information_schema.tables where lower(table_name) in "
                            + "('users','novels','chapters','chapter_versions','chapter_summaries','story_memories')")) {
                while (rs.next()) {
                    tables.add(rs.getString(1).toLowerCase());
                }
            }
            assertThat(tables).contains("users", "novels", "chapters",
                    "chapter_versions", "chapter_summaries", "story_memories");

            // 章节软删除列存在
            boolean hasDeleted = false;
            try (ResultSet rs = st.executeQuery(
                    "select column_name from information_schema.columns "
                            + "where lower(table_name) = 'chapters' and lower(column_name) = 'deleted'")) {
                hasDeleted = rs.next();
            }
            assertThat(hasDeleted).isTrue();
        }
    }

    @Test
    void migrationIsIdempotent() {
        String url = freshUrl("lingxi_idem");
        MigrateResult first = flyway(url).migrate();
        assertThat(first.migrationsExecuted).isPositive();
        // 第二次 migrate 不应再应用任何脚本
        MigrateResult second = flyway(url).migrate();
        assertThat(second.migrationsExecuted).isZero();
    }
}

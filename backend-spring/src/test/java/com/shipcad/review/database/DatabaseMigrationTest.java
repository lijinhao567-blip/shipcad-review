package com.shipcad.review.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class DatabaseMigrationTest {
    private static final String LOCATIONS = "classpath:db/migration/h2";

    @Test
    void createsAndValidatesSchemaFromAnEmptyDatabase() throws Exception {
        String url = databaseUrl("empty");
        Flyway flyway = flyway(url, false);

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            assertThat(singleInt(statement, """
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name <> 'flyway_schema_history'
                    """)).isEqualTo(16);
            assertThat(singleString(statement, """
                    SELECT data_type
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'app_user'
                      AND column_name = 'role'
                    """)).isEqualTo("character varying");
            assertThat(singleInt(statement, """
                    SELECT COUNT(*)
                    FROM information_schema.table_constraints
                    WHERE table_schema = 'public'
                      AND constraint_type = 'FOREIGN KEY'
                    """)).isGreaterThanOrEqualTo(17);
        }
    }

    @Test
    void damengRuntimeAdaptersLoadWithTheManagedHibernateVersion() throws Exception {
        Class.forName("dm.jdbc.driver.DmDriver");
        Class<?> dialectType = Class.forName("org.hibernate.dialect.DmDialect");
        Object dialect = dialectType.getDeclaredConstructor().newInstance();

        assertThat((Integer) dialectType.getMethod("getMaxVarcharLength").invoke(dialect))
                .isGreaterThan(0);
    }

    @Test
    void adoptsANonEmptyLegacySchemaAndReappliesHardening() throws Exception {
        String url = databaseUrl("legacy");
        Flyway initial = flyway(url, false);
        initial.migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE app_user DROP CONSTRAINT uk_app_user_username");
            statement.execute("""
                    ALTER TABLE app_user ALTER COLUMN role
                    ENUM('ADMIN', 'REVIEW_EXPERT', 'DESIGN_ENGINEER', 'VIEWER')
                    """);
            statement.execute("DROP TABLE flyway_schema_history");
        }

        Flyway adopted = flyway(url, true);
        assertThat(adopted.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(adopted.info().current().getVersion().getVersion()).isEqualTo("2");

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            assertThat(singleString(statement, """
                    SELECT data_type
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'app_user'
                      AND column_name = 'role'
                    """)).isEqualTo("character varying");
            assertThat(singleInt(statement, """
                    SELECT COUNT(*)
                    FROM information_schema.table_constraints
                    WHERE table_schema = 'public'
                      AND table_name = 'app_user'
                      AND constraint_name = 'uk_app_user_username'
                      AND constraint_type = 'UNIQUE'
                    """)).isEqualTo(1);
        }
    }

    private Flyway flyway(String url, boolean baselineOnMigrate) {
        return Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .load();
    }

    private String databaseUrl(String label) {
        return "jdbc:h2:mem:migration-" + label + "-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    }

    private int singleInt(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private String singleString(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}

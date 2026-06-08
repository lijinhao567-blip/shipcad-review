package com.shipcad.review.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class DatabaseMigrationTest {
    private static final String LOCATIONS = "classpath:db/migration/h2";
    private static final Pattern MIGRATION_FILE = Pattern.compile("V(\\d+)__.+\\.sql");

    @Test
    void createsAndValidatesSchemaFromAnEmptyDatabase() throws Exception {
        String url = databaseUrl("empty");
        Flyway flyway = flyway(url, false);

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(5);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("5");

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
            assertThat(singleString(statement, """
                    SELECT data_type
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'parsed_entity'
                      AND column_name = 'cad_handle'
                    """)).isEqualTo("character varying");
            assertThat(singleString(statement, """
                    SELECT data_type
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'review_evidence'
                      AND column_name = 'location_json'
                    """)).isEqualTo("character large object");
            assertThat(singleInt(statement, """
                    SELECT COUNT(*)
                    FROM information_schema.table_constraints
                    WHERE table_schema = 'public'
                      AND constraint_type = 'FOREIGN KEY'
                    """)).isGreaterThanOrEqualTo(17);
            assertThat(singleString(statement, """
                    SELECT data_type
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'drawing_version'
                      AND column_name = 'file_object_key'
                    """)).isEqualTo("character varying");
            assertThat(singleString(statement, """
                    SELECT data_type
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'report_document'
                      AND column_name = 'content_object_key'
                    """)).isEqualTo("character varying");
        }
    }

    @Test
    void damengRuntimeAdaptersLoadWithTheManagedHibernateVersion() throws Exception {
        Class.forName("dm.jdbc.driver.DmDriver");
        ShipCadDmDialect dialect = new ShipCadDmDialect();

        assertThat(dialect.getMaxVarcharLength()).isGreaterThan(0);
        assertThat(dialect.getSequenceSupport().supportsSequences()).isFalse();
        assertThat(dialect.getQuerySequencesString()).isNull();
    }

    @Test
    void damengMigrationScriptsStayInStepWithH2Migrations() throws Exception {
        Path h2Dir = Path.of("src/main/resources/db/migration/h2");
        Path dm8Dir = Path.of("../deploy/database/dm8");
        List<Path> h2Scripts = migrationScripts(h2Dir);
        List<Path> dm8Scripts = migrationScripts(dm8Dir);

        assertThat(dm8Scripts.stream().map(this::migrationVersion).toList())
                .containsExactlyElementsOf(h2Scripts.stream().map(this::migrationVersion).toList());
        for (Path script : dm8Scripts) {
            int version = migrationVersion(script);
            String fileName = script.getFileName().toString();
            String sql = Files.readString(script);
            assertThat(sql)
                    .contains("INSERT INTO shipcad_schema_version(version_no, script_name, installed_at)")
                    .contains("VALUES (" + version + ", '" + fileName + "'");
        }
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
        assertThat(adopted.migrate().migrationsExecuted).isEqualTo(5);
        assertThat(adopted.info().current().getVersion().getVersion()).isEqualTo("5");

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
            assertThat(singleInt(statement, """
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'drawing_version'
                      AND column_name = 'storage_mode'
                    """)).isEqualTo(1);
            assertThat(singleInt(statement, """
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'report_document'
                      AND column_name = 'content_path'
                    """)).isEqualTo(1);
            assertThat(singleInt(statement, """
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'review_evidence'
                      AND column_name = 'location_json'
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

    private List<Path> migrationScripts(Path directory) throws Exception {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(path -> MIGRATION_FILE.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(this::migrationVersion))
                    .toList();
        }
    }

    private int migrationVersion(Path path) {
        Matcher matcher = MIGRATION_FILE.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a migration file: " + path);
        }
        return Integer.parseInt(matcher.group(1));
    }
}

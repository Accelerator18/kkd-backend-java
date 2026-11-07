package ru.kkd.integration;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.DirectoryResourceAccessor;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class MigrationIT {
    @Test void updateRollbackAndUpdateAgainOnRealPostgres() throws Exception {
        String schema="kkd_migration_" + UUID.randomUUID().toString().replace("-","");
        String url=System.getenv().getOrDefault("IT_DB_URL","jdbc:postgresql://localhost:5433/kkd");
        String user=System.getenv().getOrDefault("IT_DB_USER","kkd");
        String password=System.getenv().getOrDefault("IT_DB_PASSWORD","kkd_local");
        try {
            try (Connection connection=DriverManager.getConnection(url,user,password)) {
                try (var statement=connection.createStatement()) { statement.execute("CREATE SCHEMA " + schema); }
                connection.setSchema(schema);
                var database=DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
                database.setDefaultSchemaName(schema);
                database.setLiquibaseSchemaName(schema);
                try (Liquibase liquibase=new Liquibase("db/changelog/db.changelog-master.yaml",new ClassLoaderResourceAccessor(),database)) {
                    liquibase.update(new Contexts(),new LabelExpression());
                    assertCounts(connection);
                    var filesystem=new Liquibase("src/main/resources/db/changelog/db.changelog-master.yaml",
                            new DirectoryResourceAccessor(Path.of(".")),database);
                    filesystem.update(new Contexts(),new LabelExpression());
                    try (var statement = connection.createStatement()) {
                        statement.executeUpdate("INSERT INTO org_unit(id,name) VALUES ('CUSTOM','Другое подразделение')");
                        statement.executeUpdate("INSERT INTO kkd_run(id,snapshot_date,status) VALUES ('CUSTOM',CURRENT_DATE,'COMPLETED')");
                    }
                    database.commit();
                    filesystem.rollback(1, new Contexts(), new LabelExpression());
                    try (var statement = connection.createStatement();
                         var rows = statement.executeQuery("SELECT id FROM org_unit UNION ALL SELECT id FROM kkd_run")) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString(1)).isEqualTo("CUSTOM");
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString(1)).isEqualTo("CUSTOM");
                        assertThat(rows.next()).isFalse();
                    }
                    filesystem.update(new Contexts(), new LabelExpression());
                    filesystem.rollback(2,new Contexts(),new LabelExpression());
                    try (var statement=connection.createStatement(); var rs=statement.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema='" + schema + "' AND table_name NOT IN ('databasechangelog','databasechangeloglock')")) {
                        rs.next(); assertThat(rs.getInt(1)).isZero();
                    }
                    liquibase.update(new Contexts(),new LabelExpression());
                    assertCounts(connection);
                    liquibase.update(new Contexts(),new LabelExpression());
                    assertCounts(connection);
                }
            }
        } finally {
            try (var c=DriverManager.getConnection(url,user,password); var s=c.createStatement()) {
                s.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
    private void assertCounts(Connection connection) throws Exception {
        for (var expected : java.util.Map.of("org_unit",3,"goal",24,"key_result",48,"kkd_rule",4).entrySet()) {
            try (var s=connection.createStatement(); var rs=s.executeQuery("SELECT count(*) FROM " + expected.getKey())) {
                rs.next(); assertThat(rs.getInt(1)).isEqualTo(expected.getValue());
            }
        }
    }
}

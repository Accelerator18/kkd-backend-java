package ru.kkd.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.kkd.service.DemoSeedService;
import ru.kkd.service.RunService;
import java.time.LocalDate;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostgresIT {
    private static final String SCHEMA="kkd_it_" + UUID.randomUUID().toString().replace("-","");
    private static final String URL=System.getenv().getOrDefault("IT_DB_URL","jdbc:postgresql://localhost:5433/kkd");
    private static final String USER=System.getenv().getOrDefault("IT_DB_USER","kkd");
    private static final String PASSWORD=System.getenv().getOrDefault("IT_DB_PASSWORD","kkd_local");

    @DynamicPropertySource static void datasource(DynamicPropertyRegistry registry) throws Exception {
        try (var connection=DriverManager.getConnection(URL,USER,PASSWORD); var statement=connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }
        registry.add("spring.datasource.url",() -> URL + (URL.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username",() -> USER);
        registry.add("spring.datasource.password",() -> PASSWORD);
        registry.add("app.seed-demo",() -> "true");
    }
    @AfterAll static void cleanup() throws Exception {
        try (var connection=DriverManager.getConnection(URL,USER,PASSWORD); var statement=connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DemoSeedService seed;
    @Autowired RunService runs;
    @Autowired ObjectMapper json;

    @Test void demoHasRequiredDataAndInitializationIsIdempotent() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM goal",Integer.class)).isEqualTo(24);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM org_unit",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT rule_code) FROM kkd_issue",Integer.class)).isEqualTo(4);
        var counts = jdbc.queryForList(
                "SELECT count(DISTINCT rule_code) FROM kkd_issue GROUP BY run_id ORDER BY run_id", Integer.class);
        assertThat(counts).containsExactly(4, 4);
        seed.seed();
        mvc.perform(get("/api/runs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value("RUN-002"))
                .andExpect(jsonPath("$.items[0].issues_count").value(11))
                .andExpect(jsonPath("$.items[0].compared_to.id").value("RUN-001"));
    }
    @Test void summaryIncludesChildrenAndRealComparison() throws Exception {
        mvc.perform(get("/api/kkd/summary").param("run_id","RUN-002").param("unit_id","HQ"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.metrics[0].value").value(47.6))
                .andExpect(jsonPath("$.metrics[0].previous").value(42.9))
                .andExpect(jsonPath("$.metrics[0].delta").value(4.7))
                .andExpect(jsonPath("$.metrics[0].trend").value("better"))
                .andExpect(jsonPath("$.metrics[1].delta").value(-1))
                .andExpect(jsonPath("$.metrics[1].trend").value("better"))
                .andExpect(jsonPath("$.delta.new").value(4)).andExpect(jsonPath("$.delta.persisting").value(7))
                .andExpect(jsonPath("$.delta.resolved").value(5))
                .andExpect(jsonPath("$.issues_by_severity[0].current").value(2))
                .andExpect(jsonPath("$.issues_by_severity[1].current").value(6))
                .andExpect(jsonPath("$.issues_by_severity[2].current").value(3));
    }
    @Test void firstRunHasJsonNullInsteadOfZero() throws Exception {
        String body=mvc.perform(get("/api/kkd/summary").param("run_id","RUN-001").param("unit_id","HQ"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.metrics[0].value").value(42.9))
                .andReturn().getResponse().getContentAsString();
        var node = json.readTree(body);
        assertThat(node.get("compared_to").isNull()).isTrue();
        assertThat(node.get("delta").isNull()).isTrue();
        for (var metric : node.get("metrics")) {
            assertThat(metric.get("previous").isNull()).isTrue();
            assertThat(metric.get("delta").isNull()).isTrue();
            assertThat(metric.get("trend").isNull()).isTrue();
        }
        for (var level : node.get("issues_by_severity")) {
            assertThat(level.get("previous").isNull()).isTrue();
            assertThat(level.get("delta").isNull()).isTrue();
        }
    }
    @Test void issueFiltersPaginationAndResolvedUseConsistentTotals() throws Exception {
        mvc.perform(get("/api/kkd/issues").param("run_id","RUN-002").param("unit_id","CIB")
                        .param("rule_code","CASCADE_NO_PARENT").param("severity","major").param("limit","1").param("offset","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].object_id").value("G-010"));
        mvc.perform(get("/api/kkd/issues").param("run_id","RUN-002").param("delta_status","resolved"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.items[0].delta_status").value("resolved"));
        mvc.perform(get("/api/kkd/issues").param("run_id","RUN-002").param("offset","100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.total").value(11));
    }
    @Test void liveChangesDoNotChangeHistoricalMetrics() throws Exception {
        jdbc.update("UPDATE goal SET owner_name='Новый ответственный'");
        jdbc.update("UPDATE key_result SET weight=50,fact_updated_at=DATE '2026-11-02'");
        jdbc.update("UPDATE org_unit SET parent_id=NULL WHERE id='CIB'");
        mvc.perform(get("/api/kkd/summary").param("run_id","RUN-002").param("unit_id","HQ"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.metrics[1].value").value(11));
    }
    @Test void unitMustExistInRequestedSnapshot() throws Exception {
        jdbc.update("INSERT INTO org_unit(id,name,parent_id) VALUES ('EMPTY','Пустое','HQ')");
        for (String endpoint : new String[]{"/api/kkd/summary", "/api/kkd/issues"}) {
            mvc.perform(get(endpoint).param("run_id", "RUN-002").param("unit_id", "EMPTY"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("UNIT_NOT_FOUND"));
        }

        String run = runs.create(LocalDate.of(2026, 11, 9)).id();
        jdbc.update("DELETE FROM org_unit WHERE id='EMPTY'");
        var response = mvc.perform(get("/api/kkd/summary").param("run_id", run).param("unit_id", "EMPTY"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unit.name").value("Пустое"))
                .andExpect(jsonPath("$.metrics[1].value").value(0))
                .andExpect(jsonPath("$.metrics[2].value").value(0))
                .andReturn().getResponse();
        var summary = json.readTree(response.getContentAsByteArray());
        assertThat(summary.at("/metrics/0/value").isNull()).isTrue();
        assertThat(summary.at("/metrics/1/previous").isNull()).isTrue();
        assertThat(summary.at("/issues_by_severity/0/previous").isNull()).isTrue();
    }

    @Test void movingGoalKeepsIssueIdentityAcrossUnitFilters() throws Exception {
        jdbc.update("UPDATE goal SET unit_id='SME' WHERE id='G-011'");
        String run = runs.create(LocalDate.of(2026, 11, 9)).id();
        mvc.perform(get("/api/kkd/issues").param("run_id", run).param("unit_id", "SME")
                        .param("rule_code", "GOAL_NO_OWNER").param("delta_status", "persisting"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].object_id").value("G-011"));
        mvc.perform(get("/api/kkd/issues").param("run_id", run).param("unit_id", "CIB")
                        .param("rule_code", "GOAL_NO_OWNER").param("delta_status", "resolved"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/kkd/summary").param("run_id", run).param("unit_id", "SME"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.delta.new").value(0))
                .andExpect(jsonPath("$.delta.persisting").value(5));
        mvc.perform(get("/api/kkd/summary").param("run_id", run).param("unit_id", "CIB"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.delta.resolved").value(0));
    }

    @Test void historicalSeverityIsReadFromStoredIssue() throws Exception {
        jdbc.update("UPDATE kkd_issue SET severity='minor' WHERE run_id='RUN-001' AND rule_code='GOAL_NO_OWNER'");
        mvc.perform(get("/api/kkd/summary").param("run_id", "RUN-002").param("unit_id", "HQ"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.issues_by_severity[0].previous").value(0))
                .andExpect(jsonPath("$.issues_by_severity[2].previous").value(6));
        mvc.perform(get("/api/kkd/issues").param("run_id", "RUN-001")
                        .param("rule_code", "GOAL_NO_OWNER").param("severity", "minor"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3));
    }

    @Test void apiSupportsContextPathAndUsesJsonForErrors() throws Exception {
        mvc.perform(get("/tracker/api/kkd/issues").contextPath("/tracker").param("run_id", "RUN-002"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(11));
        mvc.perform(get("/api/runs").accept("text/plain"))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error.code").value("NOT_ACCEPTABLE"));
    }
    @Test void errorsHaveExpectedStatusAndUniformEnvelope() throws Exception {
        mvc.perform(get("/api/kkd/summary").param("run_id","UNKNOWN").param("unit_id","HQ"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RUN_NOT_FOUND"));
        mvc.perform(get("/api/kkd/summary").param("run_id","RUN-002").param("unit_id","UNKNOWN"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("UNIT_NOT_FOUND"));
        for (String bad : new String[]{"0","-1","101","1.5","abc","2147483648","1 0","0x10"}) {
            mvc.perform(get("/api/kkd/issues").param("run_id","RUN-002").param("limit",bad))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(get("/api/kkd/issues")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/kkd/issues").param("run_id"," ")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/kkd/issues").param("run_id","RUN-002").param("run_id","RUN-001"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/kkd/issues").param("run_id","RUN-002").param("severity","CRITICAL"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/kkd/issues").param("run_id","RUN-002").param("rule_code","UNKNOWN"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/kkd/issues").param("run_id","RUN-002").param("delta_status","other"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/kkd/issues").param("run_id","RUN-002").param("unknown","x"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/runs").contentType("application/json").content("{\"snapshot_date\":\"2026-13-45\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/runs").contentType("application/json").content("{\"snapshot_date\":\"2026-11-09\",\"unknown\":1}"))
                .andExpect(status().isUnprocessableEntity());
    }
    @ParameterizedTest
    @ValueSource(strings = {
            "[2026,11,9]", "\"2026-11-09T00:00:00\"", "\"+999999999-12-31\"", "\"2027-02-29\""
    })
    void rejectsMalformedSnapshotDateBeforeCreatingRun(String value) throws Exception {
        mvc.perform(post("/api/runs").contentType("application/json").content("{\"snapshot_date\":" + value + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM kkd_run", Integer.class)).isEqualTo(2);
    }

    @Test void failedAndRunningRunsDoNotReturnPartialResults() throws Exception {
        jdbc.update("INSERT INTO kkd_run(id,snapshot_date,status) VALUES ('FAILED',DATE '2026-11-03','FAILED'),('RUNNING',DATE '2026-11-04','RUNNING')");
        mvc.perform(get("/api/kkd/summary").param("run_id","FAILED").param("unit_id","HQ"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RUN_FAILED"));
        mvc.perform(get("/api/kkd/issues").param("run_id","RUNNING"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RUN_IN_PROGRESS"));
    }
    @Test void newRunSkipsFailedRunAndChecksLiveData() throws Exception {
        jdbc.update("INSERT INTO kkd_run(id,snapshot_date,status) VALUES ('FAILED',DATE '2026-11-03','FAILED')");
        jdbc.update("UPDATE goal SET owner_name='Исправлено' WHERE id='G-011'");
        mvc.perform(post("/api/runs").contentType("application/json").content("{\"snapshot_date\":\"2026-11-09\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.compared_to.id").value("RUN-002"))
                .andExpect(jsonPath("$.issues_count").value(10));
        mvc.perform(post("/api/runs").contentType("application/json").content("{\"snapshot_date\":\"2026-11-02\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
    @Test void metaAndIssueDetailsAreAvailable() throws Exception {
        mvc.perform(get("/api/meta")).andExpect(status().isOk()).andExpect(jsonPath("$.rules.length()").value(4));
        String id=jdbc.queryForObject("SELECT min(id) FROM kkd_issue WHERE run_id='RUN-002'",String.class);
        mvc.perform(get("/api/kkd/issues/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.recommendation").isNotEmpty());
        mvc.perform(get("/api/kkd/issues/unknown")).andExpect(status().isNotFound());
        mvc.perform(get("/api/no-such-endpoint")).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("ENDPOINT_NOT_FOUND"));
        mvc.perform(put("/api/runs")).andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }
}

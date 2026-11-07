package ru.kkd.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.kkd.domain.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

@Repository
public class KkdRepository {
    private final JdbcTemplate jdbc;
    public KkdRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void lockRuns() { jdbc.execute("SELECT pg_advisory_xact_lock(736211)"); }
    public void lockSource() { jdbc.execute("LOCK TABLE org_unit, goal, key_result IN SHARE MODE"); }
    public long runCount() { return Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM kkd_run", Long.class)); }

    public List<OrgUnit> liveUnits() {
        return jdbc.query("SELECT id, name, parent_id FROM org_unit ORDER BY id", (rs, n) -> unit(rs));
    }
    public Snapshot liveSnapshot(LocalDate date) { return readSnapshot(date, null); }
    public Snapshot snapshot(Run run) { return readSnapshot(run.snapshotDate(), run.id()); }

    private Snapshot readSnapshot(LocalDate date, String runId) {
        boolean live = runId == null;
        Object[] args = live ? new Object[0] : new Object[]{runId};
        String where = live ? "" : " WHERE run_id=?";
        List<OrgUnit> units = jdbc.query("SELECT id, name, parent_id FROM " + (live ? "org_unit" : "run_unit")
                + where + " ORDER BY id", (rs,n) -> unit(rs), args);
        Map<String,List<KeyResult>> results = new HashMap<>();
        jdbc.query("SELECT * FROM " + (live ? "key_result" : "run_key_result") + where + " ORDER BY id", rs -> {
            results.computeIfAbsent(rs.getString("goal_id"), k -> new ArrayList<>()).add(new KeyResult(
                    rs.getString("id"), rs.getBigDecimal("target_value"), rs.getBigDecimal("actual_value"),
                    rs.getBigDecimal("weight"), rs.getObject("fact_updated_at", LocalDate.class)));
        }, args);
        List<Goal> goals = jdbc.query("SELECT * FROM " + (live ? "goal" : "run_goal") + where + " ORDER BY id", (rs,n) ->
                new Goal(rs.getString("id"), rs.getString("unit_id"), rs.getString("owner_name"),
                        rs.getString("parent_goal_id"), GoalStatus.valueOf(rs.getString("status")),
                        rs.getObject("due_date", LocalDate.class), results.getOrDefault(rs.getString("id"), List.of())), args);
        return new Snapshot(date, units, goals);
    }

    public void insertRun(String id, LocalDate date, String comparedTo) {
        jdbc.update("INSERT INTO kkd_run(id,snapshot_date,status,compared_to) VALUES (?,?,'RUNNING',?)", id, Date.valueOf(date), comparedTo);
    }
    public void completeRun(String id) { jdbc.update("UPDATE kkd_run SET status='COMPLETED' WHERE id=?", id); }
    public void saveSnapshot(String runId, Snapshot snapshot) {
        jdbc.batchUpdate("INSERT INTO run_unit(run_id,id,name,parent_id) VALUES (?,?,?,?)", snapshot.units().stream()
                .map(u -> new Object[]{runId,u.id(),u.name(),u.parentId()}).toList());
        jdbc.batchUpdate("INSERT INTO run_goal(run_id,id,unit_id,owner_name,parent_goal_id,status,due_date) VALUES (?,?,?,?,?,?,?)",
                snapshot.goals().stream().map(g -> new Object[]{runId,g.id(),g.unitId(),g.ownerName(),g.parentGoalId(),g.status().name(),Date.valueOf(g.dueDate())}).toList());
        List<Object[]> krRows = new ArrayList<>();
        for (Goal g : snapshot.goals()) for (KeyResult kr : g.keyResults()) krRows.add(new Object[]{runId,kr.id(),g.id(),
                kr.targetValue(),kr.actualValue(),kr.weight(),kr.factUpdatedAt() == null ? null : Date.valueOf(kr.factUpdatedAt())});
        jdbc.batchUpdate("INSERT INTO run_key_result(run_id,id,goal_id,target_value,actual_value,weight,fact_updated_at) VALUES (?,?,?,?,?,?,?)", krRows);
    }
    public void saveIssues(List<Issue> issues) {
        jdbc.batchUpdate("INSERT INTO kkd_issue(id,run_id,rule_code,severity,object_type,object_id,goal_id,unit_id,message,actual_value,expected_value,recommendation) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                issues.stream().map(i -> {
                    IssueDraft d = i.data();
                    return new Object[]{i.id(),i.runId(),d.ruleCode().name(),i.severity().code(),d.objectType().name(),
                            d.objectId(),d.goalId(),d.unitId(),d.message(),d.actualValue(),d.expectedValue(),d.recommendation()};
                }).toList());
    }
    private static final String RUN_SELECT = """
            SELECT r.*, (SELECT count(*) FROM kkd_issue i WHERE i.run_id = r.id) AS issues_count
            FROM kkd_run r
            """;
    private static final String RUN_ORDER = " ORDER BY r.snapshot_date DESC, r.created_at DESC, r.id DESC";

    public List<Run> runs() {
        return jdbc.query(RUN_SELECT + RUN_ORDER, (rs, n) -> run(rs));
    }

    public Optional<Run> findRun(String id) {
        return jdbc.query(RUN_SELECT + " WHERE r.id = ?", (rs, n) -> run(rs), id).stream().findFirst();
    }

    public Optional<Run> latestCompleted() {
        return jdbc.query(RUN_SELECT + " WHERE r.status = 'COMPLETED'" + RUN_ORDER + " LIMIT 1",
                (rs, n) -> run(rs)).stream().findFirst();
    }
    public List<Issue> issues(String runId) {
        return jdbc.query("SELECT * FROM kkd_issue WHERE run_id=? ORDER BY id", (rs,n) -> issue(rs), runId);
    }
    public Optional<Issue> findIssue(String id) {
        return jdbc.query("SELECT * FROM kkd_issue WHERE id=?", (rs,n) -> issue(rs), id).stream().findFirst();
    }
    public void applySecondDemoState() {
        jdbc.update("UPDATE goal SET owner_name='Исправленный ответственный' WHERE id IN ('G-003','G-019')");
        jdbc.update("UPDATE goal SET owner_name=NULL WHERE id='G-005'");
        jdbc.update("UPDATE key_result SET weight=50 WHERE goal_id='G-012'");
        jdbc.update("UPDATE key_result SET weight=40 WHERE id='KR-021-1'");
        jdbc.update("UPDATE goal SET parent_goal_id='G-001' WHERE id='G-017'");
        jdbc.update("UPDATE goal SET parent_goal_id=NULL WHERE id='G-018'");
        jdbc.update("UPDATE key_result SET fact_updated_at=DATE '2026-10-30' WHERE id='KR-002-1'");
        jdbc.update("UPDATE key_result SET fact_updated_at=DATE '2026-09-01' WHERE id='KR-006-1'");
    }

    private static OrgUnit unit(ResultSet rs) throws SQLException {
        return new OrgUnit(rs.getString("id"),rs.getString("name"),rs.getString("parent_id"));
    }
    private static Run run(ResultSet rs) throws SQLException {
        return new Run(rs.getString("id"),rs.getObject("snapshot_date", LocalDate.class),RunStatus.valueOf(rs.getString("status")),
                rs.getString("compared_to"),rs.getTimestamp("created_at").toInstant(),rs.getLong("issues_count"));
    }
    private static Issue issue(ResultSet rs) throws SQLException {
        return new Issue(rs.getString("id"),rs.getString("run_id"), new IssueDraft(RuleCode.valueOf(rs.getString("rule_code")),
                ObjectType.valueOf(rs.getString("object_type")),rs.getString("object_id"),rs.getString("goal_id"),rs.getString("unit_id"),
                rs.getString("message"),rs.getString("actual_value"),rs.getString("expected_value"),rs.getString("recommendation")),
                Severity.valueOf(rs.getString("severity").toUpperCase(Locale.ROOT)));
    }
}

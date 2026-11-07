package ru.kkd.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.format.DateTimeParseException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ApiModels {
    private ApiModels() { }
    public record ErrorBody(ErrorDetail error) { }
    public record ErrorDetail(String code, String message) { }
    public record RunRef(String id, LocalDate snapshotDate) { }
    public record RunView(String id, String status, LocalDate snapshotDate, RunRef comparedTo, Long issuesCount, Instant createdAt) { }
    public record RunList(List<RunView> items) { }
    public record UnitView(String id, String name, String parentId) { }
    public record RuleView(String code, String severity, String title, String recommendation) { }
    public record Meta(List<UnitView> units, List<RuleView> rules, List<String> severities) { }
    public record Metric(String key, String title, String unit, BigDecimal value, BigDecimal previous,
                         BigDecimal delta, String trend, boolean higherIsBetter) { }
    public record SeverityCount(String code, long current, Long previous, Long delta) { }
    public record Delta(@JsonProperty("new") Integer newCount, Integer persisting, Integer resolved) { }
    public record Summary(RunRef run, RunRef comparedTo, UnitView unit, List<Metric> metrics,
                          List<SeverityCount> issuesBySeverity, Delta delta) { }
    public record IssueView(String id, String runId, String ruleCode, String severity, String objectType,
                            String objectId, String goalId, String unitId, String description,
                            String actualValue, String expectedValue, String recommendation, String deltaStatus) { }
    public record IssuePage(List<IssueView> items, long total, int limit, int offset) { }
    public record CreateRunRequest(@NotNull @Pattern(regexp = "(?!0000)[0-9]{4}-[0-9]{2}-[0-9]{2}") String snapshotDate) {
        public LocalDate date() {
            try {
                return LocalDate.parse(snapshotDate);
            } catch (DateTimeParseException ex) {
                throw ApiException.validation("Несуществующая дата снимка: " + snapshotDate);
            }
        }
    }
}

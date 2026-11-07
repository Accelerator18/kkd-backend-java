package ru.kkd.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.kkd.api.ApiException;
import ru.kkd.api.ApiModels.*;
import ru.kkd.domain.*;
import ru.kkd.repository.KkdRepository;
import java.util.*;

@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class KkdQueryService {
    private final KkdRepository repository;
    private final MetricCalculator metrics;
    private final DeltaCalculator delta;
    public KkdQueryService(KkdRepository repository, MetricCalculator metrics, DeltaCalculator delta) {
        this.repository=repository; this.metrics=metrics; this.delta=delta;
    }

    public RunList runs() { return new RunList(repository.runs().stream().map(this::runView).toList()); }
    public RunView runView(Run r) {
        return new RunView(r.id(),r.status().name(),r.snapshotDate(),reference(r.comparedTo()),
                r.status() == RunStatus.COMPLETED ? r.issuesCount() : null,r.createdAt());
    }
    public Meta meta() {
        return new Meta(repository.liveUnits().stream().map(u -> new UnitView(u.id(),u.name(),u.parentId())).toList(),
                Arrays.stream(RuleCode.values()).map(r -> new RuleView(r.name(),r.severity().code(),r.title(),r.recommendation())).toList(),
                Arrays.stream(Severity.values()).map(Severity::code).toList());
    }
    public Summary summary(String runId, String unitId) {
        Run run = availableRun(runId);
        Snapshot current = repository.snapshot(run);
        OrgUnit unit = requireUnit(current, unitId);
        Set<String> scope = UnitScope.descendants(current.units(), unitId);
        List<Issue> currentIssues = repository.issues(runId);
        List<Issue> issues = inScope(currentIssues, scope);

        Run beforeRun = run.comparedTo() == null ? null : availableRun(run.comparedTo());
        Snapshot before = beforeRun == null ? null : repository.snapshot(beforeRun);
        Set<String> beforeScope = before == null ? Set.of() : UnitScope.descendants(before.units(), unitId);
        List<Issue> previousIssues = beforeRun == null ? null : repository.issues(beforeRun.id());
        List<Issue> past = previousIssues == null ? List.of() : inScope(previousIssues, beforeScope);
        boolean hasPreviousUnit = !beforeScope.isEmpty();
        var comparison = delta.compare(currentIssues, previousIssues);
        List<SeverityCount> severities = Arrays.stream(Severity.values()).map(s -> {
            long count = issues.stream().filter(i -> i.severity() == s).count();
            Long previous = !hasPreviousUnit ? null : past.stream().filter(i -> i.severity() == s).count();
            return new SeverityCount(s.code(),count,previous,previous == null ? null : count-previous);
        }).toList();
        return new Summary(new RunRef(run.id(),run.snapshotDate()), beforeRun == null ? null : new RunRef(beforeRun.id(), beforeRun.snapshotDate()),
                new UnitView(unit.id(),unit.name(),unit.parentId()),
                metrics.compare(metrics.values(current,issues,scope),!hasPreviousUnit ? null : metrics.values(before,past,beforeScope)),
                severities, comparison.counts(issues, past));
    }
    public IssuePage issues(String runId, String unitId, String ruleCode, String severity,
                            String deltaStatus, int limit, int offset) {
        Run run = availableRun(runId);
        RuleCode rule = ruleCode == null ? null : parseRule(ruleCode);
        Severity level = severity == null ? null : parseSeverity(severity);
        if (deltaStatus != null && !Set.of("new","persisting","resolved").contains(deltaStatus))
            throw ApiException.validation("delta_status: допустимы new, persisting, resolved");
        Snapshot current = repository.snapshot(run);
        if (unitId != null) requireUnit(current, unitId);
        List<Issue> currentIssues = repository.issues(runId);
        List<Issue> previousIssues = run.comparedTo() == null ? null : repository.issues(run.comparedTo());
        var comparison = delta.compare(currentIssues, previousIssues);
        List<IssueView> candidates;
        if ("resolved".equals(deltaStatus)) {
            if (previousIssues == null) return new IssuePage(List.of(), 0, limit, offset);
            Snapshot before = repository.snapshot(availableRun(run.comparedTo()));
            Set<String> scope = UnitScope.descendants(before.units(), unitId);
            candidates = inScope(previousIssues, scope).stream().filter(comparison::resolved)
                    .map(issue -> view(issue, "resolved")).toList();
        } else {
            Set<String> scope = UnitScope.descendants(current.units(), unitId);
            candidates = inScope(currentIssues, scope).stream()
                    .map(issue -> view(issue, comparison.status(issue))).toList();
        }
        List<IssueView> filtered = candidates.stream().filter(i -> rule == null || i.ruleCode().equals(rule.name()))
                .filter(i -> level == null || i.severity().equals(level.code()))
                .filter(i -> deltaStatus == null || deltaStatus.equals(i.deltaStatus()))
                .sorted(Comparator.comparingInt((IssueView i) -> parseSeverity(i.severity()).ordinal())
                        .thenComparing(IssueView::ruleCode).thenComparing(IssueView::objectId).thenComparing(IssueView::id)).toList();
        return new IssuePage(filtered.stream().skip(offset).limit(limit).toList(),filtered.size(),limit,offset);
    }
    public IssueView issue(String id) {
        Issue issue = repository.findIssue(id).orElseThrow(() -> new ApiException(404,"ISSUE_NOT_FOUND","Проблема не найдена"));
        Run run = availableRun(issue.runId());
        List<Issue> previous = run.comparedTo() == null ? null : repository.issues(run.comparedTo());
        return view(issue, delta.compare(List.of(issue), previous).status(issue));
    }

    private static List<Issue> inScope(List<Issue> issues, Set<String> scope) {
        return issues.stream().filter(i -> scope.contains(i.data().unitId())).toList();
    }

    private static OrgUnit requireUnit(Snapshot snapshot, String id) {
        return snapshot.units().stream().filter(unit -> unit.id().equals(id)).findFirst()
                .orElseThrow(() -> new ApiException(404, "UNIT_NOT_FOUND", "Подразделение не найдено: " + id));
    }

    private RunRef reference(String id) {
        if (id == null) return null;
        Run run = repository.findRun(id).orElseThrow(() -> new ApiException(404,"RUN_NOT_FOUND","Прогон не найден: " + id));
        return new RunRef(run.id(),run.snapshotDate());
    }
    private Run availableRun(String id) {
        Run run = repository.findRun(id).orElseThrow(() -> new ApiException(404,"RUN_NOT_FOUND","Прогон не найден: " + id));
        if (run.status() == RunStatus.FAILED) throw new ApiException(409,"RUN_FAILED","Прогон завершился с ошибкой");
        if (run.status() == RunStatus.RUNNING) throw new ApiException(409,"RUN_IN_PROGRESS","Прогон ещё выполняется");
        return run;
    }
    private static IssueView view(Issue issue, String status) {
        IssueDraft d = issue.data();
        return new IssueView(issue.id(),issue.runId(),d.ruleCode().name(),issue.severity().code(),d.objectType().name(),
                d.objectId(),d.goalId(),d.unitId(),d.message(),d.actualValue(),d.expectedValue(),d.recommendation(),status);
    }
    private static RuleCode parseRule(String value) {
        try { return RuleCode.valueOf(value); } catch (IllegalArgumentException e) { throw ApiException.validation("Неизвестное правило: " + value); }
    }
    private static Severity parseSeverity(String value) {
        return Arrays.stream(Severity.values()).filter(s -> s.code().equals(value)).findFirst()
                .orElseThrow(() -> ApiException.validation("severity: допустимы critical, major, minor"));
    }
}

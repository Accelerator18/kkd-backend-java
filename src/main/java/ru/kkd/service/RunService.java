package ru.kkd.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kkd.api.ApiException;
import ru.kkd.domain.*;
import ru.kkd.repository.KkdRepository;
import ru.kkd.rule.RuleEngine;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class RunService {
    private final KkdRepository repository;
    private final RuleEngine engine;
    public RunService(KkdRepository repository, RuleEngine engine) { this.repository=repository; this.engine=engine; }

    @Transactional
    public Run create(LocalDate date) {
        repository.lockRuns();
        Run previous = repository.latestCompleted().orElse(null);
        if (previous != null && !date.isAfter(previous.snapshotDate()))
            throw ApiException.validation("snapshot_date должна быть позже последнего успешного прогона: " + previous.snapshotDate());
        repository.lockSource();
        return persist("RUN-" + UUID.randomUUID(), date, previous == null ? null : previous.id());
    }

    Run persist(String id, LocalDate date, String comparedTo) {
        Snapshot snapshot = repository.liveSnapshot(date);
        List<Issue> issues = engine.evaluate(snapshot).stream().map(d -> {
            String key = id + "|" + d.ruleCode() + "|" + d.objectType() + "|" + d.objectId();
            return new Issue(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString(), id, d);
        }).toList();
        repository.insertRun(id,date,comparedTo);
        repository.saveSnapshot(id,snapshot);
        repository.saveIssues(issues);
        repository.completeRun(id);
        return repository.findRun(id).orElseThrow();
    }
}

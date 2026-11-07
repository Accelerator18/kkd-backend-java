package ru.kkd.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kkd.repository.KkdRepository;
import java.time.LocalDate;

@Service
public class DemoSeedService {
    private final KkdRepository repository;
    private final RunService runs;
    public DemoSeedService(KkdRepository repository, RunService runs) { this.repository=repository; this.runs=runs; }
    @Transactional
    public void seed() {
        repository.lockRuns();
        if (repository.runCount() > 0) return;
        repository.lockSource();
        runs.persist("RUN-001", LocalDate.of(2026,10,26), null);
        repository.applySecondDemoState();
        runs.persist("RUN-002", LocalDate.of(2026,11,2), "RUN-001");
    }
}

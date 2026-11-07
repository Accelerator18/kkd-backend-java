package ru.kkd.domain;
import java.time.Instant;
import java.time.LocalDate;
public record Run(String id, LocalDate snapshotDate, RunStatus status, String comparedTo,
                  Instant createdAt, long issuesCount) { }

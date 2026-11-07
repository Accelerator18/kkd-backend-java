package ru.kkd.domain;
import java.time.LocalDate;
import java.util.List;
public record Snapshot(LocalDate date, List<OrgUnit> units, List<Goal> goals) {
    public Snapshot { units = List.copyOf(units); goals = List.copyOf(goals); }
}

package ru.kkd.domain;
import java.time.LocalDate;
import java.util.List;
public record Goal(String id, String unitId, String ownerName, String parentGoalId,
                   GoalStatus status, LocalDate dueDate, List<KeyResult> keyResults) {
    public Goal { keyResults = List.copyOf(keyResults); }
    public boolean active() { return status == GoalStatus.ACTIVE; }
}

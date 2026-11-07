package ru.kkd.rule;

import org.springframework.stereotype.Component;
import ru.kkd.domain.*;
import java.util.List;

@Component
public final class GoalNoOwnerRule implements KkdRule {
    public RuleCode code() { return RuleCode.GOAL_NO_OWNER; }
    public List<IssueDraft> check(Snapshot snapshot, Goal goal) {
        if (!goal.active() || (goal.ownerName() != null && !goal.ownerName().isBlank())) return List.of();
        return List.of(new IssueDraft(code(), ObjectType.GOAL, goal.id(), goal.id(), goal.unitId(),
                code().title(), goal.ownerName(), "Непустой ответственный", code().recommendation()));
    }
}

package ru.kkd.rule;

import org.springframework.stereotype.Component;
import ru.kkd.domain.*;
import java.util.List;
import java.util.Set;

@Component
public final class CascadeNoParentRule implements KkdRule {
    public RuleCode code() { return RuleCode.CASCADE_NO_PARENT; }
    public List<IssueDraft> check(Snapshot snapshot, Goal goal) {
        if (!goal.active()) return List.of();
        OrgUnit unit = snapshot.units().stream().filter(u -> u.id().equals(goal.unitId())).findFirst().orElseThrow();
        if (unit.parentId() == null) return List.of();
        Set<String> ancestors = UnitScope.ancestors(snapshot.units(), goal.unitId());
        boolean valid = snapshot.goals().stream().anyMatch(parent -> parent.id().equals(goal.parentGoalId())
                && ancestors.contains(parent.unitId()));
        if (valid) return List.of();
        return List.of(new IssueDraft(code(), ObjectType.GOAL, goal.id(), goal.id(), goal.unitId(),
                code().title(), goal.parentGoalId(), "Цель подразделения — строгого предка", code().recommendation()));
    }
}

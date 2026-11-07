package ru.kkd.rule;

import org.springframework.stereotype.Component;
import ru.kkd.domain.*;
import java.math.BigDecimal;
import java.util.List;

@Component
public final class KrWeightsSumRule implements KkdRule {
    public RuleCode code() { return RuleCode.KR_WEIGHTS_SUM; }
    public List<IssueDraft> check(Snapshot snapshot, Goal goal) {
        if (!goal.active()) return List.of();
        BigDecimal sum = goal.keyResults().stream().map(KeyResult::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(BigDecimal.valueOf(100)) == 0) return List.of();
        return List.of(new IssueDraft(code(), ObjectType.GOAL, goal.id(), goal.id(), goal.unitId(),
                code().title(), sum.stripTrailingZeros().toPlainString(), "100", code().recommendation()));
    }
}

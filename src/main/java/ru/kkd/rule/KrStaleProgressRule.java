package ru.kkd.rule;

import org.springframework.stereotype.Component;
import ru.kkd.domain.*;
import java.time.LocalDate;
import java.util.List;

@Component
public final class KrStaleProgressRule implements KkdRule {
    public RuleCode code() { return RuleCode.KR_STALE_PROGRESS; }
    public List<IssueDraft> check(Snapshot snapshot, Goal goal) {
        if (!goal.active()) return List.of();
        LocalDate threshold = snapshot.date().minusDays(45);
        return goal.keyResults().stream().filter(kr -> kr.factUpdatedAt() == null || kr.factUpdatedAt().isBefore(threshold))
                .map(kr -> new IssueDraft(code(), ObjectType.KR, kr.id(), goal.id(), goal.unitId(), code().title(),
                        kr.factUpdatedAt() == null ? null : kr.factUpdatedAt().toString(),
                        "Дата не раньше " + threshold, code().recommendation())).toList();
    }
}

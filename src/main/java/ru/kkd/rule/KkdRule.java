package ru.kkd.rule;
import ru.kkd.domain.Goal;
import ru.kkd.domain.IssueDraft;
import ru.kkd.domain.RuleCode;
import ru.kkd.domain.Snapshot;
import java.util.List;
public interface KkdRule {
    RuleCode code();
    List<IssueDraft> check(Snapshot snapshot, Goal goal);
}

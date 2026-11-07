package ru.kkd.rule;

import org.springframework.stereotype.Component;
import ru.kkd.domain.IssueDraft;
import ru.kkd.domain.Snapshot;
import java.util.List;

@Component
public final class RuleEngine {
    private final List<KkdRule> rules;
    public RuleEngine(List<KkdRule> rules) { this.rules = List.copyOf(rules); }
    public List<IssueDraft> evaluate(Snapshot snapshot) {
        return snapshot.goals().stream().filter(g -> g.active())
                .flatMap(goal -> rules.stream().flatMap(rule -> rule.check(snapshot, goal).stream())).toList();
    }
}

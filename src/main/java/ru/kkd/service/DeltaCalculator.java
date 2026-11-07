package ru.kkd.service;

import org.springframework.stereotype.Component;
import ru.kkd.api.ApiModels.Delta;
import ru.kkd.domain.Issue;
import ru.kkd.domain.IssueKey;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public final class DeltaCalculator {
    public Comparison compare(List<Issue> current, List<Issue> previous) {
        return new Comparison(keys(current), previous == null ? null : keys(previous));
    }

    private static Set<IssueKey> keys(List<Issue> issues) {
        return issues.stream().map(Issue::key).collect(Collectors.toUnmodifiableSet());
    }

    public record Comparison(Set<IssueKey> currentKeys, Set<IssueKey> previousKeys) {
        public String status(Issue issue) {
            if (previousKeys == null) return null;
            return previousKeys.contains(issue.key()) ? "persisting" : "new";
        }

        public boolean resolved(Issue issue) {
            return !currentKeys.contains(issue.key());
        }

        public Delta counts(List<Issue> currentInUnit, List<Issue> previousInUnit) {
            if (previousKeys == null) return null;
            int persisting = (int) currentInUnit.stream().map(Issue::key).filter(previousKeys::contains).count();
            int resolved = (int) previousInUnit.stream().filter(this::resolved).count();
            return new Delta(currentInUnit.size() - persisting, persisting, resolved);
        }
    }
}

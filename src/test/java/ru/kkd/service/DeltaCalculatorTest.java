package ru.kkd.service;

import org.junit.jupiter.api.Test;
import ru.kkd.domain.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class DeltaCalculatorTest {
    private final DeltaCalculator calculator=new DeltaCalculator();

    @Test void matchesRuleAndObjectNotDatabaseIssueIdOrDescription() {
        Issue old=issue("old","GOAL-A",RuleCode.GOAL_NO_OWNER,"Старый текст");
        Issue current=issue("new-id","GOAL-A",RuleCode.GOAL_NO_OWNER,"Новый текст");
        var counts=counts(List.of(current,issue("new","GOAL-B",RuleCode.GOAL_NO_OWNER,"")),
                List.of(old,issue("resolved","GOAL-C",RuleCode.KR_WEIGHTS_SUM,"")));
        assertThat(counts.newCount()).isEqualTo(1);
        assertThat(counts.persisting()).isEqualTo(1);
        assertThat(counts.resolved()).isEqualTo(1);
    }
    @Test void differentRuleOnSameGoalIsNewAndOldRuleIsResolved() {
        var counts=counts(List.of(issue("1","G",RuleCode.KR_WEIGHTS_SUM,"")),
                List.of(issue("2","G",RuleCode.GOAL_NO_OWNER,"")));
        assertThat(counts.newCount()).isEqualTo(1);
        assertThat(counts.persisting()).isZero();
        assertThat(counts.resolved()).isEqualTo(1);
    }
    @Test void firstRunHasNoComparisonRatherThanZero() {
        Issue i=issue("1","G",RuleCode.GOAL_NO_OWNER,"");
        var comparison = calculator.compare(List.of(i), null);
        assertThat(comparison.counts(List.of(i), List.of())).isNull();
        assertThat(comparison.status(i)).isNull();
    }
    @Test void noRemainingProblemsMeansAllPreviousAreResolved() {
        var counts=counts(List.of(),List.of(issue("1","G",RuleCode.GOAL_NO_OWNER,"")));
        assertThat(counts.newCount()).isZero();
        assertThat(counts.persisting()).isZero();
        assertThat(counts.resolved()).isEqualTo(1);
    }
    private ru.kkd.api.ApiModels.Delta counts(List<Issue> current, List<Issue> previous) {
        return calculator.compare(current, previous).counts(current, previous);
    }

    private Issue issue(String id,String objectId,RuleCode rule,String message) {
        return new Issue(id,id,new IssueDraft(rule,ObjectType.GOAL,objectId,objectId,"HQ",message,null,"Ожидается","Исправьте"));
    }
}

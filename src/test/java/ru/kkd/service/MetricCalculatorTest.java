package ru.kkd.service;

import org.junit.jupiter.api.Test;
import ru.kkd.domain.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class MetricCalculatorTest {
    private final MetricCalculator calculator=new MetricCalculator();
    private final LocalDate date=LocalDate.of(2026,11,2);
    private final List<OrgUnit> units=List.of(new OrgUnit("HQ","HQ",null),new OrgUnit("CHILD","CHILD","HQ"));

    @Test void metricIncludesDescendantsAndCountsBadGoalOnlyOnce() {
        var snapshot=new Snapshot(date,units,List.of(goal("A","HQ",GoalStatus.ACTIVE),goal("B","CHILD",GoalStatus.ACTIVE),goal("C","CHILD",GoalStatus.COMPLETED)));
        var values=calculator.values(snapshot,List.of(issue("1","A",RuleCode.GOAL_NO_OWNER),issue("2","A",RuleCode.KR_WEIGHTS_SUM)),UnitScope.descendants(units,"HQ"));
        assertThat(values.get("clean_goals_share")).isEqualByComparingTo("50.0");
        assertThat(values.get("issues_count")).isEqualByComparingTo("2");
        assertThat(values.get("affected_goals_count")).isEqualByComparingTo("1");
    }
    @Test void emptyOrInactiveOnlyUnitHasZeroCountsAndUndefinedShare() {
        var snapshot=new Snapshot(date,units,List.of(goal("A","HQ",GoalStatus.COMPLETED)));
        var values = calculator.values(snapshot, List.of(), UnitScope.descendants(units, "HQ"));
        assertThat(values.get("clean_goals_share")).isNull();
        assertThat(values.get("issues_count")).isEqualByComparingTo("0");
        assertThat(values.get("affected_goals_count")).isEqualByComparingTo("0");
    }
    @Test void cleanShareRoundsHalfUpToOneDecimal() {
        var snapshot=new Snapshot(date,units,List.of(goal("A","HQ",GoalStatus.ACTIVE),goal("B","HQ",GoalStatus.ACTIVE),goal("C","HQ",GoalStatus.ACTIVE)));
        var values=calculator.values(snapshot,List.of(issue("1","A",RuleCode.GOAL_NO_OWNER)),UnitScope.descendants(units,"HQ"));
        assertThat(values.get("clean_goals_share")).isEqualByComparingTo("66.7");
    }
    @Test void trendKnowsThatMoreIssuesIsWorseButMoreCleanGoalsIsBetter() {
        var previous=Map.of("clean_goals_share",new BigDecimal("42.9"),"issues_count",new BigDecimal("12"),"affected_goals_count",new BigDecimal("12"));
        var current=Map.of("clean_goals_share",new BigDecimal("47.6"),"issues_count",new BigDecimal("11"),"affected_goals_count",new BigDecimal("11"));
        var metrics=calculator.compare(current,previous);
        assertThat(metrics).allMatch(m -> m.trend().equals("better"));
        assertThat(metrics.get(0).delta()).isEqualByComparingTo("4.7");
        assertThat(metrics.get(1).delta()).isEqualByComparingTo("-1");
        assertThat(calculator.compare(previous,current)).allMatch(m -> m.trend().equals("worse"));
    }
    @Test void firstRunAndUndefinedPastDoNotPretendThatPastWasZero() {
        var current=Map.of("clean_goals_share",new BigDecimal("100"),"issues_count",BigDecimal.ZERO,"affected_goals_count",BigDecimal.ZERO);
        assertThat(calculator.compare(current,null)).allMatch(m -> m.previous() == null && m.delta() == null && m.trend() == null);
        assertThat(calculator.compare(current,current)).allMatch(m -> m.trend().equals("unchanged"));
    }
    private Goal goal(String id,String unit,GoalStatus status) { return new Goal(id,unit,"Владелец",null,status,date,List.of()); }
    private Issue issue(String id,String goal,RuleCode code) {
        return new Issue(id,"RUN",new IssueDraft(code,ObjectType.GOAL,goal,goal,"HQ","Проблема",null,"Ожидание","Рекомендация"));
    }
}

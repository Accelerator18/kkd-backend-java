package ru.kkd.rule;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.kkd.domain.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RulesTest {
    private static final LocalDate DATE = LocalDate.of(2026,11,2);
    private static final List<OrgUnit> UNITS = List.of(new OrgUnit("HQ","HQ",null),
            new OrgUnit("MID","MID","HQ"),new OrgUnit("CHILD","CHILD","MID"),new OrgUnit("OTHER","OTHER","HQ"));

    @Test void weightsUseExactDecimalsAndAcceptExactlyOneHundred() {
        Goal g = goal("G","HQ",null,GoalStatus.ACTIVE,List.of(kr("A","33.333333",0),kr("B","66.666667",0)));
        assertThat(new KrWeightsSumRule().check(snapshot(g),g)).isEmpty();
    }
    @Test void weightsRejectTinyDeviationAndReportOneIssuePerGoal() {
        Goal g = goal("G","HQ",null,GoalStatus.ACTIVE,List.of(kr("A","33.333333",0),kr("B","66.666666",0)));
        var found = new KrWeightsSumRule().check(snapshot(g),g);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).actualValue()).isEqualTo("99.999999");
        assertThat(found.get(0).objectType()).isEqualTo(ObjectType.GOAL);
    }
    @Test void goalWithoutKeyResultsHasZeroWeight() {
        Goal g=goal("G","HQ",null,GoalStatus.ACTIVE,List.of());
        assertThat(new KrWeightsSumRule().check(snapshot(g),g)).singleElement().extracting(IssueDraft::actualValue).isEqualTo("0");
    }
    @ParameterizedTest @ValueSource(ints={0,44,45})
    void factAtMost45DaysOldIsCurrent(int age) {
        Goal g=goal("G","HQ",null,GoalStatus.ACTIVE,List.of(kr("KR","100",age)));
        assertThat(new KrStaleProgressRule().check(snapshot(g),g)).isEmpty();
    }
    @Test void fact46DaysOldIsStale() {
        Goal g=goal("G","HQ",null,GoalStatus.ACTIVE,List.of(kr("KR","100",46)));
        assertThat(new KrStaleProgressRule().check(snapshot(g),g)).singleElement()
                .extracting(IssueDraft::objectId).isEqualTo("KR");
    }
    @Test void missingFactDateIsStale() {
        Goal g=goal("G","HQ",null,GoalStatus.ACTIVE,List.of(new KeyResult("KR",null,null,BigDecimal.valueOf(100),null)));
        assertThat(new KrStaleProgressRule().check(snapshot(g),g)).hasSize(1);
    }
    @Test void futureFactDateIsNotStale() {
        Goal g=goal("G","HQ",null,GoalStatus.ACTIVE,List.of(kr("KR","100",-1)));
        assertThat(new KrStaleProgressRule().check(snapshot(g),g)).isEmpty();
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"  ","\t\n"})
    void blankOwnerIsMissing(String owner) {
        Goal g=new Goal("G","HQ",owner,null,GoalStatus.ACTIVE,DATE,List.of());
        assertThat(new GoalNoOwnerRule().check(snapshot(g),g)).hasSize(1);
    }
    @Test void namedOwnerIsPresent() {
        Goal g=goal("G","HQ",null,GoalStatus.ACTIVE,List.of());
        assertThat(new GoalNoOwnerRule().check(snapshot(g),g)).isEmpty();
    }
    @Test void rootUnitGoalDoesNotNeedParent() {
        Goal g=goal("G","HQ",null,GoalStatus.ACTIVE,List.of());
        assertThat(new CascadeNoParentRule().check(snapshot(g),g)).isEmpty();
    }
    @Test void childUnitNeedsExistingParentGoal() {
        Goal missing=goal("G","CHILD","MISSING",GoalStatus.ACTIVE,List.of());
        Goal absent=goal("G","CHILD",null,GoalStatus.ACTIVE,List.of());
        assertThat(new CascadeNoParentRule().check(snapshot(missing),missing)).hasSize(1);
        assertThat(new CascadeNoParentRule().check(snapshot(absent),absent)).hasSize(1);
    }
    @Test void parentInAnyStrictAncestorIsValidEvenIfCompleted() {
        Goal g=goal("G","CHILD","P",GoalStatus.ACTIVE,List.of());
        Goal p=goal("P","HQ",null,GoalStatus.COMPLETED,List.of());
        assertThat(new CascadeNoParentRule().check(snapshot(g,p),g)).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"CHILD","OTHER"})
    void parentInSameUnitOrSiblingIsInvalid(String parentUnit) {
        Goal g=goal("G","CHILD","P",GoalStatus.ACTIVE,List.of());
        Goal p=goal("P",parentUnit,null,GoalStatus.ACTIVE,List.of());
        assertThat(new CascadeNoParentRule().check(snapshot(g,p),g)).hasSize(1);
    }
    @Test void inactiveGoalsAndTheirKeyResultsAreIgnoredByAllRules() {
        var rules=List.of(new GoalNoOwnerRule(),new KrWeightsSumRule(),new CascadeNoParentRule(),new KrStaleProgressRule());
        for (GoalStatus status : List.of(GoalStatus.COMPLETED,GoalStatus.DRAFT,GoalStatus.CANCELLED)) {
            Goal g=new Goal("G","CHILD",null,null,status,DATE,List.of(kr("KR","1",90)));
            for (var rule : rules) assertThat(rule.check(snapshot(g),g)).isEmpty();
            assertThat(new RuleEngine(rules).evaluate(snapshot(g))).isEmpty();
        }
    }
    @Test void cyclicUnitDataDoesNotHangTraversalOrMakeSelfAnAncestor() {
        var units=List.of(new OrgUnit("A","A","B"),new OrgUnit("B","B","A"));
        assertThat(UnitScope.ancestors(units,"A")).containsExactly("B");
        assertThat(UnitScope.descendants(units,"A")).containsExactlyInAnyOrder("A","B");
    }
    private static Snapshot snapshot(Goal... goals) { return new Snapshot(DATE,UNITS,List.of(goals)); }
    private static Goal goal(String id,String unit,String parent,GoalStatus status,List<KeyResult> krs) {
        return new Goal(id,unit,"Ответственный",parent,status,DATE,krs);
    }
    private static KeyResult kr(String id,String weight,int age) {
        return new KeyResult(id,BigDecimal.TEN,BigDecimal.ONE,new BigDecimal(weight),DATE.minusDays(age));
    }
}

package ru.kkd.domain;
public record IssueDraft(RuleCode ruleCode, ObjectType objectType, String objectId,
                         String goalId, String unitId, String message,
                         String actualValue, String expectedValue, String recommendation) {
    public IssueKey key() { return new IssueKey(ruleCode, objectType, objectId); }
}

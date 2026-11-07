package ru.kkd.domain;

public record Issue(String id, String runId, IssueDraft data, Severity severity) {
    public Issue(String id, String runId, IssueDraft data) {
        this(id, runId, data, data.ruleCode().severity());
    }

    public IssueKey key() { return data.key(); }
}

package ru.kkd.domain;

public enum RuleCode {
    GOAL_NO_OWNER(Severity.CRITICAL, "Цель без ответственного", "Назначьте ответственного за цель."),
    KR_WEIGHTS_SUM(Severity.MAJOR, "Сумма весов КР не равна 100%", "Укажите веса КР так, чтобы их сумма равнялась 100%."),
    CASCADE_NO_PARENT(Severity.MAJOR, "Нет связи с целью вышестоящего подразделения", "Свяжите цель с целью одного из вышестоящих подразделений."),
    KR_STALE_PROGRESS(Severity.MINOR, "Факт КР устарел", "Обновите фактическое значение и дату его обновления.");

    private final Severity severity;
    private final String title;
    private final String recommendation;

    RuleCode(Severity severity, String title, String recommendation) {
        this.severity = severity;
        this.title = title;
        this.recommendation = recommendation;
    }
    public Severity severity() { return severity; }
    public String title() { return title; }
    public String recommendation() { return recommendation; }
}

package ru.kkd.domain;
public enum Severity { CRITICAL, MAJOR, MINOR;
    public String code() { return name().toLowerCase(java.util.Locale.ROOT); }
}

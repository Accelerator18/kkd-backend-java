package ru.kkd.domain;
import java.math.BigDecimal;
import java.time.LocalDate;
public record KeyResult(String id, BigDecimal targetValue, BigDecimal actualValue,
                        BigDecimal weight, LocalDate factUpdatedAt) { }

package ru.kkd.service;

import org.springframework.stereotype.Component;
import ru.kkd.domain.*;
import ru.kkd.api.ApiModels.Metric;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Component
public final class MetricCalculator {
    public Map<String, BigDecimal> values(Snapshot snapshot, List<Issue> issues, Set<String> scope) {
        Set<String> active = snapshot.goals().stream().filter(Goal::active).filter(g -> scope.contains(g.unitId()))
                .map(Goal::id).collect(Collectors.toSet());
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        long count = issues.stream().filter(i -> active.contains(i.data().goalId())).count();
        long affected = issues.stream().map(i -> i.data().goalId()).filter(active::contains).distinct().count();
        values.put("clean_goals_share", active.isEmpty() ? null : BigDecimal.valueOf(active.size() - affected)
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(active.size()), 1, RoundingMode.HALF_UP));
        values.put("issues_count", BigDecimal.valueOf(count));
        values.put("affected_goals_count", BigDecimal.valueOf(affected));
        return values;
    }

    public List<Metric> compare(Map<String,BigDecimal> current, Map<String,BigDecimal> previous) {
        return List.of(metric("clean_goals_share","Доля корректных целей","%",true,current,previous),
                metric("issues_count","Количество проблем","шт.",false,current,previous),
                metric("affected_goals_count","Цели с проблемами","шт.",false,current,previous));
    }
    private Metric metric(String key, String title, String unit, boolean higherIsBetter,
                          Map<String,BigDecimal> current, Map<String,BigDecimal> previous) {
        BigDecimal value = current.get(key);
        BigDecimal past = previous == null ? null : previous.get(key);
        BigDecimal delta = value == null || past == null ? null : value.subtract(past);
        String trend = null;
        if (delta != null) {
            int sign = delta.signum();
            trend = sign == 0 ? "unchanged" : ((sign > 0) == higherIsBetter ? "better" : "worse");
        }
        return new Metric(key,title,unit,value,past,delta,trend,higherIsBetter);
    }
}

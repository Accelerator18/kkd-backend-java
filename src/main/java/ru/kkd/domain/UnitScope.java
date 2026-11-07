package ru.kkd.domain;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class UnitScope {
    private UnitScope() { }

    public static Set<String> descendants(List<OrgUnit> units, String unitId) {
        if (unitId == null) return units.stream().map(OrgUnit::id).collect(Collectors.toSet());
        Set<String> scope = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        if (units.stream().anyMatch(u -> u.id().equals(unitId))) queue.add(unitId);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (scope.add(id)) {
                units.stream().filter(u -> id.equals(u.parentId())).map(OrgUnit::id).forEach(queue::addLast);
            }
        }
        return scope;
    }

    public static Set<String> ancestors(List<OrgUnit> units, String unitId) {
        Map<String, OrgUnit> byId = units.stream().collect(Collectors.toMap(OrgUnit::id, Function.identity()));
        Set<String> ancestors = new HashSet<>();
        Set<String> visited = new HashSet<>();
        visited.add(unitId);
        OrgUnit current = byId.get(unitId);
        while (current != null && current.parentId() != null && visited.add(current.parentId())) {
            ancestors.add(current.parentId());
            current = byId.get(current.parentId());
        }
        return ancestors;
    }
}

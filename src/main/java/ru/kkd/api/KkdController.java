package ru.kkd.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.kkd.api.ApiModels.*;
import ru.kkd.service.KkdQueryService;
import ru.kkd.service.RunService;

@RestController
@RequestMapping("/api")
@Validated
public class KkdController {
    private final KkdQueryService query;
    private final RunService runs;
    public KkdController(KkdQueryService query, RunService runs) { this.query=query; this.runs=runs; }

    @GetMapping("/runs") public RunList runs() { return query.runs(); }
    @GetMapping("/meta") public Meta meta() { return query.meta(); }
    @GetMapping("/kkd/summary")
    public Summary summary(
            @RequestParam("run_id") @Pattern(regexp="[A-Za-z0-9_-]{1,60}") String runId,
            @RequestParam("unit_id") @Pattern(regexp="[A-Za-z0-9_-]{1,40}") String unitId) {
        return query.summary(runId,unitId);
    }
    @GetMapping("/kkd/issues")
    public IssuePage issues(
            @RequestParam("run_id") @Pattern(regexp="[A-Za-z0-9_-]{1,60}") String runId,
            @RequestParam(value="unit_id",required=false) @Pattern(regexp="[A-Za-z0-9_-]{1,40}") String unitId,
            @RequestParam(value="rule_code",required=false) String ruleCode,
            @RequestParam(value="severity",required=false) String severity,
            @RequestParam(value="delta_status",required=false) String deltaStatus,
            @RequestParam(value="limit",defaultValue="20") @Min(1) @Max(100) int limit,
            @RequestParam(value="offset",defaultValue="0") @Min(0) int offset) {
        return query.issues(runId,unitId,ruleCode,severity,deltaStatus,limit,offset);
    }
    @GetMapping("/kkd/issues/{id}") public IssueView issue(@PathVariable String id) { return query.issue(id); }
    @PostMapping("/runs")
    public ResponseEntity<RunView> create(@Valid @RequestBody CreateRunRequest request) {
        var run = runs.create(request.date());
        return ResponseEntity.status(201).body(query.runView(run));
    }
}

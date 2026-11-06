package com.smartdoor.backend.access.controller;

import com.smartdoor.backend.access.entity.AccessLog;
import com.smartdoor.backend.access.service.AccessQueryService;
import com.smartdoor.backend.access.service.MonitoringService;
import com.smartdoor.backend.shared.api.PageResponse;
import com.smartdoor.backend.shared.security.CurrentAccountProvider;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/doors/{doorId}")
public class AccessController {
    private final AccessQueryService queries;
    private final MonitoringService monitoring;
    private final CurrentAccountProvider current;
    public AccessController(AccessQueryService queries,MonitoringService monitoring,CurrentAccountProvider current) {
        this.queries=queries; this.monitoring=monitoring; this.current=current;
    }
    @GetMapping("/access-logs")
    public PageResponse<AccessQueryService.LogView> logs(@PathVariable Long doorId,
        @RequestParam(required=false) AccessLog.Method method,@RequestParam(required=false) AccessLog.Result result,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return queries.list(current.requireAccountId(),doorId,method,result,page,size);
    }
    @GetMapping("/access-summary")
    public AccessQueryService.Summary summary(@PathVariable Long doorId) { return queries.summary(current.requireAccountId(),doorId); }
    @GetMapping("/monitoring")
    public MonitoringService.View monitoring(@PathVariable Long doorId) { return monitoring.latest(current.requireAccountId(),doorId); }
}

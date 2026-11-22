package com.smartdoor.backend.onboarding;

import com.smartdoor.backend.shared.security.CurrentAccountProvider;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class ReplacementController {
    private final ReplacementService service;
    private final CurrentAccountProvider account;
    public ReplacementController(ReplacementService service,CurrentAccountProvider account) { this.service=service; this.account=account; }
    @ModelAttribute void noCache(HttpServletResponse response) { response.setHeader("Cache-Control","no-store"); }
    @PostMapping("/doors/{doorId}/devices/{deviceId}/replacement-requests")
    Object create(@PathVariable Long doorId,@PathVariable Long deviceId,@Valid @RequestBody Create request) {
        return service.create(account.requireAccountId(),doorId,deviceId,request.requestId(),request.reason());
    }
    @GetMapping("/doors/{doorId}/devices/{deviceId}/replacement-request")
    Object current(@PathVariable Long doorId,@PathVariable Long deviceId) { return service.current(account.requireAccountId(),doorId,deviceId); }
    @PostMapping("/doors/{doorId}/replacement-requests/{id}/complete")
    Object complete(@PathVariable Long doorId,@PathVariable String id) { return service.complete(account.requireAccountId(),doorId,id); }
    @PostMapping("/doors/{doorId}/replacement-requests/{id}/cancel")
    Object cancel(@PathVariable Long doorId,@PathVariable String id) { return service.cancel(account.requireAccountId(),doorId,id); }
    @GetMapping("/technician/replacements") Object list() { return service.technicianList(account.requireAccountId()); }
    @PostMapping("/technician/replacements") Object bind(@Valid @RequestBody Bind request) {
        return service.bind(account.requireAccountId(),request.authorizationCode(),request.unitCode());
    }
    public record Create(@NotBlank @Size(max=36) String requestId,@NotBlank @Size(max=500) String reason) {}
    public record Bind(@NotBlank @Size(max=100) String authorizationCode,@NotBlank @Size(max=40) String unitCode) {
        @Override public String toString() { return "Bind[redacted]"; }
    }
}

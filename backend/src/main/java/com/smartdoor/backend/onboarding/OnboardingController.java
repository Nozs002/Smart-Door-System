package com.smartdoor.backend.onboarding;

import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.shared.security.CurrentAccountProvider;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class OnboardingController {
    private final OnboardingService service;
    private final CurrentAccountProvider current;
    public OnboardingController(OnboardingService service,CurrentAccountProvider current) { this.service=service; this.current=current; }
    @ModelAttribute void noCache(HttpServletResponse response) { response.setHeader("Cache-Control","no-store"); }
    @GetMapping("/technician/inventory") Object inventory() { return service.inventory(current.requireAccountId()); }
    @PostMapping("/technician/inventory") Object register(@Valid @RequestBody RegisterUnit request) { return service.register(current.requireAccountId(),request.nodeType()); }
    @GetMapping("/technician/inventory/{code}/factory") Object factory(@PathVariable String code) { return service.factory(current.requireAccountId(),code); }
    @GetMapping("/technician/kits") Object kits() { return service.listKits(current.requireAccountId()); }
    @PostMapping("/technician/kits") Object create(@Valid @RequestBody CreateKit r) { return service.createKit(current.requireAccountId(),r.requestId(),r.name(),r.location(),r.node1Code(),r.node2Code()); }
    @PostMapping("/technician/kits/{id}/claim-code") Object reissue(@PathVariable String id) { return service.reissue(current.requireAccountId(),id); }
    @GetMapping("/doors/{id}/setup-nodes") Object nodes(@PathVariable Long id) { return service.setupNodes(current.requireAccountId(),id); }
    @PostMapping("/doors/{id}/complete-setup") Object complete(@PathVariable Long id) { return service.complete(current.requireAccountId(),id); }
    @PostMapping("/device-enrollment") Object enroll(@Valid @RequestBody Enrollment r) { return service.enroll(r.unitCode(),r.bootstrapSecret()); }
    public record RegisterUnit(@NotNull Device.NodeType nodeType) {}
    public record CreateKit(@NotBlank String requestId,@NotBlank @Size(max=100) String name,@Size(max=255) String location,
            @NotBlank @Size(max=40) String node1Code,@NotBlank @Size(max=40) String node2Code) {}
    public record Enrollment(@NotBlank @Size(max=40) String unitCode,@NotBlank @Size(max=100) String bootstrapSecret) {
        @Override public String toString() { return "Enrollment[redacted]"; }
    }
}

package com.smartdoor.backend.control.controller;

import com.smartdoor.backend.control.service.UnlockService;
import com.smartdoor.backend.control.service.UnlockService.CommandView;
import com.smartdoor.backend.shared.security.CurrentAccountProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/doors/{doorId}/commands")
public class UnlockController {
    private final UnlockService service;
    private final CurrentAccountProvider account;
    public UnlockController(UnlockService service,CurrentAccountProvider account) { this.service=service; this.account=account; }
    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
    public CommandView unlock(@PathVariable Long doorId,@Valid @RequestBody UnlockRequest body) {
        return service.command(account.requireAccountId(),doorId,body.requestId(),body.action()==null ? "UNLOCK" : body.action().name());
    }
    @GetMapping("/latest") public CommandView latest(@PathVariable Long doorId) { return service.latest(account.requireAccountId(),doorId); }
    @GetMapping("/{id}") public CommandView get(@PathVariable Long doorId,@PathVariable String id) { return service.get(account.requireAccountId(),doorId,id); }
    public enum Action { UNLOCK, CLOSE }
    public record UnlockRequest(@NotNull UUID requestId,Action action) { }
}

package com.smartdoor.backend.credentials.controller;

import com.smartdoor.backend.credentials.dto.CredentialDtos.*;
import com.smartdoor.backend.credentials.service.CredentialsManagementService;
import com.smartdoor.backend.shared.api.PageResponse;
import com.smartdoor.backend.shared.security.CurrentAccountProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CredentialsController {
    private final CredentialsManagementService credentials;
    private final CurrentAccountProvider current;
    public CredentialsController(CredentialsManagementService credentials, CurrentAccountProvider current) {
        this.credentials = credentials; this.current = current;
    }
    @GetMapping("/cardholders")
    public PageResponse<CardholderView> cardholders(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return credentials.listCardholders(current.requireAccountId(), page, size);
    }
    @PostMapping("/cardholders") @ResponseStatus(HttpStatus.CREATED)
    public CardholderView create(@Valid @RequestBody CreateCardholder request) {
        return credentials.createCardholder(current.requireAccountId(), request);
    }
    @PatchMapping("/cardholders/{id}")
    public CardholderView update(@PathVariable Long id, @Valid @RequestBody UpdateCardholder request) {
        return credentials.updateCardholder(current.requireAccountId(), id, request);
    }
    @GetMapping("/doors/{doorId}/rfid-credentials")
    public PageResponse<RfidView> rfids(@PathVariable Long doorId, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return credentials.listRfid(current.requireAccountId(), doorId, page, size);
    }
    @PostMapping("/doors/{doorId}/rfid-credentials") @ResponseStatus(HttpStatus.CREATED)
    public RfidView issue(@PathVariable Long doorId, @Valid @RequestBody IssueRfid request) {
        return credentials.issueRfid(current.requireAccountId(), doorId, request);
    }
    @PostMapping("/doors/{doorId}/rfid-credentials/{id}/revoke")
    public RfidView revoke(@PathVariable Long doorId, @PathVariable Long id) {
        return credentials.revokeRfid(current.requireAccountId(), doorId, id);
    }
    @GetMapping("/doors/{doorId}/pin")
    public PinStatus pin(@PathVariable Long doorId) {
        return credentials.pinStatus(current.requireAccountId(), doorId);
    }
    @PutMapping("/doors/{doorId}/pin")
    public PinStatus updatePin(@PathVariable Long doorId, @Valid @RequestBody UpdatePin request) {
        return credentials.updatePin(current.requireAccountId(), doorId, request);
    }
}

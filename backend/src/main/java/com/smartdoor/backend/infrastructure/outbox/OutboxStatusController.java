package com.smartdoor.backend.infrastructure.outbox;

import com.smartdoor.backend.shared.api.PageResponse;
import com.smartdoor.backend.shared.security.CurrentAccountProvider;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/doors/{doorId}/delivery-status")
public class OutboxStatusController {
	private final OutboxService outbox;
	private final CurrentAccountProvider account;
	public OutboxStatusController(OutboxService outbox, CurrentAccountProvider account) {
		this.outbox = outbox; this.account = account;
	}
	@GetMapping
	public PageResponse<OutboxService.DeliveryStatus> list(@PathVariable Long doorId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return outbox.status(account.requireAccountId(), doorId, page, size);
	}
}

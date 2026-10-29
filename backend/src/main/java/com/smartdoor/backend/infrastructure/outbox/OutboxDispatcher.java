package com.smartdoor.backend.infrastructure.outbox;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!provision & !device-security")
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDispatcher {
	private final OutboxService outbox;
	private final List<OutboxTransport> transports;
	public OutboxDispatcher(OutboxService outbox, List<OutboxTransport> transports) {
		this.outbox = outbox; this.transports = transports;
	}

	@Scheduled(fixedDelayString = "${app.outbox.dispatch-interval-ms:1000}")
	public void dispatch() {
		List<String> kinds = List.of(OutboxService.CONFIGURATION, OutboxService.CREDENTIALS).stream()
				.filter(kind -> transports.stream().anyMatch(t -> t.supports(kind))).toList();
		if (kinds.isEmpty()) return; // No MQTT transport: queued messages remain honestly PENDING.
		for (OutboxService.Claim claim : outbox.claim(kinds, 10)) {
			try {
				OutboxTransport transport = transports.stream().filter(t -> t.supports(claim.delivery().kind())).findFirst().orElseThrow();
				transport.deliver(claim.delivery());
				outbox.delivered(claim);
			} catch (Exception error) {
				outbox.failed(claim, error);
			}
		}
	}
}

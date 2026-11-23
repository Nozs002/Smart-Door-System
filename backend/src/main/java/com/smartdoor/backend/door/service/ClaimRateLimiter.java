package com.smartdoor.backend.door.service;

import com.smartdoor.backend.shared.error.ApplicationException;
import com.smartdoor.backend.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Giới hạn theo Account và transport IP trên một instance; không tin X-Forwarded-For. */
@Component
public class ClaimRateLimiter {
	private final Clock clock;
	private final Map<String, Window> windows = new HashMap<>();
	private static final int MAX_KEYS = 20_000;

	public ClaimRateLimiter(Clock clock) { this.clock = clock; }

	public synchronized void check(Long accountId, String remoteAddress) {
		Instant now = clock.instant();
		windows.entrySet().removeIf(entry -> !entry.getValue().start.plus(Duration.ofMinutes(1)).isAfter(now));
		String accountKey = "account:" + accountId;
		String ipKey = "ip:" + remoteAddress;
		if (windows.size() >= MAX_KEYS || exhausted(accountKey) || exhausted(ipKey)) {
			throw new ApplicationException(ErrorCode.RATE_LIMITED, "Đã vượt số lần thử mã. Vui lòng chờ một phút.");
		}
		increment(accountKey, now);
		increment(ipKey, now);
	}

	private boolean exhausted(String key) { return windows.containsKey(key) && windows.get(key).count >= 5; }
	private void increment(String key, Instant now) {
		Window window = windows.computeIfAbsent(key, ignored -> new Window(now));
		window.count++;
	}
	private static final class Window {
		private final Instant start;
		private int count;
		private Window(Instant start) { this.start = start; }
	}
}

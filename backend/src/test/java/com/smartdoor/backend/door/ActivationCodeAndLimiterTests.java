package com.smartdoor.backend.door;

import static org.assertj.core.api.Assertions.*;

import com.smartdoor.backend.door.service.ActivationCodes;
import com.smartdoor.backend.door.service.ClaimRateLimiter;
import com.smartdoor.backend.shared.error.ApplicationException;
import com.smartdoor.backend.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class ActivationCodeAndLimiterTests {
	@Test
	void codesUseUnambiguousCharactersAndNormalizeCaseAndHyphen() {
		ActivationCodes codes = new ActivationCodes();
		var values = new HashSet<String>();
		for (int i = 0; i < 1000; i++) {
			String value = codes.generate();
			assertThat(value).matches("[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{4}-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{4}");
			assertThat(codes.hash(value)).isEqualTo(codes.hash(value.toLowerCase().replace("-", "")));
			values.add(value);
		}
		assertThat(values).hasSize(1000);
	}

	@Test
	void accountAndIpLimitsExpireWithoutDependingOnClientHeaders() {
		MutableClock clock = new MutableClock();
		ClaimRateLimiter limiter = new ClaimRateLimiter(clock);
		for (int i = 0; i < 5; i++) limiter.check(1L, "127.0.0.1");
		assertThatThrownBy(() -> limiter.check(1L, "192.0.2.1"))
				.isInstanceOfSatisfying(ApplicationException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED));
		assertThatThrownBy(() -> limiter.check(2L, "127.0.0.1")).isInstanceOf(ApplicationException.class);
		clock.now = clock.now.plusSeconds(60);
		assertThatCode(() -> limiter.check(1L, "127.0.0.1")).doesNotThrowAnyException();
	}

	private static class MutableClock extends Clock {
		Instant now = Instant.parse("2026-10-16T00:00:00Z");
		@Override public ZoneId getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(ZoneId zone) { return this; }
		@Override public Instant instant() { return now; }
	}
}

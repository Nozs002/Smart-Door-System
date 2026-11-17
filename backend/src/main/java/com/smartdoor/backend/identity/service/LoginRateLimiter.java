package com.smartdoor.backend.identity.service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.smartdoor.backend.shared.error.ApplicationException;
import com.smartdoor.backend.shared.error.ErrorCode;

@Component
public class LoginRateLimiter {
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final LoginRateLimitProperties properties;
    private final Clock clock;

    public LoginRateLimiter(LoginRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized void check(String username, String ip) {
        Instant now = clock.instant();
        buckets.values().removeIf(bucket -> !now.isBefore(bucket.until));
        String normalized = Normalizer.normalize(username, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).stripTrailing();
        String userKey = "user:" + normalized;
        String ipKey = "ip:" + ip;
        int newKeys = (buckets.containsKey(userKey) ? 0 : 1) + (buckets.containsKey(ipKey) ? 0 : 1);
        if (buckets.size() + newKeys > properties.maxKeys()) { throw limited(); }
        Bucket address = buckets.computeIfAbsent(ipKey, key -> new Bucket(now.plus(properties.window())));
        if (address.attempts >= properties.ipAttempts()) { throw limited(); }
        address.attempts++;
        Bucket user = buckets.computeIfAbsent(userKey, key -> new Bucket(now.plus(properties.window())));
        if (user.attempts >= properties.usernameAttempts()) { throw limited(); }
        user.attempts++;
    }

    private ApplicationException limited() {
        return new ApplicationException(ErrorCode.LOGIN_RATE_LIMITED, "Vui lòng chờ trước khi đăng nhập lại.");
    }
    private static class Bucket {
        private final Instant until;
        private int attempts;
        Bucket(Instant until) { this.until = until; }
    }
}

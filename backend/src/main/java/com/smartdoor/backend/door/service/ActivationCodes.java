package com.smartdoor.backend.door.service;

import com.smartdoor.backend.shared.error.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ActivationCodes {
	private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
	private final SecureRandom random = new SecureRandom();

	public String generate() {
		StringBuilder value = new StringBuilder(9);
		for (int i = 0; i < 8; i++) {
			if (i == 4) value.append('-');
			value.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return value.toString();
	}

	public String hash(String raw) {
		String normalized = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT).replace("-", "");
		if (normalized.length() != 8 || normalized.chars().anyMatch(c -> ALPHABET.indexOf(c) < 0)) {
			throw new BadRequestException("Mã kích hoạt không hợp lệ hoặc không thể sử dụng.");
		}
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(normalized.getBytes(StandardCharsets.US_ASCII)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 unavailable", exception);
		}
	}
}

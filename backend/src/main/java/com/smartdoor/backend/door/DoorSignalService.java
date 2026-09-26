package com.smartdoor.backend.door;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class DoorSignalService {

	private static final Pattern TOPIC_PATTERN = Pattern.compile("^smart-door/([A-Za-z0-9_-]{1,64})/(status|event)$");
	private static final Set<String> ALLOWED_STATUSES = Set.of("OPENED", "CLOSED", "DENIED");

	private final DoorSignalHistoryRepository repository;
	private final ObjectMapper objectMapper;

	public DoorSignalService(DoorSignalHistoryRepository repository, ObjectMapper objectMapper) {
		this.repository = repository;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public void process(String topic, String jsonPayload) {
		Matcher matcher = TOPIC_PATTERN.matcher(topic);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("Unsupported MQTT topic: " + topic);
		}

		DoorSignalPayload signal = parse(jsonPayload);
		if (signal.status() == null || !ALLOWED_STATUSES.contains(signal.status())) {
			throw new IllegalArgumentException("Unsupported door status: " + signal.status());
		}

		repository.save(new DoorSignalHistory(
				matcher.group(1),
				matcher.group(2).toUpperCase(),
				signal.status(),
				signal.requestId(),
				jsonPayload,
				Instant.now()));
	}

	@Transactional(readOnly = true)
	public List<DoorSignalResponse> getHistory(String deviceId) {
		return repository.findTop100ByDeviceIdOrderByReceivedAtDesc(deviceId).stream()
				.map(DoorSignalResponse::from)
				.toList();
	}

	private DoorSignalPayload parse(String jsonPayload) {
		try {
			return objectMapper.readValue(jsonPayload, DoorSignalPayload.class);
		} catch (JacksonException exception) {
			throw new IllegalArgumentException("Invalid MQTT JSON payload", exception);
		}
	}
}

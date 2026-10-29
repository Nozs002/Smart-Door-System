package com.smartdoor.backend.infrastructure.outbox;

import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.door.service.DoorOwnershipService;
import com.smartdoor.backend.shared.api.PageResponse;
import com.smartdoor.backend.shared.error.ConflictException;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {
	public static final String CONFIGURATION = "CONFIGURATION";
	public static final String CREDENTIALS = "CREDENTIALS";
	private final OutboxRepository messages;
	private final DeviceRepository devices;
	private final DoorOwnershipService ownership;
	private final Clock clock;
	private final int maxAttempts;

	public OutboxService(OutboxRepository messages, DeviceRepository devices, DoorOwnershipService ownership,
			Clock clock, @Value("${app.outbox.max-attempts:8}") int maxAttempts) {
		if (maxAttempts < 1 || maxAttempts > 100) throw new IllegalArgumentException("Invalid outbox attempt limit");
		this.messages = messages; this.devices = devices; this.ownership = ownership;
		this.clock = clock; this.maxAttempts = maxAttempts;
	}

	/** Must participate in the transaction that changes the desired state. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void enqueue(String kind, Long doorId, String deviceCode, String payload) {
		if (!List.of(CONFIGURATION, CREDENTIALS).contains(kind)) throw new IllegalArgumentException("Unknown message kind");
		Device device = devices.findByDeviceCode(deviceCode)
				.filter(d -> doorId.equals(d.getDoorId()))
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị nhận cấu hình."));
		if (device.getLifecycleStatus() == Device.LifecycleStatus.RETIRED) {
			throw new ConflictException("Thiết bị đã ngừng sử dụng không được nhận cấu hình.");
		}
		if (messages.existsByDeviceIdAndKindAndPayloadAndStatusIn(device.getId(), kind, payload,
				List.of(OutboxMessage.Status.PENDING, OutboxMessage.Status.PROCESSING, OutboxMessage.Status.FAILED))) return;
		OutboxMessage message = new OutboxMessage();
		message.setKind(kind); message.setDoorId(doorId); message.setDeviceId(device.getId());
		message.setDeviceCode(deviceCode); message.setPayload(payload);
		message.setCreatedAt(now()); message.setNextAttemptAt(message.getCreatedAt());
		messages.save(message);
	}

	@Transactional
	public List<Claim> claim(Collection<String> kinds, int size) {
		if (kinds.isEmpty()) return List.of();
		LocalDateTime now = now();
		return messages.ready(kinds, OutboxMessage.Status.PENDING, OutboxMessage.Status.PROCESSING,
				now, PageRequest.of(0, Math.clamp(size, 1, 50))).stream().map(message -> {
			if (message.getAttempts() >= maxAttempts) {
				message.setStatus(OutboxMessage.Status.FAILED);
				message.setLastError("Đã hết số lần gửi tự động; cần kiểm tra và thử lại.");
				message.setLeaseUntil(null); message.setLeaseToken(null);
				return null;
			}
			String token = UUID.randomUUID().toString();
			message.setStatus(OutboxMessage.Status.PROCESSING);
			message.setAttempts(message.getAttempts() + 1);
			message.setLeaseToken(token); message.setLeaseUntil(now.plusMinutes(2));
			return new Claim(new OutboxDelivery(message.getId(), message.getKind(), message.getDoorId(),
					message.getDeviceCode(), message.getPayload()), token);
		}).filter(java.util.Objects::nonNull).toList();
	}

	@Transactional
	public void delivered(Claim claim) {
		messages.lock(claim.delivery().id()).filter(message -> owns(message, claim)).ifPresent(message -> {
			message.setStatus(OutboxMessage.Status.SENT); message.setSentAt(now());
			message.setLeaseUntil(null); message.setLeaseToken(null); message.setLastError(null);
		});
	}

	@Transactional
	public void failed(Claim claim, Exception error) {
		messages.lock(claim.delivery().id()).filter(message -> owns(message, claim)).ifPresent(message -> {
			message.setStatus(message.getAttempts() >= maxAttempts ? OutboxMessage.Status.FAILED : OutboxMessage.Status.PENDING);
			message.setNextAttemptAt(now().plusSeconds(Math.min(300, 1L << Math.min(message.getAttempts(), 8))));
			message.setLeaseUntil(null); message.setLeaseToken(null);
			// Transport exceptions may contain credentials or payload; never persist their text.
			message.setLastError("Gửi chưa thành công (" + error.getClass().getSimpleName() + ").");
		});
	}

	@Transactional
	public void retry(Long accountId, Long doorId) {
		ownership.requireOwnerForUpdate(accountId, doorId);
		for (OutboxMessage message : messages.retryable(doorId, OutboxMessage.Status.FAILED, OutboxMessage.Status.PENDING)) {
			message.setStatus(OutboxMessage.Status.PENDING); message.setAttempts(0);
			message.setNextAttemptAt(now()); message.setLastError(null);
		}
	}

	@Transactional(readOnly = true)
	public PageResponse<DeliveryStatus> status(Long accountId, Long doorId, int page, int size) {
		ownership.requireOwner(accountId, doorId);
		return PageResponse.from(messages.findAllByDoorId(doorId, PageResponse.pageable(page, size)).map(message ->
				new DeliveryStatus(message.getId(), message.getKind(), message.getDeviceCode(), message.getStatus(),
						message.getAttempts(), message.getNextAttemptAt(), message.getLastError(),
						message.getCreatedAt(), message.getSentAt())));
	}

	private boolean owns(OutboxMessage message, Claim claim) {
		return message.getStatus() == OutboxMessage.Status.PROCESSING && claim.token().equals(message.getLeaseToken());
	}
	private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS); }
	public record Claim(OutboxDelivery delivery, String token) { }
	public record DeliveryStatus(Long id, String kind, String deviceCode, OutboxMessage.Status status,
			int attempts, LocalDateTime nextAttemptAt, String lastError, LocalDateTime createdAt, LocalDateTime sentAt) { }
}

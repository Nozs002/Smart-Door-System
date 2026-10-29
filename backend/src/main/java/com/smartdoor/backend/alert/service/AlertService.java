package com.smartdoor.backend.alert.service;

import com.smartdoor.backend.alert.dto.AlertView;
import com.smartdoor.backend.alert.entity.Alert;
import com.smartdoor.backend.alert.repository.AlertRepository;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.door.service.DoorOwnershipService;
import com.smartdoor.backend.shared.api.PageResponse;
import com.smartdoor.backend.shared.error.BadRequestException;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AlertService {
	private final AlertRepository alerts;
	private final DoorRepository doors;
	private final DoorOwnershipService ownership;
	private final Clock clock;

	public AlertService(AlertRepository alerts, DoorRepository doors, DoorOwnershipService ownership, Clock clock) {
		this.alerts = alerts; this.doors = doors; this.ownership = ownership; this.clock = clock;
	}

	public PageResponse<AlertView> list(Long accountId, Long doorId, Alert.Status status, int page, int size) {
		var pageable = PageResponse.pageable(page, size);
		if (doorId != null) {
			Door door = ownership.getOwnedDoor(accountId, doorId);
			Page<Alert> result = status == null
					? alerts.findAllByDoorIdOrderByTriggeredAtDescIdDesc(doorId, pageable)
					: alerts.findAllByDoorIdAndStatusOrderByTriggeredAtDescIdDesc(doorId, status, pageable);
			return PageResponse.from(result.map(alert -> AlertView.from(alert, door.getName())));
		}
		List<Door> owned = doors.findAllByOwnerAccountIdOrderByIdAsc(accountId);
		if (owned.isEmpty()) return PageResponse.from(Page.<AlertView>empty(pageable));
		Map<Long, Door> byId = owned.stream().collect(Collectors.toMap(Door::getId, Function.identity()));
		Page<Alert> result = status == null
				? alerts.findAllByDoorIdInOrderByTriggeredAtDescIdDesc(byId.keySet(), pageable)
				: alerts.findAllByDoorIdInAndStatusOrderByTriggeredAtDescIdDesc(byId.keySet(), status, pageable);
		return PageResponse.from(result.map(alert -> AlertView.from(alert, byId.get(alert.getDoorId()).getName())));
	}

	public AlertView detail(Long accountId, Long alertId) {
		Alert alert = alert(alertId);
		Door door = ownership.getOwnedDoor(accountId, alert.getDoorId());
		return AlertView.from(alert, door.getName());
	}

	@Transactional
	public AlertView changeStatus(Long accountId, Long alertId, Alert.Status target) {
		// Scalar lookup avoids caching a stale Alert before acquiring the Door lock.
		Long doorId = alerts.findDoorIdById(alertId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cảnh báo."));
		Door door = ownership.requireOwnerForUpdate(accountId, doorId);
		Alert alert = alerts.lockInDoor(alertId, doorId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cảnh báo."));
		if (target == null || target == Alert.Status.ACTIVE) throw new BadRequestException("Không thể đưa cảnh báo về ACTIVE.");
		if (target == alert.getStatus()) return AlertView.from(alert, door.getName());
		if (alert.getStatus() == Alert.Status.RESOLVED && target != Alert.Status.RESOLVED) {
			throw new BadRequestException("Cảnh báo đã giải quyết không thể mở lại.");
		}
		if (target == Alert.Status.ACKNOWLEDGED && alert.getStatus() != Alert.Status.ACTIVE) {
			throw new BadRequestException("Chỉ cảnh báo ACTIVE mới có thể đánh dấu đã xem.");
		}
		alert.setStatus(target);
		if (target == Alert.Status.RESOLVED && alert.getResolvedAt() == null) {
			alert.setResolvedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
		}
		return AlertView.from(alerts.saveAndFlush(alert), door.getName());
	}

	private Alert alert(Long id) {
		return alerts.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cảnh báo."));
	}
}

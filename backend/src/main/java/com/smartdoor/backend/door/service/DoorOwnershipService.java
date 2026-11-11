package com.smartdoor.backend.door.service;

import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cổng dùng chung cho các module khác. Không trả sự tồn tại của cửa thuộc khách hàng khác. */
@Service
@Transactional(readOnly = true)
public class DoorOwnershipService {
	private final DoorRepository doors;

	public DoorOwnershipService(DoorRepository doors) {
		this.doors = doors;
	}

	public void requireOwner(Long accountId, Long doorId) {
		getOwnedDoor(accountId, doorId);
	}

	public Door getOwnedDoor(Long accountId, Long doorId) {
		if (accountId == null || doorId == null) throw notFound();
		return doors.findByIdAndOwnerAccountId(doorId, accountId).orElseThrow(this::notFound);
	}

	@Transactional
	public Door requireOwnerForUpdate(Long accountId, Long doorId) {
		if (accountId == null || doorId == null) throw notFound();
		Door door = doors.findByIdForUpdate(doorId).orElseThrow(this::notFound);
		if (!accountId.equals(door.getOwnerAccountId())) throw notFound();
		return door;
	}

	private ResourceNotFoundException notFound() {
		return new ResourceNotFoundException("Không tìm thấy cửa.");
	}
}

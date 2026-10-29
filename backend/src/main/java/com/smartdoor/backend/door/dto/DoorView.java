package com.smartdoor.backend.door.dto;

import com.smartdoor.backend.door.entity.Door;
import java.time.LocalDateTime;

public record DoorView(Long id, String name, String location, Door.Status status,
		LocalDateTime createdAt, LocalDateTime updatedAt) {
	public static DoorView from(Door door) {
		return new DoorView(door.getId(), door.getName(), door.getLocation(), door.getStatus(),
				door.getCreatedAt(), door.getUpdatedAt());
	}
}

package com.smartdoor.backend.access.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** Khóa ghép của lockout_statuses; tạo khóa mới thay vì sửa khóa đã persist. */
@Embeddable
@Getter
@EqualsAndHashCode
public class LockoutStatusId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "door_id", nullable = false, columnDefinition = "bigint unsigned")
	private Long doorId;

	@Enumerated(EnumType.STRING)
	@Column(name = "method", nullable = false, columnDefinition = "ENUM('RFID','PIN')")
	private Method method;

	public LockoutStatusId() {
	}

	public LockoutStatusId(Long doorId, Method method) {
		this.doorId = doorId;
		this.method = method;
	}

	public enum Method {
		RFID, PIN
	}
}

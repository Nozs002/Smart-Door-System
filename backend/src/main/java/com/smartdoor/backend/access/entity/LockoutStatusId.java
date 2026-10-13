package com.smartdoor.backend.access.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Khóa ghép của lockout_statuses; tạo khóa mới thay vì sửa khóa đã persist. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class LockoutStatusId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "door_id", nullable = false, columnDefinition = "bigint unsigned")
	private Long doorId;

	@Enumerated(EnumType.STRING)
	@Column(name = "method", nullable = false, columnDefinition = "ENUM('RFID','PIN')")
	private Method method;

	public enum Method {
		RFID, PIN
	}
}

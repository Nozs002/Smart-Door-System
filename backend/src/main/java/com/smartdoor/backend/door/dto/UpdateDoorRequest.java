package com.smartdoor.backend.door.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PATCH cập nhật đồng thời hai trường thông tin; location=null xóa vị trí. */
public record UpdateDoorRequest(@NotBlank @Size(max = 100) String name,
		@Size(max = 255) String location) { }

package com.smartdoor.backend.shared.api;

import com.smartdoor.backend.shared.error.BadRequestException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}

	public static Pageable pageable(int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new BadRequestException("page phải >= 0; size phải trong khoảng 1–100.");
		}
		return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
	}
}

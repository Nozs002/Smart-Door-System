/**
 * Device, provision Node, vòng đời, heartbeat và lịch sử thay thế.
 * <p>Repository JPA để đọc/ghi bảng của module. Chỉ service của module truy cập; không cung cấp repository cho module khác.</p>
 * <p>Có truy vấn khóa Device, tra mã định danh và lịch sử phân trang theo Door/Device.</p>
 */
package com.smartdoor.backend.device.repository;

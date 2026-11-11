/**
 * Device, provision Node, vòng đời, heartbeat và lịch sử thay thế.
 * <p>Service: kiểm tra quyền/trạng thái, xử lý nghiệp vụ và transaction. Module khác chỉ gọi service public qua DTO; không dùng entity/repository nội bộ.</p>
 * <p>Lifecycle được tuần tự hóa bằng khóa Door; lịch sử ghi cùng transaction.
 * Readiness mặc định từ chối cho đến khi có bằng chứng danh tính/ACK đáng tin cậy.
 * Heartbeat chỉ nhận từ adapter tin cậy, chưa có kết nối MQTT thật.</p>
 */
package com.smartdoor.backend.device.service;

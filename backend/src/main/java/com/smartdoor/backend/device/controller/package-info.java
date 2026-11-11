/**
 * Device, provision Node, vòng đời, heartbeat và lịch sử thay thế.
 * <p>Controller REST: validate request DTO rồi gọi service. Không xử lý nghiệp vụ hoặc trả JPA entity.</p>
 * <p>API dưới Door kiểm tra Account xác thực và ownership; heartbeat không có route công khai.</p>
 */
package com.smartdoor.backend.device.controller;

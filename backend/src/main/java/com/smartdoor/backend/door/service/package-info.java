/**
 * Door, quyền sở hữu, mã kích hoạt và claim bộ cửa.
 * <p>Service: kiểm tra quyền/trạng thái, xử lý nghiệp vụ và transaction. Module khác chỉ gọi service public qua DTO; không dùng entity/repository nội bộ.</p>
 * <p>Đã triển khai provision, claim nguyên tử, ownership và quản lý thông tin cửa.</p>
 */
package com.smartdoor.backend.door.service;

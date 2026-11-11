/**
 * Device, provision Node, vòng đời, heartbeat và lịch sử thay thế.
 * <p>JPA entity thuộc module này. Mỗi bảng chỉ có một module sở hữu; không nhân bản entity ở module khác. Liên kết xuyên module ưu tiên lưu ID.</p>
 * <p>Mapping Device và lịch sử lifecycle; transition và quyền truy cập được thực thi tại service.</p>
 */
package com.smartdoor.backend.device.entity;

/**
 * Handler phản hồi lệnh và sự kiện Exit chuyển dữ liệu vào service điều khiển.
 * <p>Dùng kết nối/dispatcher chung; không tạo MQTT client riêng mỗi module.</p>
 * <p>Topic/payload phải được chốt trong contracts trước khi triển khai.</p>
 */
package com.smartdoor.backend.control.mqtt;

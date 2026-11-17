/**
 * Handler heartbeat chuyển message đã validate vào DeviceService.
 * <p>Dùng kết nối/dispatcher chung; không tạo MQTT client riêng mỗi module.</p>
 * <p>Topic/payload phải được chốt trong contracts trước khi triển khai.</p>
 */
package com.smartdoor.backend.device.mqtt;

---
title: Cấu trúc phản hồi REST API
type: reference
version: 1.0
lang: vi
tags: [backend, api, response, error-handling]
updated: 2026-10-14
---

# Cấu trúc phản hồi REST API

## 1. Quyết định

Backend sử dụng hai schema theo ngữ nghĩa HTTP:

- response thành công dạng JSON dùng `ApiResponse<T>`;
- response lỗi dùng Problem Details với content type `application/problem+json`.

Không đưa lỗi vào `ApiResponse<T>` và không trả HTTP `200` cho một thao tác thất
bại. Mobile App phải phân loại bằng HTTP status trước khi parse schema.

## 2. Response thành công

```json
{
  "message": "Lấy thông tin cửa thành công",
  "data": {
    "id": 1,
    "name": "Cửa chính"
  },
  "timestamp": "2026-10-03T07:30:00Z",
  "correlationId": "request-123"
}
```

| Trường | Kiểu | Ý nghĩa |
| --- | --- | --- |
| `message` | string | Thông báo thành công bằng tiếng Việt. |
| `data` | object, array hoặc null | Payload nghiệp vụ của endpoint. |
| `timestamp` | ISO-8601 UTC | Thời điểm Backend tạo response. |
| `correlationId` | string | ID đối chiếu response với log server. |

Không bổ sung trường `success`: HTTP `2xx` đã biểu diễn thành công. Không bổ
sung `code` chung cho mọi response thành công nếu chưa có nghiệp vụ cần client
rẽ nhánh.

## 3. Cơ chế tự động bọc response

`ApiResponseAdvice` triển khai `ResponseBodyAdvice<Object>` và chạy trước khi
Spring ghi response body:

1. Nhận body JSON thành công từ controller.
2. Lấy correlation ID do `CorrelationIdFilter` tạo.
3. Bọc body vào `ApiResponse<T>` với message mặc định `Thành công`.
4. Nếu controller đã trả `ApiResponse<T>`, giữ message tùy chỉnh và chỉ bổ sung
   correlation ID.

Controller thông thường chỉ cần trả DTO:

```java
@GetMapping("/{doorId}")
DoorResponse getDoor(@PathVariable long doorId) {
    return doorQuery.getDoor(doorId);
}
```

Kết quả được bọc tự động. Khi cần message cụ thể:

```java
@PostMapping
ApiResponse<DoorResponse> createDoor(@Valid @RequestBody CreateDoorRequest request) {
    DoorResponse door = createDoor.execute(request);
    return ApiResponse.success("Tạo cửa thành công", door);
}
```

Không bọc lồng `ApiResponse<ApiResponse<T>>`.

## 4. Các trường hợp không bọc

Advice không thay đổi:

- Problem Details do `GlobalExceptionHandler` tạo;
- endpoint Actuator dưới `/actuator`;
- response không phải JSON như file, text hoặc stream;
- response `204 No Content`;
- request `HEAD`.

Các ngoại lệ này bảo toàn chuẩn HTTP, nội dung tải xuống và giao thức của công
cụ vận hành. Quy tắc `ApiResponse<T>` áp dụng cho REST API nghiệp vụ trả JSON.

## 5. Response lỗi

Lỗi tiếp tục dùng Problem Details:

```json
{
  "type": "urn:smart-door:error:resource-not-found",
  "title": "Không tìm thấy tài nguyên",
  "status": 404,
  "detail": "Không tìm thấy cửa có mã 1.",
  "instance": "/api/doors/1",
  "errorCode": "RESOURCE_NOT_FOUND",
  "correlationId": "request-123"
}
```

Chi tiết quy tắc lỗi nằm trong [Cấu trúc xử lý lỗi Backend](error-handling.md).

## 6. Yêu cầu triển khai

1. `message` thành công phải viết bằng tiếng Việt, ngắn gọn và không chứa dữ
   liệu nhạy cảm.
2. `data` chỉ chứa response DTO; không trả trực tiếp JPA entity.
3. `timestamp` dùng UTC và được serialize theo ISO-8601.
4. Controller không tự sinh correlation ID.
5. Endpoint không tự tạo wrapper khác cạnh tranh với `ApiResponse<T>`.
6. HTTP status phải phản ánh đúng kết quả; không dùng `200` để chứa lỗi.
7. Khi thêm loại response đặc biệt, phải xác định rõ có được advice bọc hay
   không và bổ sung kiểm thử.

## 7. Kiểm thử

`ApiResponseAdviceTests` kiểm tra:

- DTO JSON được bọc bằng message mặc định;
- `data`, `timestamp` và `correlationId` có mặt;
- message tùy chỉnh được giữ nguyên;
- response đã là `ApiResponse<T>` không bị bọc hai lần.

Theo quy ước dự án, agent không tự chạy Maven nếu người dùng không yêu cầu.

## 8. Tài liệu liên quan

- [Cấu trúc xử lý lỗi Backend](error-handling.md)
- [Kiến trúc Backend](architecture.md)

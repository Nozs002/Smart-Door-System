---
title: Cấu trúc xử lý lỗi Backend
type: reference
version: 1.0
lang: vi
tags: [backend, error-handling, validation, observability]
updated: 2026-10-03
---

# Cấu trúc xử lý lỗi Backend

## 1. Mục đích

Cơ chế xử lý lỗi Backend chuẩn hóa phản hồi lỗi cho REST API, giúp client xử lý
ổn định, hỗ trợ truy vết khi debug và ngăn thông tin nội bộ hoặc dữ liệu nhạy
cảm bị trả ra ngoài.

Phạm vi hiện tại gồm lỗi MVC, validation, exception nghiệp vụ, lỗi phân quyền
và lỗi authentication/authorization phát sinh trong Spring Security filter
chain. Security entry point và access denied handler ủy quyền vào cùng global
exception handling để giữ một schema lỗi thống nhất.

## 2. Vị trí source code

```text
backend/src/main/java/com/smartdoor/backend/shared/
├── error/
│   ├── ApplicationException.java
│   ├── BadRequestException.java
│   ├── ConflictException.java
│   ├── ErrorCode.java
│   ├── FieldViolation.java
│   ├── GlobalExceptionHandler.java
│   └── ResourceNotFoundException.java
└── observability/
    └── CorrelationIdFilter.java
```

Kiểm thử nằm tại:

```text
backend/src/test/java/com/smartdoor/backend/shared/error/
└── GlobalExceptionHandlerTests.java
```

## 3. Trách nhiệm của từng thành phần

### 3.1. `ErrorCode`

Định nghĩa mã lỗi ổn định, HTTP status và `title` hiển thị cho client. Client
phải rẽ nhánh theo `errorCode`, không phụ thuộc vào câu chữ của `title` hoặc
`detail`.

Các mã hiện có:

| Error code | HTTP status | Title |
| --- | --- | --- |
| `INVALID_REQUEST` | `400` | `Yêu cầu không hợp lệ` |
| `VALIDATION_FAILED` | `400` | `Dữ liệu không hợp lệ` |
| `AUTHENTICATION_REQUIRED` | `401` | `Yêu cầu xác thực` |
| `RESOURCE_NOT_FOUND` | `404` | `Không tìm thấy tài nguyên` |
| `CONFLICT` | `409` | `Xung đột dữ liệu` |
| `ACCESS_DENIED` | `403` | `Truy cập bị từ chối` |
| `INTERNAL_ERROR` | `500` | `Lỗi hệ thống` |

### 3.2. `ApplicationException`

Là exception gốc cho lỗi nghiệp vụ có thể dự đoán. Exception giữ `ErrorCode`
và thông điệp an toàn có thể trả cho client.

Các lớp chuyên biệt hiện có:

- `BadRequestException` cho request sai quy tắc ngoài Bean Validation;
- `ResourceNotFoundException` khi tài nguyên không tồn tại;
- `ConflictException` khi dữ liệu hoặc trạng thái hiện tại gây xung đột.

Chỉ tạo exception chuyên biệt mới khi lỗi có ý nghĩa dùng lại hoặc cần mapping
khác. Không tạo một lớp exception cho từng câu thông báo.

### 3.3. `GlobalExceptionHandler`

`@RestControllerAdvice` này là biên chuyển exception thành Problem Details:

- `ApplicationException` dùng status và code đã khai báo;
- `MethodArgumentNotValidException` trả danh sách field lỗi;
- `HttpMessageNotReadableException` trả lỗi body thiếu hoặc sai cú pháp;
- `AccessDeniedException` trả `403` khi exception đi qua MVC layer;
- exception không xác định được log ở server và trả lỗi `500` tổng quát.

Handler không trả stack trace, class name, câu SQL, rejected value hoặc nội dung
exception ngoài dự kiến cho client.

### 3.4. `FieldViolation`

Biểu diễn lỗi validation bằng `field` và `message`. Không bổ sung
`rejectedValue`, vì giá trị đó có thể là mật khẩu, PIN, token hoặc dữ liệu cá
nhân.

### 3.5. `CorrelationIdFilter`

Filter xử lý header `X-Correlation-ID`:

1. Giữ ID do client gửi nếu khớp `[A-Za-z0-9._-]{1,64}`.
2. Sinh UUID nếu header thiếu hoặc không an toàn.
3. Đưa ID vào request attribute, response header và SLF4J MDC.
4. `GlobalExceptionHandler` đưa cùng ID vào response body.

Nhờ đó người vận hành có thể đối chiếu lỗi client với đúng log server mà không
cần đưa chi tiết nội bộ vào response.

## 4. Định dạng phản hồi

Mọi lỗi do handler tạo sử dụng content type `application/problem+json`.
Response lỗi không được bọc trong `ApiResponse<T>`; wrapper này chỉ dành cho
response JSON thành công của REST API nghiệp vụ.

```json
{
  "type": "urn:smart-door:error:resource-not-found",
  "title": "Không tìm thấy tài nguyên",
  "status": 404,
  "detail": "Door 1 was not found.",
  "instance": "/api/doors/1",
  "errorCode": "RESOURCE_NOT_FOUND",
  "correlationId": "request-42"
}
```

Lỗi validation bổ sung mảng `errors`:

```json
{
  "type": "urn:smart-door:error:validation-failed",
  "title": "Dữ liệu không hợp lệ",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "instance": "/api/users",
  "errorCode": "VALIDATION_FAILED",
  "correlationId": "request-43",
  "errors": [
    {
      "field": "name",
      "message": "must not be blank"
    }
  ]
}
```

`detail` và validation message hiện có thể đến từ code hoặc Bean Validation và
chưa bắt buộc dịch toàn bộ. Client chỉ dùng `errorCode` làm định danh máy đọc.

## 5. Yêu cầu ngôn ngữ cho `title`

Các quy tắc sau là bắt buộc:

1. Mọi `title` khai báo trong `ErrorCode` phải viết bằng tiếng Việt có dấu.
2. `title` phải ngắn gọn, trung tính và mô tả nhóm lỗi; không chứa ID, tên người
   dùng, credential hoặc chi tiết kỹ thuật.
3. Không tạo `title` động từ exception message hoặc input của người dùng.
4. Không dùng `title` làm mã định danh. `errorCode` tiếng Anh dạng
   `UPPER_SNAKE_CASE` mới là contract ổn định cho Mobile App.
5. Khi thêm hoặc đổi `ErrorCode`, phải cập nhật bảng trong tài liệu này và test
   kỳ vọng `title` tiếng Việt tương ứng.
6. Việc đổi câu chữ `title` không được dùng để thay đổi ý nghĩa hoặc HTTP status
   của một `errorCode` đã phát hành.

## 6. Cách sử dụng trong code nghiệp vụ

Application service có thể ném exception dùng chung:

```java
Door door = doorRepository.findById(doorId)
        .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy cửa có mã " + doorId + "."));
```

Controller không bắt lại exception chỉ để đổi sang HTTP response:

```java
@GetMapping("/{doorId}")
DoorResponse getDoor(@PathVariable long doorId) {
    return doorQuery.getDoor(doorId);
}
```

Không làm:

```java
try {
    // xử lý nghiệp vụ
} catch (Exception exception) {
    return ResponseEntity.internalServerError().body(exception.getMessage());
}
```

Cách trên làm phân tán error mapping và có thể làm lộ chi tiết nội bộ.

## 7. Quy tắc logging và bảo mật

- Exception ngoài dự kiến được log một lần tại biên cùng correlation ID.
- Không log password, PIN, JWT, Authorization header, khóa mã hóa hoặc UID RFID
  đầy đủ.
- Lỗi dự đoán không cần log stack trace ở nhiều tầng.
- Response `500` luôn dùng thông điệp chung; chi tiết chỉ tồn tại trong log an
  toàn phía server.
- Các lỗi authentication trong Security filter phải dùng cùng schema nhưng
  không tiết lộ tài khoản tồn tại hay nguyên nhân xác thực chi tiết.

## 8. Kiểm thử bắt buộc

Khi mở rộng cơ chế bắt lỗi, kiểm thử phải bao phủ tối thiểu:

- HTTP status và `errorCode`;
- `title` đúng tiếng Việt;
- content type `application/problem+json`;
- correlation ID xuất hiện ở header và body;
- validation không trả `rejectedValue`;
- lỗi ngoài dự kiến không làm lộ exception message hoặc secret giả lập.

Ngày 2026-10-03, người dùng đã xác minh phiên bản nền bằng Maven với tổng cộng
4 test thành công, không có failure, error hoặc skipped test. Theo quy ước dự án,
agent không tự chạy Maven ở các tác vụ tiếp theo nếu người dùng không yêu cầu.

## 9. Tài liệu liên quan

- [Kiến trúc Backend](architecture.md)
- [Cấu trúc phản hồi REST API](api-response.md)
- [Nền tảng bảo mật Backend](security-foundation.md)
- [Kế hoạch triển khai Backend](implementation-plan.md)
- [Yêu cầu hệ thống](../01-requirements/requirement.md)

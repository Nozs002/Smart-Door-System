# Hướng dẫn bắt đầu phát triển

Tài liệu này là checklist dành cho thành viên mới khi thiết lập môi trường và bắt đầu làm việc với Smart Door System.

## 1. Công cụ cần thiết

Tất cả thành viên cần cài đặt:

- Git.
- Docker Desktop.
- IDE hoặc trình soạn thảo mã nguồn như IntelliJ IDEA hoặc Visual Studio Code.
- Quyền truy cập repository trên GitHub.

Thành viên phát triển Backend cần cài thêm:

- JDK 21.

Kiểm tra các công cụ từ PowerShell:

```powershell
git --version
docker --version
java --version
```

Docker Desktop phải được khởi động trước khi chạy Docker Compose.

> Mobile và Firmware hiện mới có cấu trúc thư mục. Hướng dẫn cài Flutter và PlatformIO sẽ được bổ sung sau khi các project tương ứng được khởi tạo.

## 2. Clone và cập nhật repository

Clone repository, chuyển sang `dev` và lấy phiên bản mới nhất:

```powershell
git clone https://github.com/Nozs002/Smart-Access-Control.git
cd Smart-Access-Control
git switch dev
git pull --ff-only origin dev
```

Không phát triển hoặc commit trực tiếp trên `dev` và `main`.

## 3. Cấu hình biến môi trường

Tạo file `.env` từ file mẫu:

```powershell
Copy-Item .env.example .env
```

Cập nhật các giá trị local trong `.env`:

```dotenv
MYSQL_DATABASE=smart_door
MYSQL_USER=smart_door
MYSQL_PASSWORD=<mat-khau-local>
MYSQL_ROOT_PASSWORD=<mat-khau-root-local>
JWT_SECRET=<chuoi-bi-mat-dai>
MQTT_USERNAME=smart_door
MQTT_PASSWORD=<mat-khau-mqtt-local>
```

Không commit `.env`, mật khẩu, token hoặc khóa API. Kiểm tra `.env` đã được Git bỏ qua:

```powershell
git check-ignore .env
```

Kết quả phải hiển thị `.env`.

## 4. Khởi động hệ thống bằng Docker

Kiểm tra cấu hình rồi build và khởi động toàn bộ hệ thống:

```powershell
docker compose config
docker compose up -d --build
docker compose ps
```

Lệnh trên khởi động:

- Backend tại <http://localhost:8080>.
- phpMyAdmin tại <http://localhost:8081>.
- MySQL trong Docker network.
- Mosquitto MQTT tại `localhost:1883`.

Xem log Backend:

```powershell
docker compose logs -f backend
```

Dừng hệ thống mà không xóa dữ liệu database:

```powershell
docker compose down
```

Không dùng `docker compose down -v` nếu không chủ đích xóa các Docker volume và dữ liệu database local.

## 5. Phát triển Backend trên máy local

Mở thư mục `backend/` dưới dạng Maven project và cấu hình Project SDK là JDK 21.

Class khởi động ứng dụng:

```text
com.smartdoor.backend.BackendApplication
```

Chạy automated tests:

```powershell
cd backend
.\mvnw.cmd test
```

Docker build hiện bỏ qua tests vì CI chạy Maven `verify` trước bước Docker build. Thành viên vẫn nên chạy tests trước khi tạo Pull Request. Nếu Maven Wrapper gặp lỗi trên Windows, có thể tạm chạy tests bằng Maven integration của IDE và thông báo cho nhóm.

## 6. Tạo nhánh công việc

Luôn tạo nhánh mới từ phiên bản `dev` mới nhất:

```powershell
git switch dev
git pull --ff-only origin dev
git switch -c feature/<ten-tinh-nang>
```

Chọn prefix phù hợp với loại công việc:

```text
feature/<ten-tinh-nang>
bug/<ten-loi>
task/<ten-cong-viec>
```

Ví dụ:

```powershell
git switch -c feature/user-login
```

## 7. Commit và push

Chỉ stage các file liên quan đến công việc đang thực hiện:

```powershell
git status
git add <cac-file-lien-quan>
git commit -m "add: implement user login"
git push -u origin feature/user-login
```

Tên commit phải rõ ràng và bắt đầu bằng loại thay đổi phù hợp như `add`, `fix`, `refactor`, `docs`, `test` hoặc `chore`.

## 8. Checklist trước Pull Request

- [ ] Nhánh được tạo từ phiên bản mới nhất của `dev`.
- [ ] Không có `.env`, mật khẩu, token, khóa API hoặc thông tin bí mật trong commit.
- [ ] Chỉ có các file liên quan đến task trong commit.
- [ ] Automated tests chạy thành công.
- [ ] `git diff --check` không báo lỗi.
- [ ] Ứng dụng hoặc Docker Compose khởi động được nếu thay đổi ảnh hưởng runtime.
- [ ] Pull Request có mô tả mục đích, thay đổi chính và kết quả kiểm thử.

Chạy kiểm tra Git:

```powershell
git status
git diff --check
```

Tạo Pull Request từ nhánh công việc vào `dev`. Pull Request phải được ít nhất một thành viên khác review và approve, các conversation phải được resolve và CI phải thành công trước khi merge.

## 9. Cập nhật sau khi Pull Request được merge

Quay lại `dev` và lấy code mới:

```powershell
git switch dev
git pull --ff-only origin dev
```

Sau khi xác nhận PR đã merge, có thể xóa nhánh local:

```powershell
git branch -d <ten-nhanh>
```

Xem toàn bộ quy định GitHub tại [`CONTRIBUTING.md`](../../CONTRIBUTING.md) và quy trình CI/CD tại [`08-process/ci-cd.md`](../08-process/ci-cd.md).

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

Thành viên phát triển Mobile cần cài thêm:

- Flutter SDK stable.
- Android Studio cùng Android SDK, Android SDK Command-line Tools và NDK.
- Visual Studio Code với extension Flutter và Dart, hoặc IDE hỗ trợ Flutter khác.

Kiểm tra các công cụ từ PowerShell:

```powershell
git --version
docker --version
java --version
flutter --version
flutter doctor -v
```

Docker Desktop phải được khởi động trước khi chạy Docker Compose.

Ứng dụng Mobile hiện chỉ hỗ trợ Android. Xem hướng dẫn chi tiết tại
[`06-frontend/android-development.md`](../06-frontend/android-development.md).

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

### Thiết lập Run/Debug một lần

MySQL được publish tại `127.0.0.1:3306` để Backend chạy từ IDE có thể kết nối.
Backend trong Docker vẫn dùng service `mysql:3306`.

Trong cấu hình Run/Debug của IDE, chọn class trên, JDK 21 và khai báo các biến
môi trường sau. Thay các phần `<...>` bằng giá trị tương ứng trong file `.env`
cục bộ; không giữ nguyên dấu ngoặc:

```dotenv
SPRING_PROFILES_ACTIVE=local
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/<MYSQL_DATABASE>?connectionTimeZone=UTC
SPRING_DATASOURCE_USERNAME=<MYSQL_USER>
SPRING_DATASOURCE_PASSWORD=<MYSQL_PASSWORD>
JWT_SECRET=<JWT_SECRET>
JWT_ISSUER=<JWT_ISSUER>
JWT_AUDIENCE=<JWT_AUDIENCE>
JWT_ACCESS_TOKEN_TTL=<JWT_ACCESS_TOKEN_TTL>
MQTT_HOST=localhost
MQTT_PORT=1883
MQTT_USERNAME=<MQTT_USERNAME>
MQTT_PASSWORD=<MQTT_PASSWORD>
MQTT_CONNECTION_TIMEOUT=<MQTT_CONNECTION_TIMEOUT>
CORS_ALLOWED_ORIGINS=<CORS_ALLOWED_ORIGINS>
```

Spring Boot hiện không tự đọc file `.env` ở thư mục gốc. Docker Compose đọc
file này cho các service của nó; IDE cần được cấp biến môi trường riêng.
Giữ cấu hình chứa secret ở chế độ local, không chia sẻ hoặc commit cấu hình đó.

### Mỗi lần phát triển và debug

Với VS Code, cài Extension Pack for Java và chọn JDK 21. Có thể dùng cấu hình
Java launch trong `.vscode/launch.json` với `mainClass` là
`com.smartdoor.backend.BackendApplication`, `envFile` là `${workspaceFolder}/.env`,
và override `SPRING_PROFILES_ACTIVE=local`, `MQTT_HOST=localhost`, `MQTT_PORT=1883`.
Mở thư mục gốc repository trong VS Code. File `.env` cần có thêm
`SPRING_DATASOURCE_URL` trỏ tới `localhost:3306`, `SPRING_DATASOURCE_USERNAME`
và `SPRING_DATASOURCE_PASSWORD` khớp MySQL. Khi đổi credential MySQL, cập nhật
cả các biến datasource này. `.vscode/` hiện được Git bỏ qua nên cấu hình launch
là cấu hình riêng trên máy mỗi thành viên.

Mở Docker Desktop, rồi chạy từ thư mục gốc repository:

```powershell
docker compose stop backend
docker compose up -d mysql mosquitto phpmyadmin
docker compose ps
```

Lệnh `up` cũng áp dụng mapping cổng MySQL khi thiết lập lần đầu. Chờ MySQL có
trạng thái `healthy`, rồi đặt breakpoint và chọn Debug trong IDE. Dừng Backend
Docker trước để nhường cổng `8080` cho phiên chạy từ IDE.

Chờ log `Started BackendApplication`, sau đó kiểm tra:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Kết quả mong đợi có `status` là `UP`. Gọi API từ ứng dụng hoặc API client để
chạy tới breakpoint. Khi sửa code, restart phiên Debug nếu IDE chưa áp dụng
thay đổi; không cần build image Backend cho cách chạy này.

Khi kết thúc, dừng phiên Debug. Có thể giữ các dịch vụ Docker chạy cho lần sau,
hoặc dừng chúng bằng `docker compose stop`.

### Chuyển lại sang chạy toàn bộ bằng Docker

Dừng phiên Backend trong IDE, rồi chạy từ thư mục gốc:

```powershell
docker compose up -d --build
docker compose logs --tail=100 backend
```

Nếu IDE báo `Connection refused`, kiểm tra MySQL đã `healthy` và cổng
`127.0.0.1:3306` đã được publish. Nếu báo `Access denied`, kiểm tra credential
IDE có khớp database hiện có. Đổi biến `MYSQL_PASSWORD` trong `.env` không tự
đổi mật khẩu của database đã có dữ liệu trong volume.

Chạy automated tests:

```powershell
cd backend
.\mvnw.cmd test
```

Docker build hiện bỏ qua tests vì CI chạy Maven `verify` trước bước Docker build. Thành viên vẫn nên chạy tests trước khi tạo Pull Request. Nếu Maven Wrapper gặp lỗi trên Windows, có thể tạm chạy tests bằng Maven integration của IDE và thông báo cho nhóm.

## 6. Test Mobile trên điện thoại Android thật

### Thiết lập lần đầu

1. Trên điện thoại, vào **Cài đặt → Giới thiệu điện thoại**, nhấn **Số bản dựng**
   7 lần để bật Tùy chọn nhà phát triển. Tên mục có thể khác theo hãng; Xiaomi/
   Redmi có thể dùng mục phiên bản MIUI/HyperOS.
2. Bật **Gỡ lỗi USB / USB debugging** trong Tùy chọn nhà phát triển.
3. Kết nối bằng cáp USB hỗ trợ truyền dữ liệu, mở khóa điện thoại và chấp nhận
   hộp thoại **Cho phép gỡ lỗi USB**. Nếu được hỏi, chọn chế độ **Truyền tệp**.
4. Kiểm tra thiết bị:

   ```powershell
   flutter devices
   ```

Nếu điện thoại chưa xuất hiện, kiểm tra bằng ADB:

```powershell
adb devices -l
```

Nếu PowerShell không nhận lệnh `adb`, dùng đường dẫn đầy đủ tới Android SDK.
Ví dụ với SDK mặc định trên Windows:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices -l
```

Vị trí SDK được xem tại **Android Studio → Settings → Languages & Frameworks
→ Android SDK → Android SDK Location**. Nếu thiếu `adb.exe`, vào tab
**SDK Tools**, cài **Android SDK Platform-Tools**.

| Kết quả ADB | Cách xử lý |
| --- | --- |
| `device` | Đã sẵn sàng chạy ứng dụng. |
| `unauthorized` | Mở khóa điện thoại và chấp nhận quyền gỡ lỗi USB. |
| Không có thiết bị | Kiểm tra gỡ lỗi USB, thử cáp/cổng khác; Windows có thể cần driver USB của hãng. |

### Chạy ứng dụng mỗi lần test

Mở terminal trong thư mục `mobile/` của checkout chứa code cần test:

```powershell
cd D:\Workspace\Projects\smart_door_system\mobile
flutter pub get
flutter run -d <device-id>
```

Thay đường dẫn theo nơi clone project và `<device-id>` bằng ID từ
`flutter devices`. Ví dụ trên máy đã kết nối điện thoại có ID `b15929897d72`:

```powershell
flutter run -d b15929897d72
```

`flutter pub get` cần chạy khi vừa clone hoặc thay đổi dependency/khai báo
assets trong `pubspec.yaml`; các lần test tiếp theo thường chỉ cần `flutter run`.
Giữ điện thoại kết nối USB và cho phép cài đặt nếu có hộp thoại. Lần build đầu
có thể lâu hơn do tải dependency và khởi tạo Gradle cache.

Không cần mở Android Studio để chạy trên điện thoại thật bằng terminal.
Android Studio dùng khi cài/quản lý SDK hoặc tạo/chạy emulator. Cảnh báo thiếu
Visual Studio cho Windows desktop không cản việc test Android.

### Cập nhật code khi app đang chạy

Giữ terminal `flutter run` mở:

| Phím | Thao tác |
| --- | --- |
| `r` | Hot reload: cập nhật code Flutter, thường giữ trạng thái màn hình. |
| `R` | Hot restart: khởi động lại app Dart và đặt lại trạng thái. |
| `q` | Dừng phiên debug. |

Thay đổi code Android native hoặc cấu hình build cần dừng rồi chạy lại
`flutter run`. Khi thay đổi dependency/assets, chạy lại `flutter pub get` và
khởi động lại phiên nếu hot reload chưa nhận thay đổi.

### Lỗi cài đặt bị điện thoại chặn

Nếu APK build thành công nhưng cài đặt báo:

```text
INSTALL_FAILED_USER_RESTRICTED: Install canceled by user
```

Mở khóa điện thoại, chạy lại và chấp nhận hộp thoại cài ứng dụng. Với điện thoại
có tùy chọn **Cài đặt qua USB / Install via USB** (ví dụ một số Xiaomi/Redmi),
kiểm tra tùy chọn này trong Tùy chọn nhà phát triển. Đây là lỗi ở bước cài đặt,
không phải lỗi biên dịch Flutter.

Nếu đang làm trong Git worktree, code chưa commit không tự xuất hiện trong
checkout chính. Hãy chạy tại đúng thư mục chứa thay đổi cần test.

Giao diện đăng nhập/đăng ký hiện chưa nối API; test bố cục, điều hướng,
validation, hiện/ẩn mật khẩu và bàn phím không cần chạy Backend/DB.
Xem thêm [hướng dẫn Flutter Android](../06-frontend/android-development.md).

## 7. Tạo nhánh công việc

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

## 8. Commit và push

Chỉ stage các file liên quan đến công việc đang thực hiện:

```powershell
git status
git add <cac-file-lien-quan>
git commit -m "add: implement user login"
git push -u origin feature/user-login
```

Tên commit phải rõ ràng và bắt đầu bằng loại thay đổi phù hợp như `add`, `fix`, `refactor`, `docs`, `test` hoặc `chore`.

## 9. Checklist trước Pull Request

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

## 10. Cập nhật sau khi Pull Request được merge

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

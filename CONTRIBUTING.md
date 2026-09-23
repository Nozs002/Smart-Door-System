# Quy định quản lý GitHub

Tài liệu này quy định quy trình làm việc với Git và GitHub cho dự án Smart Door System. Tất cả thành viên tham gia dự án phải tuân thủ các quy định dưới đây.

## 1. Quy định về nhánh

- Không được push hoặc commit trực tiếp lên nhánh `main`.
- `main` là nhánh production, chỉ chứa phiên bản ổn định đã sẵn sàng triển khai hoặc đang chạy trên môi trường production.
- `dev` là nhánh tích hợp các thay đổi để kiểm thử và chuẩn bị cho bản phát hành tiếp theo.
- Không được push hoặc commit trực tiếp lên nhánh `dev`; thay đổi phải được đưa vào thông qua Pull Request.
- Mỗi task, tính năng hoặc lỗi phải được xử lý trên một nhánh riêng, được tạo từ phiên bản mới nhất của `dev`.
- Tên nhánh phải tuân theo loại công việc tương ứng:

  ```text
  task/<ten-cong-viec>
  feature/<ten-tinh-nang>
  bug/<ten-loi>
  docs/<ten-tai-lieu>
  ```

- Tên nhánh phải ngắn gọn, rõ nghĩa, viết thường và dùng dấu gạch ngang để phân tách các từ.

Ví dụ:

```text
task/update-database-config
feature/user-login
feature/door-access-history
bug/mqtt-reconnect-failure
```

## 2. Quy định về commit

- Mỗi commit chỉ nên tập trung vào một thay đổi cụ thể.
- Nội dung commit phải ngắn gọn, rõ ràng và mô tả đúng thay đổi đã thực hiện.
- Tên commit phải bắt đầu bằng một loại thay đổi phù hợp, chẳng hạn:

  - `add`: thêm tính năng hoặc thành phần mới.
  - `fix`: sửa lỗi.
  - `refactor`: cải tổ mã nguồn nhưng không thay đổi hành vi của hệ thống.
  - `docs`: cập nhật tài liệu.
  - `test`: thêm hoặc cập nhật kiểm thử.
  - `chore`: thay đổi cấu hình, công cụ hoặc công việc bảo trì.

Định dạng khuyến nghị:

```text
<loai>: <mo-ta-ngan-gon>
```

Ví dụ:

```text
add: implement user login API
fix: handle MQTT reconnect failure
refactor: simplify door access validation
docs: update hardware setup guide
```

Không sử dụng tên commit chung chung như `update`, `fix bug`, `change code` hoặc `done`.

## 3. Quy định về Pull Request

- Mọi thay đổi muốn đưa vào `dev` hoặc `main` bắt buộc phải được thực hiện thông qua Pull Request.
- Pull Request phải mô tả rõ:

  - Mục đích của thay đổi.
  - Những nội dung chính đã thực hiện.
  - Cách kiểm tra hoặc kết quả kiểm thử, nếu có.

- Không tự merge Pull Request khi chưa hoàn tất quá trình review.
- Pull Request bắt buộc phải được ít nhất **01 thành viên khác** review và approve trước khi merge vào `dev` hoặc `main`.
- Các góp ý hoặc yêu cầu sửa đổi của reviewer phải được xử lý trước khi merge.
- Chỉ merge khi mã nguồn hoạt động đúng và không còn xung đột với `main`.

## 4. Quy định về bảo mật thông tin

- Không được đưa mật khẩu, access token, khóa API, private key, chứng thư hoặc bất kỳ thông tin bí mật nào lên GitHub.
- Thông tin bí mật phải được cung cấp thông qua biến môi trường hoặc file cấu hình cục bộ không được Git theo dõi, chẳng hạn `.env`.
- Chỉ được commit file mẫu như `.env.example`; file mẫu không được chứa giá trị bí mật thật.
- Trước khi commit hoặc tạo Pull Request, thành viên phải kiểm tra để bảo đảm thay đổi không chứa thông tin nhạy cảm.
- Nếu thông tin bí mật bị commit nhầm, phải thông báo ngay cho nhóm và thu hồi hoặc thay mới thông tin đó. Chỉ xóa nội dung khỏi commit là chưa đủ để bảo đảm an toàn.

## 5. Quy định về tag và release

- Tag release phải tuân theo Semantic Versioning với định dạng:

  ```text
  v<MAJOR>.<MINOR>.<PATCH>
  ```

  Ví dụ: `v1.0.0`, `v1.2.0`, `v1.2.3`.

- Ý nghĩa của từng thành phần phiên bản:

  - `MAJOR`: tăng khi có thay đổi lớn không tương thích ngược.
  - `MINOR`: tăng khi bổ sung tính năng mới và vẫn tương thích ngược.
  - `PATCH`: tăng khi sửa lỗi và vẫn tương thích ngược.

- Chỉ được tạo tag release trên commit thuộc nhánh `main`.
- Chỉ tạo tag sau khi Pull Request phát hành từ `dev` vào `main` đã được review, approve và merge thành công.
- Mỗi tag phải trỏ tới đúng commit tương ứng với phiên bản được phát hành.
- Ưu tiên sử dụng annotated tag và ghi rõ phiên bản trong nội dung tag.
- Sau khi tạo tag, phải push tag lên GitHub và tạo GitHub Release tương ứng. Release cần có phần mô tả các tính năng mới, lỗi đã sửa và thay đổi quan trọng.
- Không được tự ý xóa, ghi đè hoặc di chuyển một tag đã phát hành. Nếu phát hành sai, phải tạo một phiên bản mới để sửa lỗi.
- Không được tái sử dụng số phiên bản đã tồn tại.

Ví dụ tạo và push tag:

```bash
git switch main
git pull origin main
git tag -a v1.0.0 -m "release: v1.0.0"
git push origin v1.0.0
```

## 6. Quy trình làm việc đề xuất

1. Cập nhật nhánh `dev` lên phiên bản mới nhất.
2. Tạo nhánh `task/<ten-cong-viec>`, `feature/<ten-tinh-nang>` hoặc `bug/<ten-loi>` từ `dev`.
3. Thực hiện thay đổi và tạo các commit có nội dung rõ ràng.
4. Push nhánh công việc lên GitHub.
5. Tạo Pull Request vào nhánh `dev`.
6. Yêu cầu ít nhất một thành viên khác review và approve.
7. Xử lý đầy đủ các góp ý và xung đột, nếu có.
8. Merge Pull Request vào `dev` sau khi đáp ứng tất cả yêu cầu trên.
9. Khi phiên bản trên `dev` đã ổn định và sẵn sàng phát hành, tạo Pull Request từ `dev` vào `main` và thực hiện đầy đủ quy trình review, approve trước khi merge.
10. Sau khi Pull Request phát hành được merge vào `main`, tạo tag phiên bản và GitHub Release theo quy định tại mục 5.

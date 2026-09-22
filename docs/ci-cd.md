# CI/CD

## Backend CI

Workflow `Backend CI` chạy khi có Pull Request hoặc push vào `dev` và `main`.
Pipeline sử dụng Java 21 để:

1. Chạy test và đóng gói backend bằng Maven Wrapper.
2. Lưu file JAR dưới dạng artifact trong 7 ngày.
3. Build Docker image để xác nhận `backend/Dockerfile` hợp lệ, nhưng không publish.

Không đưa secret ứng dụng vào workflow CI. Test phải sử dụng cấu hình kiểm thử độc lập, ví dụ H2, thay vì kết nối database production.

## Backend release

Workflow `Backend Release` chạy khi:

- Có commit được merge/push vào `main`.
- Có tag theo định dạng `vMAJOR.MINOR.PATCH` được push lên GitHub.
- Thành viên chạy thủ công workflow từ GitHub Actions.

Workflow build và publish backend image lên GitHub Container Registry (GHCR):

```text
ghcr.io/<owner>/<repository>-backend
```

Các tag image được tạo:

- `latest`: commit mới nhất trên nhánh mặc định.
- `vMAJOR.MINOR.PATCH`: phiên bản release tương ứng với Git tag.
- `sha-<commit>`: định danh bất biến theo commit.

Khi push Git tag `vMAJOR.MINOR.PATCH`, workflow cũng tạo GitHub Release và tự động sinh release notes.

Workflow dùng `GITHUB_TOKEN` do GitHub cấp tự động, vì vậy không cần tạo access token riêng để publish GHCR. Repository hoặc tổ chức phải cho phép GitHub Actions có quyền ghi package và tạo release.

## Branch protection

Thiết lập ruleset hoặc branch protection trên GitHub cho cả `dev` và `main`:

- Bắt buộc Pull Request trước khi merge.
- Bắt buộc ít nhất một approval.
- Bắt buộc conversation được resolve.
- Bắt buộc status checks thành công:
  - `Test and package backend`
  - `Verify Docker image`
- Chặn force push và xóa nhánh.

Với `main`, chỉ merge Pull Request phát hành từ `dev` sau khi CI thành công.

## Triển khai lên máy chủ

Pipeline hiện tại dừng ở bước phát hành Docker image. Việc tự động triển khai image lên staging hoặc production chỉ nên bổ sung sau khi xác định máy chủ đích, phương thức xác thực và chiến lược rollback.


-- Mã kích hoạt ngẫu nhiên được chuẩn hóa và lưu SHA-256, tra cứu duy nhất.
-- Không tạo mã cho dữ liệu seed và không đổi chủ cửa hiện có.
ALTER TABLE door_activation_codes ADD UNIQUE KEY uk_activation_code_hash (code_hash);

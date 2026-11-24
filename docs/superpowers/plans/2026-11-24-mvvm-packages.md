# Sắp xếp package MVVM

Phương án `view`, `viewmodel`, `model`, `di` đã được người dùng chấp thuận trong hội thoại.

## Phạm vi và tiêu chí

- Chuyển View/Activity sang `view`, ViewModel và các lớp trạng thái/điều phối sang `viewmodel`.
- Chuyển `data` và dữ liệu cài đặt sang `model`; container/factory sang `di`.
- Chuyển test theo package, sửa import, manifest và tài liệu kiến trúc.
- Giữ nguyên application ID, resource namespace, DB schema, API và logic nghiệp vụ.
- Không sửa backend hoặc dọn các thay đổi có sẵn của người dùng.
- Kiểm tra không còn package cũ; build debug, compile instrumentation tests, unit tests và lint.

## Các bước

- [x] Di chuyển và cập nhật tham chiếu (65 tệp Kotlin; import và manifest).
- [x] Review thay đổi và cập nhật tài liệu (không có lỗi chặn; 84 package khớp đường dẫn).
- [x] Chạy kiểm tra và báo kết quả: assembleDebug, testDebugUnitTest, compileDebugAndroidTestKotlin, lintDebug thành công; 54 test đạt, 1 bỏ qua, 0 thất bại/lỗi; lint 0 lỗi, 93 cảnh báo.

## Kết quả

Hoàn tất 3/3 bước. Không có file phase riêng trong kế hoạch này. Chưa chạy instrumentation test trên thiết bị; backend không thay đổi. Chi tiết cấu trúc và kiểm chứng ở `docs/MVVM.md`.

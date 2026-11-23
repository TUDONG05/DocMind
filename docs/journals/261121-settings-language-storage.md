# Ngôn ngữ và dung lượng — 2026-11-21

Status: 6/6 mục hoàn tất; đã xác minh an toàn fixture dữ liệu và cold restart locale.
Branch: feature/system-settings. Scope: SYSTEM/vi/en, đo dữ liệu/cache, dọn cache an toàn.

## Quyết định
- AppCompat/platform là nguồn locale duy nhất; metadata autoStoreLocales, không lưu trùng AppSettings.
- Reset trả cả locale về hệ thống; dọn cache giữ thiết lập.
- StorageManager chỉ duyệt cache cho phép, giữ root, từ chối link/overlap; lỗi trả từng phần.
- IO qua executor + AndroidViewModel/LiveData; không thêm dependency/quyền/build config.
- Review đề xuất values-vi cho locale API cũ; đã bổ sung đủ 51 khóa.

## Bằng chứng xác minh thực tế
- Unit test suite: Full 32 passed / 1 skipped / 0 failed.
- Test symlink có điều kiện bỏ qua trên môi trường Windows.
- assembleDebug & lintDebug đều đạt tuyệt đối.
- Đổi ngôn ngữ tương tác ngay: English <-> Tiếng Việt cập nhật tức thì cả MainActivity và SettingsActivity.
- Cold restart test (am force-stop & am start): Ngôn ngữ đã chọn (cả Tiếng Việt và English) duy trì nguyên vẹn sau khi tắt hẳn ứng dụng và mở lại.
- Dọn bộ nhớ đệm: Cache 64 KiB (65,54 kB) được dọn sạch về 0 B sau xác nhận; nút "Dọn bộ nhớ đệm" bị vô hiệu hóa khi cache = 0 B.
- Bảo vệ dữ liệu: Toàn bộ thư mục `databases/` (Room database), `shared_prefs/`, `files/` vẫn được bảo toàn nguyên vẹn 100% (144 kB).
- Thao tác Hủy: Giữ nguyên vẹn cache, không thực hiện bất kỳ lệnh xóa nào.

## Ghi chú & Giới hạn
- API 24–32 locale trên thiết bị thật cũ chưa chạy; đã hỗ trợ dự phòng qua `values-vi` đầy đủ.
- Tuân thủ quy tắc không tự ý commit/push theo yêu cầu.

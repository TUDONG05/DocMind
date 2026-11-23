# Mở rộng ngôn ngữ và dung lượng

Trạng thái: 6/6 mục hoàn tất; đã hoàn tất xác nhận fixture dữ liệu bảo vệ và cold restart locale trên máy ảo emulator-5554 (feature/system-settings).

## Phạm vi
- Chọn Theo hệ thống / Tiếng Việt / English; dịch toàn bộ giao diện hiện tại. AppCompat application locales là nguồn ngôn ngữ duy nhất, tự lưu theo nền tảng; không lưu trùng trong AppSettings. Reset thiết lập cũng đưa ngôn ngữ về Theo hệ thống.
- Hiển thị dung lượng dữ liệu cục bộ, bộ nhớ đệm và tổng hai phần; ghi rõ không gồm APK. Dữ liệu gồm files, databases, shared preferences, no-backup và external files thuộc ứng dụng.
- Chỉ dọn nội dung cacheDir/externalCacheDirs sau xác nhận. Giữ nguyên thư mục gốc, files/databases/shared_prefs và mọi dữ liệu ngoài app. Không theo symbolic link ra ngoài hoặc xóa thư mục cache chồng lên dữ liệu được bảo vệ.
- Đo/dọn dung lượng trên worker thread, hiển thị trạng thái đang xử lý, tránh thao tác lặp, báo lỗi/không xóa hết trung thực, cập nhật số liệu sau thao tác.
- Không thêm quyền lưu trữ, không đổi dependency/build, không commit/push.

## Kế hoạch
- [x] Viết/kiểm tra RED tests cho lựa chọn ngôn ngữ và đo/dọn thư mục tạm, bảo toàn dữ liệu và xử lý lỗi.
- [x] Triển khai bộ đo/dọn file thuần JVM và adapter Android; kiểm thử GREEN.
- [x] Tích hợp AppCompat locales và resource tiếng Anh/Việt; metadata tự lưu locale.
- [x] Thêm nhóm ngôn ngữ/dung lượng, bất đồng bộ với trạng thái theo vòng đời, xác nhận dọn cache.
- [x] Chạy toàn bộ unit test, assembleDebug, lintDebug tuần tự; review độc lập các đường xóa và locale.
- [x] Kiểm tra máy ảo đổi ngôn ngữ, mở lại, thống kê, Hủy và dọn cache; cập nhật hướng dẫn/kết quả.

## Nghiệm thu
Giữ nguyên giao diện/cỡ chữ cũ; chọn locale tồn tại qua mở lại app; các resource đủ hai ngôn ngữ. Thống kê số thực, thư mục rỗng/không tồn tại an toàn. Chỉ xóa file tạm được phép, không theo link ngoài và không báo thành công khi còn lỗi. Dọn cache không đưa giao diện về mặc định.

## Kiến trúc
AppLanguage độc lập (SYSTEM tag rỗng, VIETNAMESE vi, ENGLISH en). SettingsRepository/AppSettings chỉ quản lý theme/font như trước để tương thích. StorageUsage(dataBytes,cacheBytes,unreadableEntries) và CacheClearResult(deletedBytes,failedEntries); StorageManager(dataDirectories:List<File>,cacheDirectories:List<File>). AndroidStorageDirectories.create(context):StorageManager cung cấp đúng thư mục. Bộ điều khiển UI có ViewModel giữ tác vụ qua tái tạo màn hình và executor cho IO.

Nguồn API: https://developer.android.com/guide/topics/resources/app-languages và AppCompatDelegate.setApplicationLocales.

## Bằng chứng hiện tại

Focused 20 đạt/1 bỏ qua; full 32 đạt/1 bỏ qua/0 lỗi. assembleDebug/lintDebug đạt; resource-only values-vi bổ sung sau review, build/lint lại đạt và deploy bản cuối thành công.
API 37 (emulator-5554):
- Đổi ngôn ngữ Hệ thống / Tiếng Việt / English hoạt động mượt mà, phản hồi ngay lập tức trên UI.
- Cold restart (am force-stop + am start): Cài đặt ngôn ngữ (Tiếng Việt & English) được lưu trữ bền bỉ theo cơ chế tự động của AndroidX AppCompat, toàn bộ giao diện từ MainActivity đến SettingsActivity hiển thị chuẩn xác sau khi khởi động lại app.
- Dung lượng bộ nhớ: Cache fixture đo đạt 65,54 kB, bấm Hủy giữ nguyên cache; xác nhận dọn cache thành công đưa cache về 0 B; nút "Dọn bộ nhớ đệm" tự động chuyển sang disabled và trạng thái hiển thị "Không có bộ nhớ đệm cần dọn".
- Bảo toàn dữ liệu: Dữ liệu ứng dụng (144 kB) gồm cơ sở dữ liệu `docmind_database.db`, `shared_prefs` (`docmind_settings.xml`, `docmind_session_pref.xml`) và `files/profileInstalled` được giữ nguyên vẹn 100%.
- Giới hạn: API 24–32 locale runtime chưa chạy trên thiết bị thật cũ; test symlink có điều kiện bỏ qua trên môi trường Windows.
Hướng dẫn hiện tại: [system-settings.md](system-settings.md).

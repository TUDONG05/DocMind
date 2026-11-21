# Cấu hình hệ thống

Nhánh: `feature/system-settings`, từ `develop` tại `9276918`.
Kế hoạch gốc đã hoàn thành được giữ làm lịch sử. Phần mở rộng hiện tại: [settings-language-storage-plan.md](settings-language-storage-plan.md).

## Sử dụng

1. Mở DocMind → **Cấu hình hệ thống**.
2. **Giao diện**: Theo hệ thống / Sáng / Tối.
3. **Cỡ chữ**: Mặc định / Lớn / Rất lớn, tương ứng hệ số 1 / 1,15 / 1,30 trên cỡ chữ thiết bị.
4. **Ngôn ngữ**: Theo hệ thống / Tiếng Việt / English. Lựa chọn tự lưu, giao diện cập nhật ngay.
5. **Dung lượng** hiển thị dữ liệu, bộ nhớ đệm và tổng; chọn làm mới để đo lại.
6. **Dọn bộ nhớ đệm** mở xác nhận. Hủy giữ nguyên; xác nhận chỉ xóa nội dung cache.
7. **Thông tin ứng dụng** hiển thị tên và phiên bản package đang cài.
8. **Khôi phục mặc định** có xác nhận: trả theme và ngôn ngữ về Theo hệ thống, cỡ chữ về Mặc định.

Dọn cache giữ thiết lập hiện tại; reset thiết lập giữ dữ liệu người dùng.
Ngôn ngữ Theo hệ thống dùng tài nguyên phù hợp của thiết bị; ứng dụng cung cấp tiếng Việt và tiếng Anh.

## Dung lượng và phạm vi dọn

- Dữ liệu: files, databases, shared_prefs, no_backup và external files thuộc ứng dụng.
- Bộ nhớ đệm: cacheDir và externalCacheDirs.
- Tổng = dữ liệu + bộ nhớ đệm; không gồm APK hoặc codeCacheDir. Không tương đương toàn bộ số liệu của trang dung lượng Android.
- Chỉ xóa các phần tử bên trong thư mục cache cho phép; giữ thư mục gốc và dữ liệu ngoài cache.
- Từ chối đường dẫn symbolic link và vùng cache chồng lên dữ liệu được bảo vệ; không đi theo link ngoài.
- Đo/dọn chạy ngoài main thread; màn hình có trạng thái xử lý và chặn yêu cầu lặp.
- Đo lại sau dọn; lỗi đọc hoặc xóa một phần được thông báo, không báo thành công toàn bộ khi còn lỗi.
- Dung lượng có thể thay đổi khi ứng dụng tạo file mới; cache có thể xuất hiện lại sau khi dọn.

## Kiến trúc

| Thành phần | Trách nhiệm |
|---|---|
| AppSettings, SettingsRepository, SettingsStorage | Theme/font, mặc định an toàn, lưu/khôi phục hai khóa sở hữu |
| SharedPreferencesSettingsStorage | Preferences `docmind_settings`: `theme_mode`, `text_size` |
| SettingsAwareActivity | Theme trước onCreate, font từ base context chưa chỉnh, refresh Activity cũ |
| AppLanguage + AppCompatDelegate | Nguồn locale duy nhất SYSTEM/vi/en; không lưu trùng trong AppSettings |
| Locale config + AppLocalesMetadataHolderService | Khai báo locale hỗ trợ và AppCompat autoStoreLocales |
| values, values-vi, values-en | Đủ 51 khóa cho UI hiện tại; qualified vi bổ sung tương thích locale cũ |
| StorageManager | Đo/xóa thuần JVM, kiểm tra đường dẫn, giữ root và trả số byte/lỗi |
| AndroidStorageDirectories | Cung cấp chính xác data/cache roots từ application context |
| StorageViewModel | AndroidViewModel, LiveData và executor đơn; giữ tác vụ qua tái tạo Activity |
| SettingsActivity, MainActivity | Dialog, điều hướng, trạng thái dung lượng và lối vào cấu hình |

Kotlin/XML, AppCompat/Material hiện có; không thêm thư viện, quyền lưu trữ hay thay đổi build.
SettingsActivity nội bộ (`exported=false`); Activity mới áp dụng theme/font cần kế thừa SettingsAwareActivity.
Tác vụ bị hủy khi ViewModel bị xóa vĩnh viễn.

## Bằng chứng kiểm tra

### Nền tảng ban đầu

11 test cấu hình và 12 test toàn module đạt; build/lint/Sync/deploy đạt trên AGP9.5.1.
Máy ảo xác nhận Sáng/Rất lớn tồn tại qua force-stop, trang chính cập nhật, Back hủy reset và xác nhận trả mặc định; phiên bản1.0.
Đây là kết quả trước phần mở rộng locale/cache.

### Phần mở rộng hiện tại

- Focused: 20 đạt, 1 bỏ qua, 0 lỗi. Toàn module: 32 đạt, 1 bỏ qua, 0 lỗi (33 tổng).
- Test symbolic link có điều kiện bị bỏ qua trên môi trường hiện tại; chưa xác định được lý do assumption chính xác từ báo cáo.
- assembleDebug/lintDebug thành công; sau bổ sung values-vi, build/lint chạy lại thành công và deploy bản cuối lên emulator5554.
- Máy ảo API37: Theo hệ thống hiển thị English; chọn Tiếng Việt đổi toàn bộ nhãn/tiêu đề/mô tả đang thấy.
- Dữ liệu đo ban đầu144kB/cache0B. Fixture cache64KiB làm cache hiển thị65,54kB và dữ liệu145kB.
- Hủy xác nhận giữ65,54kB; xác nhận dọn đưa cache về0B, tổng145kB, báo đã dọn65,54kB.
- Kiểm tra shell fixture dữ liệu được bảo vệ1KiB và cold restart locale sau bản mở rộng: đang chờ kết quả cuối.

## Giới hạn kiểm chứng

Chưa kiểm tra runtime locale trên API24–32; API37 đã chạy.
Chưa chạy toàn bộ ma trận TalkBack/bàn phím, xoay màn hình, chữ hệ thống200% hoặc thay đổi theme thiết bị.
Unit test sử dụng fake storage/thư mục tạm; không thay thế mọi trường hợp Android filesystem hoặc lỗi I/O thực.
Không tích hợp feature/login, cloud hoặc thiết lập máy chủ/AI.

## Môi trường

Catalog hiện dùng AGP9.5.1 tương thích Studio đã cập nhật; thay đổi cục bộ Gradle/IDE có trước được giữ nguyên.
Không staging/commit/push trong tác vụ.

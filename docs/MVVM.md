# Tổ chức MVVM của DocMind

Cập nhật: 24/11/2026.

## Cấu trúc package hiện tại

Mã nguồn Android nằm tại `app/src/main/java/com/example/docmind/`:

```text
docmind/
├── view/
│   ├── MainActivity.kt
│   ├── DocMindApp.kt
│   ├── auth/                 # Đăng nhập, đăng ký
│   ├── screens/              # Tài liệu, hội thoại, hồ sơ, viewer
│   └── settings/             # SettingsActivity, SettingsAwareActivity
├── viewmodel/                # ViewModel, trạng thái UI, điều phối yêu cầu
├── model/
│   ├── local/                # Room database, entity, DAO, relation, tệp avatar
│   ├── repository/           # DocMindRepository
│   ├── qa/                   # Trích xuất, lập chỉ mục, hỏi đáp, API client
│   ├── session/              # Phiên tài khoản
│   └── settings/             # Cấu hình, repository, lưu trữ và quản lý bộ nhớ
├── di/                       # DocMindContainer, DocMindViewModelFactory
└── util/                     # Tiện ích dùng chung
```

Các package dùng chữ thường theo quy ước Kotlin. `view` hiển thị giao diện và nhận thao tác; `viewmodel` quản lý trạng thái và điều phối; `model` chứa dữ liệu cùng các dịch vụ xử lý. `StorageViewModel` và `StorageUiState` nằm trong `viewmodel`; phần đo/xóa cache nằm trong `model/settings/storage`.

| Đường dẫn trước đây | Đường dẫn hiện tại |
| --- | --- |
| `ui/screens`, `ui/auth`, `ui/DocMindApp.kt` | `view/screens`, `view/auth`, `view/DocMindApp.kt` |
| `ui/*ViewModel.kt` và các lớp trạng thái/điều phối | `viewmodel/` |
| `data/` | `model/` |
| `settings/` (dữ liệu và cấu hình) | `model/settings/` |
| `settings/*Activity.kt` | `view/settings/` |
| `settings/storage/StorageViewModel.kt` | `viewmodel/StorageViewModel.kt` |
| `MainActivity.kt` | `view/MainActivity.kt` |
| `DocMindContainer.kt`, `ui/DocMindViewModelFactory.kt` | `di/` |

Unit test được chuyển theo package tương ứng; instrumentation test cập nhật import và Activity mới. Đợt sắp xếp này giữ nguyên application ID, resource namespace `com.example.docmind`, cấu trúc bảng/migration Room và hợp đồng API. Backend Ktor trong `backend/` giữ nguyên cấu trúc.

## Luồng phụ thuộc

Compose → ViewModel của từng chức năng → Repository / dịch vụ dữ liệu → Room, tệp và API.

- `DocMindViewModel`: đăng nhập, đăng ký, đăng xuất, hồ sơ và mật khẩu.
- `DocumentsViewModel`: danh sách, lựa chọn, tải lên, xóa và trạng thái lập chỉ mục tài liệu.
- `ChatViewModel`: hội thoại, đổi tên, phạm vi tài liệu và yêu cầu hỏi đáp.
- Các màn hình nhận trạng thái và callback. `DocMindApp` lấy ViewModel từ factory và theo dõi StateFlow bằng `collectAsStateWithLifecycle`.

## Thành phần dùng chung

- `DocMindContainer`: dependency graph trong vòng đời Application. Các factory sau khi tạo lại Activity dùng cùng repository, phiên và indexer. Factory tạo được mỗi ViewModel theo thứ tự bất kỳ.
- `UserSession`: trạng thái tài khoản, định danh phiên và số thế hệ. Kết quả bất đồng bộ thuộc phiên cũ không được thay thế tài khoản hiện tại.
- `UiMessages`: thông báo dùng chung và phân giải chuỗi theo ngôn ngữ ứng dụng.
- `QaCancellation`: tín hiệu hủy hỏi đáp khi tài liệu bị xóa; tài liệu không gọi trực tiếp ChatViewModel.

Không có ViewModel phụ thuộc ViewModel khác, factory chứa ViewModel dạng lateinit, hoặc Composable gán callback điều phối đăng xuất.

## Vòng đời và chuyển tài khoản

Mỗi ViewModel chức năng có nhóm tác vụ con thuộc viewModelScope. Chuyển phiên hủy các tác vụ upload/index/QA/tạo hội thoại/lưu phạm vi và xóa lựa chọn, trạng thái, danh sách ngay lập tức. Đóng ViewModel hủy scope và gỡ đăng ký callback.

Các snapshot danh sách và chi tiết được xóa đồng bộ khi đổi phiên; kết quả truy vấn Room được kiểm tra theo thế hệ phiên. Điều này tránh phát lại cache của tài khoản trước khi màn hình quay lại foreground. Collector dữ liệu tồn tại cùng ViewModel; collector của Compose tuân theo lifecycle của màn hình.

## Kiểm chứng đợt refactor chức năng trước khi đổi package

- Unit test: 54 đạt, 1 bỏ qua, không thất bại.
- Instrumentation test trên emulator-5554: 32 đạt.
- assembleDebug, lintDebug và backend:test thành công.
- Kiểm thử mới: kết quả restore/profile cũ, hủy QA, tạo các ViewModel độc lập, xóa cache khi màn hình ngừng theo dõi dữ liệu và hủy index/QA khi đăng xuất mà không cần callback Compose.
- Lỗi cache phiên cũ được tái hiện bằng test thất bại trước khi sửa, sau đó test đạt.

## Kiểm chứng đổi package

Lệnh kiểm chứng: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug`.

- Gradle báo `BUILD SUCCESSFUL`; build debug và compile instrumentation test thành công.
- Unit test: 55 tổng, 54 đạt, 1 bỏ qua, 0 thất bại/lỗi.
- Lint: 0 lỗi, 93 cảnh báo; chưa xử lý cảnh báo ngoài phạm vi đổi package.
- 84 tệp Kotlin có package khớp đường dẫn; không còn tham chiếu package cũ trong `app/src` hoặc thư mục nguồn trống.
- Chưa chạy instrumentation test trên thiết bị trong đợt đổi package này.

## Phạm vi kiến trúc

Đây là MVVM với dependency injection thủ công. Chưa tách Repository tổng thành các interface theo chức năng hoặc tạo domain model riêng khỏi Room entity. Các lớp use case/Hilt không phải điều kiện bắt buộc của MVVM. Một số viewer còn đọc/parse nội dung trong hiệu ứng giao diện; có thể chuyển riêng sang ViewModel khi mở rộng chức năng xem tài liệu.

# Đề xuất chức năng Cấu hình hệ thống

Trạng thái: đã triển khai; unit test/build/lint và luồng máy ảo chính đã đạt. Giới hạn kiểm chứng được ghi dưới đây.
Nhánh: `feature/system-settings`, tạo từ `develop` tại `9276918`.

## Mục tiêu

Người dùng xem và điều chỉnh thiết lập của DocMind; lựa chọn có tác dụng thực tế và được giữ lại khi mở ứng dụng lần sau.

## Phạm vi đề xuất

- Giao diện: theo hệ thống (mặc định), sáng, tối. Áp dụng cho toàn bộ Activity của DocMind.
- Cỡ chữ: mặc định, lớn, rất lớn. Tôn trọng cỡ chữ hệ thống; lưu lựa chọn của ứng dụng.
- Thông tin: tên và phiên bản ứng dụng đang cài.
- Khôi phục thiết lập mặc định: hộp thoại xác nhận; hủy không thay đổi gì. Chỉ đặt lại thiết lập, không xóa tài khoản hoặc tài liệu.
- Nút mở Cấu hình hệ thống từ màn hình chính hiện tại.

Không bao gồm cấu hình máy chủ, khóa AI, đồng bộ đám mây, thông báo chưa có chức năng thực tế, hoặc thay đổi nhánh feature/login.

## Các hướng triển khai

1. Kotlin + XML/Material 3 và SharedPreferences (đề xuất): phù hợp develop, không thêm framework hoặc thư viện mới; đủ cho các lựa chọn nhỏ lưu trên máy.
2. Compose + DataStore: cần bổ sung plugin, dependency và thiết lập kiến trúc chưa có trên develop; không cần thiết cho phạm vi này.

Người dùng đã duyệt phương án Kotlin + XML/Material 3 và SharedPreferences.

## Giao diện và hành vi

Màn hình cuộn gồm tiêu đề Cấu hình hệ thống, nhóm Hiển thị, nhóm Thông tin ứng dụng và thao tác Khôi phục mặc định.
Mỗi lựa chọn thể hiện giá trị hiện tại; nhãn tiếng Việt và vùng chạm đủ lớn.
Đổi giao diện hoặc cỡ chữ tự lưu và cập nhật màn hình; nút quay lại về màn hình chính.
Thiết lập theo hệ thống phải tiếp tục phản ánh giao diện của thiết bị.
Thiết lập không hợp lệ đọc từ bộ nhớ được đưa về mặc định an toàn.

## Điểm thay đổi dự kiến

- Thêm lớp quản lý và mô hình thiết lập dưới package `com.example.docmind.settings`.
- Thêm `SettingsActivity`, layout và chuỗi giao diện tương ứng.
- Đăng ký Activity nội bộ trong AndroidManifest.xml.
- Bổ sung lối vào từ MainActivity/activity_main.xml và áp dụng giao diện/cỡ chữ khi Activity khởi tạo.
- Không thêm dependency hay sửa các thay đổi cục bộ có trước. Ngoại lệ môi trường: catalog cuối dùng AGP 9.5.1 theo chỉnh sửa đồng thời của người dùng, để tương thích Android Studio đã cập nhật.

## Tiêu chí nghiệm thu

1. Mở được màn hình từ trang chính và quay lại được.
2. Hiển thị đúng mặc định khi chưa lưu thiết lập.
3. Mỗi lựa chọn giao diện/cỡ chữ có tác dụng và tồn tại sau khi đóng, mở lại app.
4. Hủy đặt lại giữ nguyên giá trị; xác nhận khôi phục đúng mặc định.
5. Màn hình vẫn dùng được với chữ lớn và bàn phím/đọc màn hình.
6. Unit test cho quy tắc mặc định, ánh xạ lựa chọn và đọc giá trị không hợp lệ; Gradle :app:testDebugUnitTest và :app:assembleDebug thành công.
7. Nếu máy ảo khả dụng, kiểm tra trực tiếp luồng thay đổi, mở lại và đặt lại thiết lập.

## Kết quả nghiệm thu

Đã có màn hình, lưu thiết lập, áp dụng theme/cỡ chữ và reset an toàn theo phạm vi duyệt.
11 test cấu hình và 12 test toàn module đạt; build/deploy, lint và Sync thành công với AGP 9.5.1.
Máy ảo xác nhận Sáng/Rất lớn tồn tại qua force-stop/mở lại, trang chính cập nhật, Back hủy reset và xác nhận trả về mặc định; phiên bản 1.0 hiển thị.
Review độc lập không phát hiện vấn đề chặn bàn giao.
Chưa kiểm tra đầy đủ TalkBack/bàn phím, chữ hệ thống 200%, xoay màn hình, thay đổi theme thiết bị hoặc nhấn riêng nút Hủy reset trên máy ảo.
Chi tiết sử dụng, kiến trúc và bằng chứng: [system-settings.md](system-settings.md).

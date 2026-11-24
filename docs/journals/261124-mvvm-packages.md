# Làm rõ cấu trúc package MVVM

**Date**: 2026-11-24
**Severity**: Low
**Component**: Package ứng dụng Android DocMind
**Status**: Hoàn tất di chuyển và kiểm chứng build, unit test, compile instrumentation test và lint

## What Happened

Người dùng thấy các thư mục trống rồi đặt câu hỏi vì sao dự án nói dùng MVVM nhưng cây thư mục không có `view`, `viewmodel`, `model`. Cấu trúc cũ dùng `ui`, `data` và `settings`; ViewModel nằm cùng package với thành phần giao diện. Người dùng chấp thuận chuyển sang các nhóm thể hiện trực tiếp vai trò kiến trúc.

## The Brutal Truth

Mã nguồn có thể tuân theo MVVM nhưng cách đặt thư mục vẫn khiến người đọc khó tìm đúng lớp. Sự khó hiểu này đã gây thêm nhiều lượt giải thích. Đổi tên folder chỉ giải quyết khả năng đọc; không được dùng nó làm bằng chứng rằng mọi xử lý đã được tách khỏi View.

## Technical Details

Đợt di chuyển báo 65 tệp Kotlin. `MainActivity` chuyển sang `view`; `SettingsActivity` và `SettingsAwareActivity` sang `view/settings`. Các màn hình giữ nhóm `view/screens`, `view/auth`. `StorageViewModel` sang `viewmodel`; dữ liệu cài đặt sang `model/settings`. `DocMindContainer` và `DocMindViewModelFactory` vào `di`. Manifest phải tham chiếu `.view.MainActivity` và `.view.settings.SettingsActivity`.

## Decision and Root Cause

Chọn package `view`, `viewmodel`, `model`, `di` thay vì tiếp tục giải thích quy ước `ui/data`, vì yêu cầu hiện tại là nhìn cây thư mục có thể nhận ra MVVM. Nguồn gây nhầm lẫn là quy ước tổ chức chưa phản ánh cách người dùng đọc dự án. Không thay logic nghiệp vụ, schema Room, application ID hoặc hợp đồng API trong lần này.

## Lessons and Next Steps

Di chuyển package phải cập nhật cả import từng lớp, import wildcard, test và manifest. Lượt Gradle trong phiên này báo BUILD SUCCESSFUL: assembleDebug, testDebugUnitTest, compileDebugAndroidTestKotlin và lintDebug thành công. Unit test: 54 đạt, 1 bỏ qua, 0 thất bại/lỗi; lint: 0 lỗi, 93 cảnh báo. Không còn package cũ trong app/src và 84 tệp Kotlin đều khớp package với đường dẫn. Chưa chạy test thiết bị. Chi tiết kết quả được ghi ở `docs/MVVM.md`.

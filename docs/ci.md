# CI cho DocMind

Workflow: [.github/workflows/android-ci.yml](../.github/workflows/android-ci.yml).

## Khi nào chạy

GitHub Actions tự chạy khi mở, cập nhật commit hoặc mở lại PR có nhánh đích là `develop` hoặc `main`. Workflow kiểm tra bản ghép của PR với nhánh đích. Khi PR có commit mới, lượt chạy cũ được hủy để tiết kiệm thời gian.

Có thể chạy thủ công bằng **Actions → Android CI → Run workflow** sau khi workflow có mặt trên nhánh mặc định của repository.

## Nội dung kiểm tra

Job **Build, unit tests & lint** chạy trên Ubuntu 24.04 với:

- Temurin JDK 25, khớp `gradle/gradle-daemon-jvm.properties`.
- Gradle 9.6 qua wrapper của repository; wrapper được kiểm tra checksum.
- Android SDK Platform 37.1 và Build Tools 36.0.0.
- `:app:assembleDebug`, `:app:testDebugUnitTest` và `:app:lintDebug`.

Gradle dùng `--continue` để tiếp tục các task độc lập khi có lỗi; job vẫn thất bại nếu bất kỳ kiểm tra nào thất bại. Workflow không yêu cầu secret hay khóa ký release. Đây là kiểm tra build, unit test và lint; các bài kiểm thử cần máy ảo trong `androidTest` vẫn chạy riêng.

## Xem kết quả

Mở tab **Checks** trên PR hoặc **Actions → Android CI**.

- Artifact `android-reports`: báo cáo test, lint và kết quả XML, được tải lên cả khi kiểm tra thất bại nếu đã có báo cáo.
- Artifact `docmind-debug-apk`: APK debug khi toàn bộ kiểm tra thành công.
- Artifact được lưu 7 ngày.

Có thể chạy cùng bộ kiểm tra trên máy phát triển:

```powershell
.\gradlew.bat --no-daemon --stacktrace --continue :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Trên Linux/macOS, dùng `bash ./gradlew` thay cho `.\gradlew.bat`.

## Chặn merge khi CI lỗi

Để bắt buộc CI trước khi merge, quản trị viên có thể thêm job **Build, unit tests & lint** vào required status checks trong branch protection/ruleset cho `develop` và `main`, sau khi job đã chạy lần đầu. File workflow tự chạy kiểm tra; quy tắc chặn merge là cài đặt riêng của GitHub.

Khi đổi JDK, compileSdk hoặc phiên bản Build Tools, cập nhật workflow tương ứng.

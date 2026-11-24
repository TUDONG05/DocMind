# DocMind QA backend (Kotlin + Ktor)

Backend chuyển tiếp embedding và sinh câu trả lời tới Gemini. Không lưu nội dung, vector hay lịch sử; không ghi khóa hoặc prompt vào log.

## Chạy local

Dùng JDK tương thích Gradle của dự án (hiện CI dùng JDK 25).
Mở `backend/.env` và điền khóa của bạn vào `GEMINI_API_KEY=`.
File `backend/.env.example` là mẫu để sao chép nếu chưa có `.env`.

```dotenv
GEMINI_API_KEY=khóa_của_bạn
GEMINI_MODEL=gemini-3.5-flash-lite
GEMINI_EMBEDDING_MODEL=gemini-embedding-001
PORT=8080
```

Từ thư mục gốc, trong PowerShell:

```powershell
.\gradlew.bat :backend:run
```

Backend tự nạp `backend/.env` khi chạy từ thư mục gốc hoặc `.env` khi chạy trong thư mục backend.
Biến môi trường của hệ điều hành được ưu tiên hơn giá trị trong file.
Khởi động lại backend sau khi sửa `.env`. File này được bỏ qua trong Git và không được đóng gói vào APK.
Không có `.env` vẫn có thể dùng biến môi trường như trước.

Mặc định bind **127.0.0.1:8080**, chỉ phục vụ phát triển. Đổi cổng bằng biến PORT.
Không đưa khóa vào mã nguồn, Gradle properties, APK hay commit.
Có thể kiểm tra sẵn sàng bằng GET http://127.0.0.1:8080/health.
Thiếu khóa vẫn cho phép chạy health; API Gemini trả lỗi cấu hình.

Emulator/thiết bị USB: chạy `adb reverse tcp:8080 tcp:8080`, rồi chạy app debug.
Debug mặc định dùng http://127.0.0.1:8080; chỉ loopback được phép HTTP.
URL khác được đặt khi build: `-PqaBaseUrl=https://your-backend.example`.
Release không có URL mặc định và không cho HTTP.

## Sử dụng trên app

1. Tải PDF có lớp văn bản, DOCX, XLSX, TXT hoặc Markdown (.md, UTF-8). App tự gửi nội dung tới Gemini để tạo embedding sau khi tải lên.
2. Màn hình Tài liệu hiển thị tổng số, số đã embedding/đang xử lý/chưa sẵn sàng và tiến độ theo số đoạn của từng tệp. Chỉ hiện 100% sau khi lưu chỉ mục trong Room.
3. Tài liệu cũ có nút Tạo embedding; lỗi hoặc gián đoạn có nút Thử lại. Đóng tiến trình app khi đang xử lý sẽ báo gián đoạn ở lần mở tiếp theo.
4. Nhấn tạo hội thoại để hỏi ngay trên tất cả tài liệu đã embedding của tài khoản. Mục Phạm vi tài liệu trong chat cho phép chọn riêng hoặc quay về Tất cả; lựa chọn được lưu theo từng hội thoại. Tài liệu mới xử lý xong tự xuất hiện trong phạm vi Tất cả.
   Khi đổi phạm vi hoặc xóa tài liệu, lịch sử cũ vẫn hiển thị nhưng không được gửi làm ngữ cảnh AI cho phạm vi mới. Hội thoại cũ giữ các tài liệu đã chọn; dữ liệu v1 được nâng cấp bằng migration, không xóa dữ liệu.
5. Câu trả lời có danh sách nguồn và trích đoạn. Khi lỗi có thể thử lại câu hỏi cũ.

Giới hạn mỗi tài liệu: 15 MiB, 300 trang PDF, 200 đoạn, 200.000 ký tự sau chia đoạn.
DOCX/XLSX giới hạn thêm kích thước XML giải nén. Chưa có OCR, chưa đọc DOC/XLS cũ.
XLSX dùng giá trị đã lưu của ô, không tính lại công thức. DOCX/XLSX không gán số trang giả.
Tài liệu không đủ căn cứ hoặc không có đoạn tương đồng sẽ trả thông báo không tìm thấy thông tin.
Không coi câu trả lời của LLM là bằng chứng nếu mô hình không trả ID nguồn hợp lệ.

## API

- GET /health: trạng thái dịch vụ; không lộ khóa.
- POST /embeddings: `{texts: string[], task: "RETRIEVAL_DOCUMENT" | "RETRIEVAL_QUERY"}`.
  Tối đa 16 đoạn/batch, 1.200 ký tự/đoạn (query 4.000); trả model/version, dimensions, vectors.
- POST /answer: `{question, history: [{role, content}], sources: [{id, text}]}`.
  Tối đa 6 nguồn, 8 tin lịch sử; trả `{answer, sourceIds, grounded}`.
- Lỗi: JSON `{code}`, không trả raw lỗi Gemini hoặc bí mật.
- Khóa cùng model cấu hình ở backend; đổi model embedding tự làm mới chỉ mục, không trộn không gian vector.

## Phạm vi triển khai

Đây là backend **local**, chưa có danh tính người dùng trên máy chủ.
Tài khoản Room trên Android không dùng làm chứng thực API. Trước khi mở ra Internet cần HTTPS,
xác thực người dùng phía máy chủ, phân quyền và rate limit theo người dùng.
Không đổi bind loopback thành public khi chưa bổ sung các phần này.

## Kiểm thử

```powershell
.\gradlew.bat :backend:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Kiểm thử tự động sử dụng Gemini giả lập, không gọi dịch vụ trả phí.
Để xác minh chất lượng truy hồi và câu trả lời thực tế cần chạy backend với khóa hợp lệ.
Tài liệu tham khảo: [Gemini embeddings](https://ai.google.dev/api/embeddings),
[structured output](https://ai.google.dev/gemini-api/docs/structured-output),
[Ktor](https://ktor.io/docs/server-create-a-new-project.html).

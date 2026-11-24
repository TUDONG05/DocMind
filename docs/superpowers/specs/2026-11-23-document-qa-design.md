# Thiết kế Hỏi đáp tài liệu bằng RAG + Gemini

## Yêu cầu đã chốt
Người dùng yêu cầu triển khai Hỏi đáp tài liệu trên nhánh feature/document-qa, sử dụng RAG và Gemini làm LLM.
Giữ kiến trúc Kotlin/Compose, ViewModel/StateFlow và Room của DocMind; áp dụng cài đặt ngôn ngữ, giao diện và cỡ chữ hiện có.

## Hiện trạng
ChatDetailScreen và liên kết nhiều tài liệu vào hội thoại đã có.
sendMessage hiện chỉ chờ 600 ms rồi ghi câu trả lời mẫu.
Tải tài liệu thật chưa trích xuất nội dung hay embedding.
DocumentChunks đã có content, pageNumber, embedding dạng BLOB và embeddingModel.
Tài khoản và lịch sử hội thoại hiện chỉ lưu cục bộ, chưa có backend hoặc xác thực máy chủ.

## Phương án và lựa chọn đề xuất
1. Tự xây RAG: trích xuất, chia đoạn, lưu vector trong Room, truy hồi cosine và gọi Gemini. Dễ kiểm thử riêng từng bước, tận dụng schema hiện tại; cần xử lý các định dạng tài liệu.
2. Gemini File Search: dịch vụ quản lý chia đoạn, embedding và truy hồi. Ít code hơn nhưng lưu chỉ mục trên Google, cần quản lý vòng đời dữ liệu từ xa.

Phương án 1 đã được người dùng duyệt; backend được chốt dùng Kotlin + Ktor thay cho Node.js.
Không dùng gửi toàn bộ tài liệu trực tiếp vào prompt thay cho truy hồi.

## Trải nghiệm
- Người dùng tải tài liệu, tạo hội thoại và chọn ít nhất một tài liệu.
- Lần hỏi đầu: đọc nội dung và lập chỉ mục những tài liệu chưa có embedding hợp lệ.
- Hiển thị tiến trình chuẩn bị tài liệu/đang trả lời và khóa gửi trùng.
- Tìm tối đa 6 đoạn phù hợp chỉ trong tài liệu gắn với hội thoại thuộc người đang đăng nhập.
- Gemini nhận câu hỏi, ngữ cảnh truy hồi và lịch sử gần nhất có giới hạn; trả lời theo ngôn ngữ câu hỏi.
- Câu trả lời có nguồn (tên tệp, trang khi trích xuất xác định được, trích đoạn); không suy đoán số trang của DOCX/XLSX.
- Nếu tài liệu không cung cấp đủ căn cứ, trả lời rõ không tìm thấy thông tin.
- Nếu một tài liệu được chọn không thể lập chỉ mục, dừng lượt hỏi và nêu tên tệp lỗi; không âm thầm bỏ qua.
- Mạng lỗi, thiếu cấu hình, giới hạn dịch vụ hoặc tệp không đọc được: hiển thị lỗi và cho thử lại; không tạo câu trả lời mẫu.
- Câu hỏi và trả lời lưu Room; thử lại không nhân đôi câu hỏi.

## Android
- Trích xuất PDF có lớp văn bản, DOCX, XLSX và TXT trên Dispatchers.IO; PDF quét ảnh thông báo chưa hỗ trợ OCR.
- Tái sử dụng parser Word/Excel khi phù hợp, không lập chỉ mục chuỗi thông báo lỗi do parser tạo ra.
- Giới hạn kích thước tệp/nội dung/số đoạn, xử lý tệp hỏng và hủy coroutine.
- Chia đoạn có overlap; bảo toàn metadata nguồn.
- Lưu embedding có version/model và số chiều xác định; không trộn các embedding khác model hoặc dữ liệu mẫu.
- Chỉ thay thế chỉ mục cũ bằng giao dịch sau khi chỉ mục mới hoàn tất.
- ViewModel điều phối, repository chịu trách nhiệm đọc/ghi có kiểm tra ownership.
- Trạng thái gửi gắn với conversationId; đăng xuất hoặc xóa hội thoại phải hủy công việc tương ứng.
- Gửi đến backend qua HTTP client có timeout và lỗi được ánh xạ sang tài nguyên Việt/Anh.

## Backend đề xuất
Thêm dịch vụ Kotlin + Ktor nhỏ (đã được người dùng chốt) trong backend/, sử dụng Gemini REST API và biến môi trường GEMINI_API_KEY.
Khóa không nằm trong APK, Git, BuildConfig hoặc log.
Hai chức năng: embedding (phân biệt RETRIEVAL_DOCUMENT/RETRIEVAL_QUERY) và sinh câu trả lời từ các đoạn đã truy hồi.
Model sinh và embedding cấu hình được qua biến môi trường; xác minh tên model theo tài liệu Google lúc triển khai.
Response embedding có model, version và số chiều; query và chỉ mục phải cùng hợp đồng, đổi model phải lập lại chỉ mục.
Mỗi đoạn có ID nguồn ổn định. Gemini trả cấu trúc gồm answer, sourceIds và trạng thái đủ/thiếu căn cứ. Chỉ chấp nhận sourceIds nằm trong các đoạn đã gửi; không dùng nguồn do mô hình tự tạo. Prompt coi tài liệu và lịch sử là dữ liệu, không thực thi chỉ dẫn từ tài liệu.
Backend không lưu tài liệu, vector hoặc hội thoại. Kiểm tra body, giới hạn độ dài/batch, timeout và mã lỗi.
Bản phát triển bind loopback; emulator kết nối qua adb reverse và cấu hình URL cho debug.
Release dùng HTTPS và cần tích hợp xác thực máy chủ trước khi công khai. Tài khoản Room không được coi là danh tính backend.
Không triển khai máy chủ công khai trong phạm vi công việc này.

## Kiểm thử và tiêu chí hoàn thành
- Unit: chunking/overlap/giới hạn, encode/decode vector, cosine, lọc/ranking và không trộn model.
- Integration Room: giới hạn tài liệu theo user/hội thoại, thay chỉ mục nguyên tử, lưu lịch sử và retry.
- Backend với Gemini giả lập: hình dạng request, task embedding, giới hạn input, timeout/quota, câu trả lời không có căn cứ.
- Build debug, unit tests và lint; instrumentation/UI nếu có thiết bị.
- Kiểm thử Gemini thật cần khóa hợp lệ và backend được cấu hình; báo rõ nếu chưa có để tránh tuyên bố đã kiểm chứng.
- Có hướng dẫn cấu hình/chạy backend và ví dụ kiểm tra; không commit bí mật.
- Không tự commit/push/tạo PR hoặc merge khi chưa có yêu cầu.

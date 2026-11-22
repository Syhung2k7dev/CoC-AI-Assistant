# Hướng dẫn sử dụng CoC Agent

## Trung tâm AI

1. Mở ứng dụng và nhập mục tiêu vào ô hội thoại Trung tâm AI.
2. Chọn **Chạy Agent tự động**. Agent tự mở Clash of Clans, chụp màn hình qua Accessibility và chạy OCR/OpenCV; người dùng không cần tải ảnh lên.
3. Agent sử dụng ảnh màn hình làm dữ liệu thị giác nội bộ để Gemini phân tích trạng thái và quyết định Skill phù hợp.
4. Kết quả phải phân biệt điều đã quan sát, suy luận và thông tin còn thiếu. Phân tích từ Gemini chưa phải lệnh thao tác.
5. Agent không có giới hạn cứng về số lần chạy Skill. Phiên tự dừng khi hoàn thành, cần người dùng, không đủ bằng chứng hoặc hết ngân sách 30 phút; từng Skill vẫn bị giới hạn bước và thời gian.
6. Agent chỉ chọn Skill có đúng ID/version và trạng thái VERIFIED; sau mỗi lần chạy sẽ chụp/OCR lại màn hình. Nếu không có bằng chứng xác minh rõ ràng, Agent dừng. Có thể chọn **Dừng Agent ngay** trong khi chạy.

Bạn cũng có thể dùng **Chat với AI** để trao đổi bằng văn bản mà không mở game hoặc chụp màn hình. Trong Cài đặt, nhập API Key (được che) và chọn **Kiểm tra API Key**; khóa chỉ nằm trong bộ nhớ phiên và không được lưu.

## Bật Accessibility

1. Mở **Cài đặt** trong ứng dụng.
2. Tìm mục **Kiểm tra quyền Trợ năng** và chọn **Mở cài đặt Accessibility**.
3. Trong danh sách dịch vụ Android, bật **CoC Agent – Quyền Trợ năng** và đọc kỹ màn hình xác nhận của Android.
4. Quay lại ứng dụng. Nếu trạng thái chưa cập nhật, mở lại màn hình hoặc kiểm tra dịch vụ trong Android Settings.

Nếu Android hoặc Play Protect chặn cài đặt hay bật dịch vụ, không tắt bảo vệ để tiếp tục. Hãy kiểm tra nguồn APK, chữ ký và cảnh báo trước.

## Nguyên tắc hệ thống

Trong Trung tâm AI, chọn **Xem / sửa nguyên tắc Agent** để xem nội dung đang dùng. Thay đổi chỉ được lưu sau khi chọn xem thay đổi và nhấn **Đồng ý áp dụng** ở hộp thoại xác nhận thứ hai. Đóng hoặc từ chối hộp thoại sẽ không áp dụng thay đổi.

## Nghiên cứu kỹ năng

1. Mở tab **Kỹ năng**.
2. Các mục **Chưa nghiên cứu** mới chỉ là mục tiêu/năng lực dự kiến, chưa có quy trình thao tác.
3. Chọn **Nghiên cứu kỹ năng này** để Gemini tạo phiên bản mới có mô tả điều kiện, quy trình, trạng thái mong đợi và cách xác minh.
4. Kết quả chuyển thành **Đã nghiên cứu · Chờ kiểm thử**. Trạng thái này không cho phép thực thi.
5. Chỉ Skill đã qua kiểm thử, có dependency hợp lệ và được đánh dấu **Đã xác minh** mới được ActionExecutor chấp nhận.

Nghiên cứu bằng Gemini không chứng minh rằng quy trình đã chạy thành công trên thiết bị thật. Khi thiếu ảnh hoặc bằng chứng, nội dung nghiên cứu phải ghi rõ điều chưa biết thay vì bịa tọa độ.

## Công cụ nâng cao

Tab **Cài đặt** giữ bảng điều khiển thao tác thử để kiểm tra tọa độ, chụp màn hình/OCR, Pause/Resume/Stop. Bảng này dành cho chẩn đoán, không phải cách sử dụng chính của Agent.

## Giới hạn hiện tại

- Người dùng phải tự bật quyền Accessibility trong Android Settings.
- Chụp màn hình có thể thất bại với cửa sổ được bảo vệ hoặc khi Android từ chối.
- Ảnh màn hình chỉ được Agent thu thập trong phiên chạy đã được người dùng khởi động; không có chức năng tải ảnh thủ công. Agent dùng ảnh và OCR để nhận diện trạng thái.
- Vòng lặp Agent có thể chạy nhiều Skill VERIFIED trong một phiên, với ngân sách thời gian 30 phút, giới hạn thực thi từng Skill và nút Dừng.
- Xác minh sau hành động hiện dựa trên OCR/ảnh và đánh giá của Gemini; không bảo đảm nhận diện đúng mọi trạng thái game. Khi không chắc chắn Agent phải dừng.
- Cần kiểm thử trên thiết bị Android thật vì quyền Accessibility, capture và chính sách nền khác nhau theo nhà sản xuất.
- Không biến văn bản hoặc tọa độ do mô hình sinh ra thành thao tác thiết bị trực tiếp.
- Cần kiểm thử trên thiết bị Android thật vì quyền Accessibility, capture và chính sách nền khác nhau theo nhà sản xuất.

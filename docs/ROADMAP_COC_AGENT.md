# Lộ trình CoC Agent

## Mục tiêu sản phẩm

CoC-AI-Assistant là AI Agent hỗ trợ tự động hóa giao diện Clash of Clans. Gemini là lớp phân tích và lập kế hoạch; OpenCV/OCR là lớp quan sát cục bộ; Accessibility là lớp thực thi; CADSS lưu trạng thái và Vision Memory; CASL kiểm tra điều kiện và điều phối nhánh workflow.

## Luồng hoạt động mục tiêu

1. Quan sát giao diện bằng Accessibility events và ảnh chụp màn hình được Android cho phép.
2. Phân tích cục bộ bằng OCR, OpenCV, pHash và mẫu đã lưu.
3. Khi cần, gửi ngữ cảnh tối thiểu đến Gemini theo sự đồng ý rõ ràng của người dùng.
4. Chuyển đề xuất AI thành workflow có schema/version; kiểm tra kiểu bước, tọa độ, thời lượng, giới hạn và cấu trúc IF/ELSE.
5. Cho người dùng xem lại workflow và xác nhận bắt đầu.
6. Thực thi bằng Accessibility Gesture API với countdown, giới hạn lặp, Pause/Resume/Stop.
7. Quan sát kết quả sau hành động; chỉ tiếp tục khi điều kiện xác minh đạt, nếu không thì tạm dừng hoặc dừng an toàn.
8. Ghi trạng thái và mẫu đã xác minh vào CADSS/Vision Memory; không tự học từ hành động thất bại hoặc màn hình nhạy cảm.

## Nguyên tắc an toàn và quyền riêng tư

- Không ẩn hoạt động tự động hóa, không vượt qua Play Protect, quyền Android, màn hình bảo vệ hoặc cơ chế anti-cheat.
- Không tự động thao tác khi chưa có lệnh bắt đầu rõ ràng từ người dùng.
- Mọi workflow do Gemini tạo đều là bản nháp; không được gửi trực tiếp tới Gesture API.
- Ảnh và OCR xử lý cục bộ theo mặc định. Gửi ảnh lên Gemini là lựa chọn riêng, có thông báo và xác nhận.
- Mọi vòng lặp, retry, thời gian chờ và số bước đều phải có giới hạn.
- Khi Accessibility mất kết nối, screenshot thất bại, tọa độ không hợp lệ hoặc xác minh không đạt, Agent phải dừng/tạm dừng thay vì đoán thao tác.

## Trạng thái triển khai

### Đã có trong nhánh runtime
- Accessibility Service được khai báo trong Manifest và cấu hình XML.
- Thực thi tap/swipe/wait có countdown 5 giây, tối đa 50 bước, 20 lần lặp.
- Pause/Resume/Stop và kiểm tra tọa độ theo kích thước màn hình.
- API chụp màn hình Accessibility trên Android 11 trở lên.
- Bảng điều khiển điều khiển thử trong ứng dụng.

### Còn cần triển khai
- Workflow editor và JSON codec phiên bản hóa đầy đủ.
- OCR cục bộ và điều kiện chờ văn bản.
- So khớp template đa tỉ lệ và điều kiện IF/ELSE cho ảnh.
- Kết nối ảnh chụp màn hình với Vision Memory/CADSS.
- Agent planner/executor loop có xác minh trạng thái sau từng hành động.
- Gemini workflow draft có schema validation và review UI.
- Floating dashboard toàn hệ thống và notification action.
- Scheduler/Deep Sleep thực sự, khôi phục lịch sau reboot.
- Kiểm thử trên thiết bị thật và kiểm tra phát hành/ký APK.

Việc biên dịch thành công không thay thế kiểm thử trên thiết bị Android thực tế.

# Hướng dẫn sử dụng CoC Agent

## Bật Accessibility

1. Mở tab **Tổng quan** và tìm bảng **CoC Agent · Điều khiển tự động**.
2. Chọn **Mở cài đặt Accessibility**.
3. Trong danh sách dịch vụ Android, bật **CoC Agent Accessibility** và đọc kỹ màn hình xác nhận của Android.
4. Quay lại ứng dụng. Trạng thái quyền được kiểm tra lại khi màn hình được mở lại.

Nếu Android hoặc Play Protect chặn cài đặt hay bật dịch vụ, không tắt bảo vệ để tiếp tục. Hãy kiểm tra nguồn APK, chữ ký và cảnh báo trước.

## Chạy thử thao tác

1. Đưa Clash of Clans hoặc màn hình thử nghiệm có thể thao tác hợp lệ lên trước.
2. Nhập tọa độ X/Y theo pixel màn hình.
3. Nhấn **Xác nhận và chạy thử thao tác chạm**.
4. Đọc hộp thoại xác nhận. Khi bắt đầu, ứng dụng chờ 5 giây rồi thực hiện một lần chạm.
5. Dùng **Pause / Resume** để tạm dừng/tiếp tục hoặc **Stop** để dừng.

Workflow hiện hỗ trợ Tap, Swipe và Wait ở lớp runtime nền. Mỗi workflow tối đa 50 bước và 20 lần lặp; mỗi thao tác có giới hạn thời lượng. Tọa độ ngoài màn hình hoặc thao tác bị Android từ chối sẽ làm workflow dừng.

## Chụp màn hình

Runtime cung cấp API chụp màn hình qua Accessibility trên Android 11 trở lên. Thiết bị và cửa sổ được bảo vệ có thể từ chối chụp. Chức năng OCR và pipeline nhận diện màn hình đầy đủ vẫn đang được phát triển.

## Gemini và workflow

Gemini là thành phần phân tích/lập kế hoạch. Đề xuất từ AI cần được chuyển thành workflow có schema hợp lệ và được người dùng xem lại trước khi chạy. Không tự động thực thi văn bản phản hồi từ mô hình như lệnh.

## Lưu trữ và backup

CADSS và Vision Memory tiếp tục lưu trạng thái, metadata và mẫu hình ảnh cục bộ. Tạo bản backup trước khi thay đổi cấu trúc dữ liệu hoặc khôi phục gói ZIP.

## Giới hạn hiện tại

Bản runtime đầu tiên chưa phải Agent tự động hoàn chỉnh. OCR condition, IF/ELSE JSON workflow editor, Gemini draft review, vòng lặp xác minh sau thao tác, floating dashboard toàn hệ thống và scheduler chuyên dụng còn cần triển khai và kiểm thử. Cần xác minh thực tế trên thiết bị Android/ColorOS vì chính sách nền và Accessibility khác nhau theo nhà sản xuất.

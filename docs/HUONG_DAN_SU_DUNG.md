# Hướng dẫn sử dụng Trợ lý AI CoC

## Bắt đầu

Mở ứng dụng để vào trang **Tổng quan**. Các dữ liệu ghi chú và lịch sử hình ảnh được lưu trong bộ nhớ riêng của ứng dụng trên thiết bị.

## Tổng quan

- Xem trạng thái CADSS, CASL và bộ xử lý hình ảnh.
- Nhập ghi chú cá nhân và nhấn **Lưu ghi chú** để lưu trên thiết bị.
- Dùng các thẻ lối tắt để chuyển nhanh đến khu vực xử lý hình ảnh hoặc quy tắc.

## Phân tích hình ảnh

1. Mở tab **Thị giác**.
2. Nhấn **Chọn hình ảnh để phân tích** và chọn tệp từ thiết bị.
3. Ứng dụng xử lý hình ảnh cục bộ, tạo mã pHash và so sánh với các mẫu đã lưu.
4. Xem kết quả và lịch sử ở phần **Bộ nhớ hình ảnh**.

Ảnh gốc và ảnh cắt được lưu trong vùng riêng của ứng dụng. Chỉ chọn hình ảnh mà bạn có quyền sử dụng.

## Quy tắc CASL

CASL đánh giá điều kiện khai báo theo đường dẫn dữ liệu và toán tử được hỗ trợ. Các toán tử hiện có gồm: tồn tại, bằng, khác, chứa, lớn hơn, nhỏ hơn và thuộc danh sách. Kết quả xác định nhánh **THEN** hoặc **ELSE**. Bộ máy không chạy mã tùy ý và không tự điều khiển ứng dụng khác.

## Lịch tác vụ và đồng hồ phiên

- Nhấn **Lên lịch** để đăng ký tác vụ bảo trì định kỳ qua WorkManager.
- Nhấn **Hủy lịch** để hủy tác vụ đã đăng ký.
- Đồng hồ phiên cho phép chọn 30 phút hoặc 60 phút; nhấn **Đặt lại** để dừng và đặt về 0.

Android có thể trì hoãn tác vụ nền để tối ưu pin. Đây không phải bộ hẹn giờ thời gian thực và không bảo đảm tác vụ chạy đúng một thời điểm tuyệt đối.

## Kết nối Gemini

1. Tạo và quản lý khóa API Gemini từ tài khoản Google của bạn.
2. Mở tab **Cài đặt** và nhập khóa API.
3. Nhập yêu cầu phân tích.
4. Nhấn nút gửi để chủ động gửi văn bản và, nếu có, hình ảnh đang chọn.

Khóa API chỉ được giữ trong trạng thái phiên hiện tại của màn hình và không được ghi vào CADSS. Không chia sẻ khóa API với người khác.

## Sao lưu và khôi phục

- Chọn **Sao lưu** để tạo tệp ZIP chứa dữ liệu CADSS và tài nguyên bộ nhớ hình ảnh.
- Chọn **Khôi phục** để chọn tệp ZIP đã tạo trước đó.

Khôi phục có thể thay đổi dữ liệu cục bộ hiện có. Hãy giữ một bản sao lưu độc lập trước khi thực hiện. Phiên bản hiện tại chưa cung cấp chế độ hợp nhất dữ liệu.

## Quyền riêng tư

Ứng dụng không yêu cầu quyền Trợ năng, quyền hiển thị trên ứng dụng khác hoặc quyền chụp màn hình. Không có chức năng tự động thao tác Clash of Clans. Yêu cầu Gemini chỉ được gửi khi người dùng chủ động thực hiện.

# CoC AI Assistant

**Trợ lý AI CoC** là ứng dụng Android hỗ trợ quản lý dữ liệu cá nhân, phân tích hình ảnh và kiểm tra quy tắc tự động. Ứng dụng ưu tiên xử lý cục bộ; các tính năng AI trực tuyến chỉ hoạt động khi người dùng chủ động gửi yêu cầu.

## 1. Các thành phần chính

- **CADSS – Hệ thống lưu trữ dữ liệu:** lưu ghi chú, bộ sưu tập và thông tin có cấu trúc JSON kèm số phiên bản.
- **CASL – Bộ máy quy tắc:** đánh giá điều kiện IF/ELSE bằng JSON DSL khai báo. Không thực thi mã tùy ý.
- **Không gian xử lý hình ảnh:** xử lý hình ảnh do người dùng chọn bằng OpenCV trên thiết bị và tạo mã băm cảm nhận 64 bit (pHash).
- **Bộ nhớ hình ảnh:** lưu hình ảnh gốc, hình ảnh cắt trung tâm và thông tin mô tả trong vùng lưu trữ riêng của ứng dụng.
- **Học mẫu hình ảnh:** ghi nhận mẫu hình ảnh mới và tìm mẫu gần nhất bằng khoảng cách Hamming giữa các mã pHash.
- **Kết nối Gemini:** hỗ trợ phân tích văn bản và hình ảnh khi người dùng nhập khóa API và chủ động gửi yêu cầu.
- **Bộ lập lịch:** dùng WorkManager để đăng ký tác vụ bảo trì định kỳ theo cơ chế do Android quản lý.
- **Sao lưu và khôi phục:** đóng gói dữ liệu CADSS cùng tài nguyên hình ảnh trong tệp ZIP có phiên bản.

## 2. Giao diện ứng dụng

Ứng dụng có năm khu vực:

1. **Tổng quan:** trạng thái hệ thống, số mẫu hình ảnh, ghi chú và lối tắt.
2. **Thị giác:** chọn hình ảnh, xử lý cục bộ và xem lịch sử bộ nhớ hình ảnh.
3. **Quy tắc CASL:** chạy thử quy tắc IF/ELSE theo cấu trúc JSON.
4. **Lịch tác vụ:** lên lịch bảo trì và sử dụng đồng hồ phiên 30 hoặc 60 phút.
5. **Cài đặt:** kết nối Gemini, sao lưu/khôi phục và thông tin quyền riêng tư.

Bảng điều khiển nhanh nằm bên trong ứng dụng; đây không phải cửa sổ nổi cấp hệ điều hành.

## 3. Yêu cầu xây dựng

- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21 và Kotlin Compose Compiler Plugin
- JDK 17, Gradle 8.9
- Android API 23 trở lên; biên dịch và mục tiêu API 35

Có thể chạy quy trình **Android APK** trong GitHub Actions. Khi quy trình hoàn tất thành công, GitHub Actions sẽ cung cấp tệp hiện vật `CoC-AI-Assistant-debug`.

## 4. Quyền riêng tư và phạm vi hoạt động

- Hình ảnh chỉ được xử lý sau khi người dùng chủ động chọn.
- Hình ảnh gốc và ảnh cắt được lưu trong vùng riêng của ứng dụng.
- Yêu cầu Gemini chỉ được gửi sau thao tác chủ động của người dùng.
- Khóa API không được ứng dụng ghi vào kho dữ liệu CADSS.
- Ứng dụng hiện không yêu cầu quyền Trợ năng, quyền hiển thị trên ứng dụng khác hoặc quyền chụp màn hình.
- Ứng dụng không tự động thao tác hay điều khiển trò chơi Clash of Clans.

## 5. Giới hạn hiện tại và kiểm thử

- Tác vụ bảo trì định kỳ hiện là điểm móc nhẹ; chính sách dọn dẹp chi tiết và lịch tùy chỉnh chưa được triển khai đầy đủ.
- Bảng điều khiển nhanh chỉ hiển thị trong ứng dụng, không phải lớp phủ toàn hệ thống.
- Khôi phục bản sao lưu có kiểm tra gói ZIP và phiên bản dữ liệu, nhưng chưa có lựa chọn hợp nhất hoặc thay thế dữ liệu.
- Cần cài đặt và kiểm tra thực tế trên thiết bị Android. Việc CI biên dịch thành công không tự chứng minh mọi chức năng đều hoạt động đúng trên thiết bị.

## 6. Ngôn ngữ

Giao diện và tài liệu hướng đến tiếng Việt. Các tên công nghệ, giao thức và định danh kỹ thuật như CADSS, CASL, OpenCV, pHash, JSON, Gemini và WorkManager được giữ nguyên để bảo đảm tính chính xác.

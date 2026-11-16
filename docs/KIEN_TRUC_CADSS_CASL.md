# Kiến trúc dữ liệu CADSS và quy tắc CASL

Tài liệu này mô tả cấu trúc đang có trong phiên bản hiện tại của ứng dụng. Các tên trường JSON là định danh kỹ thuật, được giữ nguyên để bảo đảm khả năng tương thích.

## 1. CADSS – kho dữ liệu có phiên bản

Tài liệu xuất/nhập có các trường cấp cao:

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `format` | chuỗi | Định danh định dạng, hiện là `CADSS` |
| `schemaVersion` | số nguyên | Phiên bản cấu trúc dữ liệu |
| `createdAt` | số | Thời điểm tạo, mili giây Unix |
| `texts` | đối tượng | Các giá trị văn bản được lưu |
| `collections` | đối tượng | Các tập bản ghi JSON |

Mỗi tập dữ liệu được lưu dưới dạng danh sách đối tượng JSON. Ứng dụng hiện lưu dữ liệu cục bộ bằng SharedPreferences. Phiên bản cấu trúc hiện tại là 1.

## 2. Bản ghi bộ nhớ hình ảnh

Bản ghi hình ảnh hiện có các trường:

| Trường | Ý nghĩa |
|---|---|
| `id` | Mã định danh bản ghi |
| `hash` | Mã pHash 64 bit ở dạng chuỗi hệ thập lục phân |
| `original` | Tên tệp ảnh gốc |
| `crop` | Tên tệp ảnh cắt trung tâm |
| `width`, `height` | Kích thước ảnh nguồn |
| `createdAt` | Thời điểm tạo bản ghi |
| `label` | Nhãn hiển thị |

Ảnh được đặt trong vùng lưu trữ riêng của ứng dụng. CADSS lưu tham chiếu tên tệp, không nhúng dữ liệu ảnh trực tiếp vào từng bản ghi JSON.

## 3. Sao lưu và khôi phục

Gói ZIP gồm tài liệu `cadss.json` và các tài nguyên trong thư mục `vision/`. Khi khôi phục, ứng dụng kiểm tra định dạng, phiên bản và đường dẫn tài nguyên trước khi nhập dữ liệu.

Phiên bản hiện tại chấp nhận phiên bản cấu trúc từ 1 đến phiên bản ứng dụng hỗ trợ. Chưa có cơ chế hợp nhất dữ liệu; người dùng nên tạo bản sao lưu trước khi khôi phục.

## 4. CASL – JSON DSL

Một quy tắc gồm ba phần:

- `condition`: điều kiện cần đánh giá.
- `then`: nhánh được chọn khi điều kiện đúng.
- `else`: nhánh được chọn khi điều kiện sai.

Trong `condition`, trường `path` chỉ đường dẫn thuộc tính theo dấu chấm; `op` là toán tử; `value` là giá trị so sánh. Các toán tử được hỗ trợ hiện tại:

| Toán tử | Ý nghĩa |
|---|---|
| `exists` | Giá trị tại đường dẫn tồn tại |
| `equals` | Hai giá trị bằng nhau |
| `not_equals` | Hai giá trị khác nhau |
| `contains` | Chuỗi bên trái chứa chuỗi cần tìm |
| `greater_than` | Giá trị số lớn hơn |
| `less_than` | Giá trị số nhỏ hơn |
| `in` | Giá trị thuộc một danh sách |

Mỗi nhánh có thể chứa `type` và `value` để mô tả hành động. Bộ máy hiện chỉ đánh giá và trả về nhánh/hành động dạng mô tả; nó không thực thi mã tùy ý hoặc lệnh hệ thống.

## 5. Quy tắc tương thích và nâng cấp

- Không đổi ý nghĩa các trường đã phát hành trong cùng một phiên bản cấu trúc.
- Khi thay đổi không tương thích, tăng `schemaVersion` và bổ sung bước chuyển đổi dữ liệu.
- Khi đọc tệp sao lưu có phiên bản mới hơn ứng dụng, từ chối khôi phục thay vì âm thầm bỏ qua trường.
- Giữ nguyên các định danh kỹ thuật và tên trường JSON; bản dịch chỉ áp dụng cho giao diện và tài liệu hướng dẫn.
- Mọi thay đổi định dạng cần có kiểm thử xuất/nhập tương ứng.

## 6. Trạng thái triển khai

Tài liệu phản ánh đúng phạm vi hiện tại: CADSS là kho JSON cục bộ; CASL là bộ đánh giá điều kiện khai báo; lịch tác vụ dùng WorkManager; Vision Watchdog so khớp pHash khi có ảnh đầu vào, chưa phải tiến trình giám sát liên tục.

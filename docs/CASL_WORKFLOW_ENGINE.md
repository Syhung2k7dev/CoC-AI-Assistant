# CASL JSON DSL và Workflow Engine

## 1. Vai trò

CASL (CoC Automation Script Language) là AST JSON khai báo dùng chung cho Workflow và Skill. Không đánh giá Kotlin/Java, JavaScript, shell, reflection hay chuỗi mã tùy ý. Runtime chỉ thực thi các operation đã định nghĩa và API nằm trong allow-list.

Luồng thực thi:

`WorkManager trigger → WorkflowScheduler → WorkflowEngine → CaslWorkflowRegistry → CaslScriptRuntime → Skill Registry / CaslApiRegistry → native capability adapter`

Workflow là đơn vị công việc cấp cao. Skill là năng lực có thể tái sử dụng. Primitive/API được đăng ký bởi mã native đáng tin cậy; script do AI tạo không thể tự cấp quyền cho API mới.

## 2. Cấu trúc program

Mỗi program có:
- `format: "CASL"`
- `schemaVersion: 1`
- `kind: "workflow" | "skill"`
- `id`: định danh ổn định
- `version`: số phiên bản nguyên dương
- `steps`: danh sách operation theo thứ tự

Workflow và Skill dùng cùng AST, validator và interpreter. Khác biệt nằm ở lifecycle, quyền gọi và cách phân giải dependency. Skill phải được VERIFIED và lời gọi trong Workflow phải pin version cụ thể.

## 3. Biểu thức tham chiếu

Giá trị object có khóa `$ref` đọc dữ liệu từ execution context:
- `inputs.<name>`: đầu vào của Workflow/Skill
- `vars.<name>`: biến trong lần thực thi hiện tại
- `outputs.<name>`: kết quả đã xuất

Đường dẫn hỗ trợ object lồng nhau và chỉ số mảng dạng số. Giá trị không tồn tại được phân biệt với giá trị hợp lệ; không thể truy cập thuộc tính tùy ý của object hệ thống.

## 4. Điều kiện

Điều kiện lá gồm `path`, `op`, và `value` khi operator cần giá trị. Hỗ trợ:
- `exists`, `not_exists`
- `equals`, `not_equals`
- `contains`, `starts_with`, `ends_with`
- `greater_than`, `less_than`, `greater_or_equal`, `less_or_equal`
- `in`, `not_in`

Điều kiện nhóm:
- `{"all": [condition, ...]}`: tất cả điều kiện đúng
- `{"any": [condition, ...]}`: ít nhất một điều kiện đúng
- `{"not": condition}`: đảo kết quả

Nhóm điều kiện có thể lồng nhau trong giới hạn độ sâu của runtime.

## 5. Operations

| Operation | Ý nghĩa |
|---|---|
| `set` | Gán biến vào `vars` |
| `output` | Ghi kết quả vào `outputs` |
| `call` | Gọi API đã đăng ký trong `CaslApiRegistry`; có timeout riêng |
| `skill` | Gọi Skill VERIFIED với ID và version pin |
| `if` | Rẽ nhánh `then` / `else` theo điều kiện |
| `repeat` | Lặp số lần hữu hạn, có giới hạn |
| `wait` | Chờ có giới hạn thời gian |
| `assert` | Dừng Workflow nếu điều kiện sai |
| `log` | Ghi dấu vết thực thi giới hạn độ dài |
| `return` | Trả giá trị kết thúc program |

Các giới hạn hiện tại: tối đa 500 step, độ sâu lồng 12, repeat 20 vòng, mỗi wait tối đa 60 giây, tổng wait 120 giây, mỗi API call timeout tối đa 120 giây, toàn Workflow tối đa 10 phút. Cancellation được truyền xuyên qua coroutine và API call.

## 6. Skill orchestration

Operation `skill` yêu cầu:
- `id` hợp lệ
- `version` bắt buộc và phải pin chính xác
- Skill có trạng thái VERIFIED, script hợp lệ và dependency VERIFIED tương thích
- Không cho phép đệ quy Skill hoặc gọi version không khớp

Kết quả Skill được đưa vào biến được chỉ định bởi `assign`, bao gồm outputs của Skill và giá trị return. Skill không thể gọi API chưa được đăng ký. Native adapter hiện có `action.tap`, `action.swipe`, `action.wait`: mỗi lệnh chỉ được gọi trong context của Skill đã pin version, Skill phải VERIFIED và phải khai báo capability tương ứng. ActionExecutor kiểm tra quyền, trạng thái Accessibility và giới hạn tọa độ/thời lượng; interpreter chờ kết quả thao tác trước khi chạy step kế tiếp.

## 7. Kiểm thử và xác minh

Workflow có thể khai báo 1-20 test cases trong `tests`; mỗi test gồm `name`, `inputs`, `expectOutputs` hoặc `expectReturn`, và tùy chọn `mockApis`. WorkflowTestRunner chạy cùng interpreter nhưng thay mọi API được gọi bằng mock, kể cả API lồng trong Skill. Test runner không thực hiện thao tác Accessibility hoặc gọi API thiết bị thật. Chỉ khi tất cả test đạt và mọi Skill dependency đã VERIFIED đúng version thì Registry mới chuyển Workflow sang VERIFIED.

## 8. Workflow lifecycle và run state

Lifecycle của định nghĩa Workflow:
- DRAFT: AI/user có thể tạo phiên bản mới; không được chạy
- TESTING: đang kiểm thử
- VERIFIED: được phép chạy và lên lịch
- DISABLED: bị vô hiệu hóa

Run state của mỗi lần thực thi:
- QUEUED → RUNNING → SUCCEEDED
- RUNNING → FAILED hoặc CANCELLED

Workflow Registry giữ các phiên bản bất biến theo ID/version. Workflow Engine chỉ lấy phiên bản VERIFIED; bản mới không ghi đè bản cũ. Execution history lưu ID/version, thời gian, trạng thái, số bước và trace giới hạn; không lưu API key, prompt, ảnh hay nội dung phản hồi AI.

## 9. Scheduler

`WorkflowScheduler` hỗ trợ:
- Chạy một lần vào mốc thời gian tương lai
- Chạy định kỳ, tối thiểu 15 phút
- Hủy lịch theo schedule ID

WorkManager bảo đảm lưu lịch qua việc process bị dừng và tương thích Doze, nhưng Android có quyền trì hoãn thời điểm thực thi để tiết kiệm pin. Đây không phải bộ lập lịch thời gian thực hoặc bảo đảm chạy đúng từng giây.

## 10. API allow-list và bảo mật

Runtime mặc định chỉ cung cấp các API CADSS cục bộ:
- `memory.read`
- `memory.write`
- `memory.append`
- `system.time`

Ứng dụng có thể đăng ký thêm native adapter đáng tin cậy qua điểm cấu hình API của WorkflowEngine. Allow-list hiện tại: `memory.read`, `memory.write`, `memory.append`, `system.time`, `action.tap`, `action.swipe`, `action.wait`. Không đưa credential vào CASL. Không cho script tự khai báo API, endpoint, quyền Android hay capability. Tương tác Accessibility/thiết bị phải đi qua adapter native có kiểm tra quyền, xác nhận người dùng và xác minh trạng thái riêng.

## 11. AI Core Provider Runtime

AI Core có Provider Registry/Adapter và Connection Runtime độc lập với Workflow DSL. Gemini adapter có connect/read timeout, hủy kết nối khi coroutine bị hủy, phân loại lỗi HTTP, retry giới hạn với exponential backoff/jitter và tôn trọng Retry-After dạng giây. Lỗi xác thực/đầu vào không retry; không tự động chuyển provider. Thông tin lỗi hiển thị không đính kèm body phản hồi máy chủ.

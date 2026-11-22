# CoC Agent: Script-Driven Runtime, Skill Framework và HVCS

## 1. Nguyên tắc kiến trúc

CoC Agent vận hành theo mô hình **Script-Driven Agent Runtime**.

- Workflow là đơn vị công việc cấp cao nhất và là thứ người dùng giao cho Agent.
- Skill là module năng lực có thể tái sử dụng, được Workflow hoặc Skill khác gọi.
- Primitive Skill là năng lực bậc thấp nhất, bao bọc một API/service cụ thể như quan sát màn hình, OCR, tính toán, ghi dữ liệu, tap, swipe hoặc wait.
- Workflow và Skill đều là CASL program, dùng chung một ngôn ngữ kịch bản và một interpreter.
- Runtime máy chủ nhiệm vụ, trạng thái, điều kiện, vòng lặp, timeout, retry, checkpoint và lỗi. Script quyết định logic nghiệp vụ và việc gọi Skill/API nào.
- Gemini là AI Core để nghiên cứu, tạo mới, sửa, kiểm thử, ghép nối và nâng phiên bản Workflow/Skill. Gemini không phải nơi thực thi thao tác trực tiếp.

Multi-account, Attack, Farm và Village Management là các Workflow/Skill có thể tạo, sửa và phối hợp bằng CASL, không phải các module nghiệp vụ cố định được hard-code vào giao diện ứng dụng.

## 2. Phân cấp

1. **System Prompt / Goal:** vai trò Agent, mục tiêu, ngữ cảnh và yêu cầu của người dùng.
2. **AI Core – Gemini:** phân tích mục tiêu, nghiên cứu tri thức, chọn Workflow/Skill hiện có hoặc đề xuất phiên bản mới.
3. **Workflow Engine – CASL Runtime:** chạy Workflow do Agent điều phối, quản lý state, context, nhánh IF/ELSE, loop, timeout, retry, checkpoint và recovery.
4. **Skill Runtime:** cùng interpreter CASL, chạy script của Skill trong context riêng; Skill có thể gọi Primitive Skill/API hoặc Skill khác.
5. **Primitive Skills:** giao diện năng lực bậc thấp, ví dụ observe.screen, vision.ocr, memory.write, interaction.tap, interaction.swipe, time.wait và calculator.evaluate.
6. **API & Service Layer:** Accessibility, OpenCV, OCR, Gemini Gateway, CADSS, Vision Memory và Android platform APIs.

Workflow không gọi Android API trực tiếp. Skill không gọi Android API tùy ý. Cả hai chỉ gọi API name đã đăng ký trong allow-list; API adapter thực hiện kiểm tra đầu vào và quyền.

## 3. Workflow và Skill là chương trình CASL

Cả hai có manifest và script document:
- format: CASL
- schemaVersion: phiên bản schema của ngôn ngữ
- id: ID ổn định
- version: phiên bản chương trình
- kind: workflow hoặc skill
- inputs / outputs: hợp đồng dữ liệu
- dependencies: Skill/API phụ thuộc
- steps: danh sách AST operation

Một Workflow gọi Skill qua operation skill; một Skill gọi API qua call, hoặc gọi Skill con qua skill. Các operation được interpreter hỗ trợ gồm:
- set: gán biến trong context
- call: gọi API đã đăng ký
- skill: gọi Skill theo ID
- if: điều kiện với nhánh then/else
- repeat: lặp hữu hạn
- wait: chờ theo thời gian
- assert: kiểm tra điều kiện
- log: ghi trace
- return: trả kết quả

Giá trị động tham chiếu bằng object có khóa $ref với path trong context, ví dụ inputs.accountId, vars.screen hoặc vars.result. Không có eval, reflection, shell, Kotlin/Java/JavaScript tùy ý hoặc tải mã thực thi từ xa.

## 4. Ví dụ luồng Workflow

Workflow cấp cao có thể gọi Skill quan sát, kiểm tra trường trạng thái, rồi gọi Skill ghi nhận hoặc chọn nhánh khác. Một Skill có thể gọi API vision.observe, lưu kết quả qua memory.write, rồi trả dữ liệu cho Workflow. Những API name này chỉ hoạt động khi app đăng ký adapter tương ứng; script không thể tự tạo quyền API mới.

Ví dụ cấu trúc logic:
1. Gọi Skill village.observe và lưu kết quả vào vars.observation.
2. IF vars.observation.state equals ready:
   - Gọi Skill village.record với observation.
3. ELSE:
   - Ghi log yêu cầu quan sát lại.
4. RETURN vars.observation.

Đây là ví dụ kiến trúc. Workflow chỉ chạy được khi các Skill được tham chiếu tồn tại, tương thích và đã Verified.

## 5. Runtime responsibilities

Workflow Engine chịu trách nhiệm:
- Chạy Workflow khi Agent yêu cầu.
- Tạo execution context riêng cho từng workflow run và account profile.
- Điều phối Skill, lưu checkpoint và khôi phục sau gián đoạn.
- Thực hiện IF/ELSE, loop, wait, timeout, retry và cancellation.
- Giới hạn step count, loop count, độ sâu gọi lồng nhau và tổng thời gian chờ.
- Ghi trace, kết quả, lỗi và trạng thái vào CADSS.
- Không tiếp tục khi API thất bại hoặc điều kiện xác minh không đạt.

Skill Runtime dùng chung interpreter nhưng có input/output contract và context riêng. Skill chỉ trả dữ liệu; Workflow mới quyết định bước tiếp theo.

## 6. Skill lifecycle và AI authoring

Research → Draft → Testing → Verified / Experimental → Disabled.

- Gemini tạo/sửa CASL program ở dạng Draft.
- Schema validator kiểm tra format, version, operation, dependency, API allow-list và resource limits.
- Test runner kiểm thử dữ liệu vào/ra, nhánh, lỗi, dependency và regression.
- Verified chỉ được cấp sau khi test pass và dependency phù hợp.
- Version cũ được giữ để rollback; không ghi đè lịch sử.
- Workflow và Skill được version độc lập; Workflow manifest ghi rõ version dependency để tránh thay đổi hành vi ngoài ý muốn.

## 7. CADSS và compatibility

- Skill manifests nằm trong collection:skill_registry.
- Workflow scripts nằm trong collection:workflow_registry.
- CASL schemaVersion xác định cú pháp AST; program version xác định phiên bản nghiệp vụ.
- Manifest có min/max Agent version và dependency versions.
- Bổ sung trường tùy chọn là thay đổi tương thích; đổi ý nghĩa operation hoặc kiểu dữ liệu là breaking change, cần schema mới và migration.
- Backup CADSS phải chứa cả manifest, script, dependency, lifecycle, test evidence, execution history và reference IDs của Vision Memory.

## 8. HVCS – Hybrid Visual Coordinate System

HVCS kết hợp:
- Grid cell ID để Gemini tham chiếu vùng ảnh bằng nhãn ổn định.
- Adaptive crop chia nhỏ vùng nghi vấn.
- Bounding box để xác định đối tượng.
- RGB/HSV, luminance và edge density làm đặc trưng.
- OCR text + bounding box để gắn chữ vào vùng ảnh.
- pHash/template reference để tìm mẫu tương tự.
- Tọa độ chuẩn hóa 0..1000 độc lập kích thước màn hình.

Luồng: ảnh toàn màn hình → grid thưa → chọn vùng nghi vấn → crop → grid dày hơn → OCR/template → bounding box → tọa độ chuẩn hóa → xác minh lại trạng thái trước hành động.

Grid không thay thế bounding box. Màu không được dùng làm căn cứ duy nhất để tương tác. Tọa độ phải gắn với kích thước và timestamp của frame.

## 9. Quyền tương tác và an toàn thực thi

Một lần người dùng xác nhận bắt đầu Workflow sẽ cấp quyền chạy Workflow đó theo phạm vi đã duyệt; không cần hỏi lại cho từng thao tác trong cùng run. Pause/Resume/Stop luôn khả dụng. Workflow không được tự mở rộng phạm vi quyền hoặc tự tạo API adapter.

Tất cả thao tác UI đi qua API adapter tập trung và Accessibility Runtime. Adapter kiểm tra trạng thái service, kích thước màn hình, tọa độ, giới hạn thời lượng và kết quả thao tác. Khi trạng thái không chắc chắn, runtime pause hoặc stop thay vì đoán.

## 10. Trạng thái triển khai

Đã có:
- Skill Registry trong CADSS, Skill Library và 43 định danh năng lực built-in.
- Gemini Skill Researcher tạo Skill Draft và yêu cầu CASL script.
- CASL interpreter dùng chung cho Workflow/Skill, hỗ trợ set/call/skill/if/repeat/wait/assert/log/return.
- Allow-list API registry, giới hạn bước/lặp/độ sâu/tổng thời gian chờ.
- CADSS Workflow Registry với version và lifecycle.
- HVCS grid, RGB/HSV, luminance, edge density, crop và normalized coordinates.
- Accessibility runtime Tap/Swipe/Wait và ActionExecutor nền tảng.

Giới hạn hiện tại:
- Không có giao diện Workflow riêng hoặc bộ lập lịch tác vụ; Agent là điểm tương tác duy nhất.

Còn thiếu:
- API adapters thực tế cho vision, OCR, memory, calculator và interaction.
- Nối Workflow Registry/Skill Registry vào Agent planner.
- Execution engine nền có checkpoint/resume bền vững.
- Gemini tool calling với API/Skill catalog có grounding và dependency resolution.
- Test runner thật, migration và version pinning xuyên suốt.
- Observe → Decide → Act → Verify end-to-end.
- Floating dashboard, deep sleep và device tests.

Không được coi catalog hay script mẫu là năng lực đã hoạt động nếu API adapter và test thực tế chưa có.

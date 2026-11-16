# CoC AI Assistant

**Trợ lý AI CoC** là ứng dụng Android AI Agent hướng tới tự động hóa Clash of Clans. Gemini hỗ trợ phân tích và lập kế hoạch; OpenCV và OCR xử lý thị giác cục bộ; Accessibility thực hiện thao tác chạm/vuốt; CADSS và Vision Memory lưu trạng thái; CASL điều phối điều kiện và workflow.

## Thành phần

- **Accessibility Automation:** dịch vụ Android cho phép workflow đã được người dùng xác nhận thực hiện tap/swipe.
- **Gemini Agent:** phân tích ngữ cảnh và tạo bản nháp workflow có cấu trúc; bản nháp phải được kiểm tra trước khi chạy.
- **OpenCV / Vision Memory:** xử lý ảnh cục bộ, pHash và lưu mẫu.
- **CADSS:** dữ liệu Agent có phiên bản, lưu trữ và backup.
- **CASL:** điều kiện IF/ELSE và JSON DSL có kiểm tra.
- **Scheduler / Deep Sleep:** điều phối công việc nền và thời gian nghỉ.
- **Floating Dashboard / Watchdog:** điều khiển tiến trình và phát hiện trạng thái màn hình bất thường.

## Trạng thái triển khai

Nhánh runtime hiện bổ sung nền tảng Accessibility: khai báo service, thao tác tap/swipe/wait có giới hạn, countdown 5 giây, Pause/Resume/Stop, xác thực tọa độ và API chụp màn hình Android 11+. Bảng điều khiển thử đã được gắn vào giao diện.

Các phần OCR, workflow editor JSON hoàn chỉnh, vòng lặp Agent tự xác minh, Gemini draft review, overlay toàn hệ thống và Scheduler chuyên dụng vẫn đang được triển khai. Không xem các phần này là đã hoàn tất cho đến khi có kiểm thử và bằng chứng build tương ứng.

## Quyền riêng tư và kiểm soát

- Chỉ chạy workflow sau thao tác bắt đầu và xác nhận rõ ràng của người dùng.
- Workflow do Gemini đề xuất là bản nháp; không tự động gửi hành động AI chưa kiểm tra tới Accessibility.
- Mọi workflow cần giới hạn bước, thời lượng và số lần lặp; có Pause/Resume/Stop.
- Xử lý ảnh cục bộ theo mặc định; tải ảnh lên Gemini cần sự đồng ý riêng.
- Không vượt qua Play Protect, quyền Android, màn hình bảo vệ hoặc cơ chế anti-cheat. Nếu Android hoặc Play Protect cảnh báo ứng dụng, cần điều tra bản build và chữ ký thay vì tắt bảo vệ.

## Yêu cầu xây dựng

- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- JDK 17, Gradle 8.9
- Android API 23 trở lên; compile/target API 35

## Tài liệu

- [Hướng dẫn sử dụng](docs/HUONG_DAN_SU_DUNG.md)
- [Kiến trúc AI Core Provider](docs/KIEN_TRUC_AI_CORE_PROVIDER.md)
- [Kiến trúc CADSS/CASL](docs/KIEN_TRUC_CADSS_CASL.md)
- [Lộ trình CoC Agent](docs/ROADMAP_COC_AGENT.md)
- [Kiến trúc Skill và HVCS](docs/ARCHITECTURE_SKILLS_HVCS.md)

CI thành công chỉ xác nhận quá trình build; vẫn cần cài và kiểm tra trên thiết bị Android thật, bao gồm khả năng bật Accessibility trên từng phiên bản Android/ColorOS.


## CASL và Workflow Engine

- [Đặc tả CASL JSON DSL, Script Runtime, Workflow Engine và Scheduler](docs/CASL_WORKFLOW_ENGINE.md)


### Workflow Engine

Workflow đã VERIFIED được chạy hoặc lên lịch qua WorkManager; CASL Skill script dùng chung interpreter và API allow-list. Test runner dùng API mock trước khi chuyển Workflow sang VERIFIED. Xem [CASL_WORKFLOW_ENGINE.md](docs/CASL_WORKFLOW_ENGINE.md) để biết schema, lifecycle, run state và giới hạn thực thi.

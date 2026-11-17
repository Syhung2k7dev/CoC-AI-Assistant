# CoC AI Assistant

**CoC AI Assistant** là ứng dụng Android hướng tới mô hình AI Agent điều phối Clash of Clans. Người dùng giao mục tiêu trong **Trung tâm AI**; Agent có thể mở trò chơi, tự chụp màn hình bằng Android Accessibility, chạy OCR cục bộ, phân tích tình huống bằng Gemini và sử dụng CADSS/CASL/Skill để quản lý công việc.

## Trải nghiệm chính

- **Trung tâm AI tiếng Việt:** hội thoại với Agent, nhập mục tiêu, theo dõi trạng thái và nhận phân tích.
- **Thị giác Agent-only:** Agent tự chụp màn hình, chạy OCR/OpenCV và gửi ảnh cho Gemini trong quá trình điều phối; không có trang tải ảnh lên hoặc phân tích ảnh thủ công.
- **API Key:** chỉ nhập trong Cài đặt để kiểm tra kết nối và cấp cho Agent; khóa được che trên giao diện, chỉ giữ trong bộ nhớ phiên, không ghi vào CADSS/backup.
- **Nguyên tắc hệ thống:** người dùng có thể đề xuất chỉnh sửa System Prompt; thay đổi chỉ được lưu sau bước xem lại và xác nhận riêng.
- **Thư viện kỹ năng tiếng Việt:** mỗi Skill có trạng thái Chưa nghiên cứu, Đang nghiên cứu, Đã nghiên cứu, Bản nháp, Đang kiểm thử, Đã xác minh, Thử nghiệm hoặc Đã tắt.
- **Nghiên cứu Skill:** Gemini có thể nghiên cứu một Skill catalog, tạo phiên bản CASL mới, mô tả điều kiện, quy trình, kết quả mong đợi và cách xác minh. Kết quả nghiên cứu không tự động được xem là đã kiểm thử.
- **CADSS / Vision Memory:** lưu trạng thái, phiên bản, dữ liệu và mẫu thị giác.
- **CASL / Workflow Engine:** điều kiện IF/ELSE, điều phối Skill, giới hạn thực thi và lịch chạy.
- **Accessibility Runtime:** thao tác tap/swipe/wait có giới hạn, Pause/Resume/Stop và kiểm tra tọa độ.

## Trạng thái thực tế của Agent

Bản triển khai trên nhánh này bổ sung giao diện Trung tâm AI, tự mở game, chụp ảnh và OCR. Gemini phân tích mục tiêu dựa trên OCR; gửi ảnh lên Gemini là lựa chọn riêng. Thư viện Skill phân biệt rõ kỹ năng chưa nghiên cứu và đã nghiên cứu, đồng thời có luồng tạo phiên bản nghiên cứu mới.

**Vòng lặp Agent:** nút Chạy Agent mở game, tự quan sát OCR/ảnh, yêu cầu Gemini chọn đúng ID/version của Skill VERIFIED, thực thi CASL qua ActionExecutor và xác minh sau mỗi hành động. Không đặt giới hạn cứng số Skill; phiên có ngân sách thời gian 30 phút, từng Skill vẫn có giới hạn bước/thời gian và có nút Dừng. Gemini không được phát tọa độ hoặc thao tác tùy ý.

**Giới hạn cần kiểm chứng:** việc xác minh hiện dựa trên phân tích OCR/ảnh của Gemini, chưa phải bộ xác minh trạng thái UI tất định cho mọi màn hình. Nếu thiếu Skill VERIFIED, không nhận diện được màn hình, AI trả kế hoạch sai schema hoặc không chứng minh được kết quả, Agent sẽ dừng. Chưa có bằng chứng chạy thành công trên thiết bị thật; OCR chưa nhận diện đầy đủ mọi thành phần game.

## Quyền Android và giới hạn

- Người dùng phải tự bật Accessibility trong Cài đặt Android.
- Android có thể từ chối chụp màn hình hoặc thao tác trên cửa sổ được bảo vệ.
- Ứng dụng không tự cấp quyền, không vượt Play Protect, không ẩn hoạt động và không né cơ chế bảo vệ của nền tảng.
- Mọi workflow thực thi cần phạm vi rõ ràng, giới hạn bước/thời gian và khả năng Pause/Stop.
- Không tuyên bố CI thành công là bằng chứng app đã hoạt động trên thiết bị thật.

## Công nghệ và yêu cầu xây dựng

- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- JDK 17, Gradle 8.9
- Android API 23 trở lên; compile/target API 35
- Jetpack Compose, Gemini Provider Runtime, OpenCV, ML Kit OCR, WorkManager

## Tài liệu

- [Hướng dẫn sử dụng](docs/HUONG_DAN_SU_DUNG.md)
- [Lộ trình CoC Agent](docs/ROADMAP_COC_AGENT.md)
- [Kiến trúc Skill và HVCS](docs/ARCHITECTURE_SKILLS_HVCS.md)
- [Đặc tả CASL và Workflow Engine](docs/CASL_WORKFLOW_ENGINE.md)
- [AELM – vòng đời tạo và xác minh Skill](docs/AELM_LIFECYCLE.md)
- [Vision Memory](docs/VISION_MEMORY.md)
- [Kiến trúc AI Core Provider](docs/KIEN_TRUC_AI_CORE_PROVIDER.md)
- [Kiến trúc CADSS/CASL](docs/KIEN_TRUC_CADSS_CASL.md)

## Kiểm thử

GitHub Actions chạy unit tests và build debug APK cho các nhánh main và feature/**. Vẫn cần cài đặt, cấp quyền và kiểm thử trên thiết bị Android thật, bao gồm các thiết bị có tùy biến hệ thống như ColorOS.

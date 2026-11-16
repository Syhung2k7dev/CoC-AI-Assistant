# CoC Agent: Skill Framework và HVCS

## Mục tiêu
Ứng dụng được tổ chức theo mô hình Agent Core + Skill Library. Gemini là AI Core phân tích và lập kế hoạch; Knowledge Base lưu kiến thức; Skill Library chứa năng lực có phiên bản; OpenCV/OCR là lớp quan sát cục bộ; Accessibility là lớp thực thi; CADSS lưu trạng thái và Vision Memory; CASL điều phối điều kiện và workflow.

Multi-account, Attack, Farm và Village Management là Skill cấp cao được ghép từ năng lực quan sát, tri thức, lập kế hoạch, tương tác và xác minh; không phải các tính năng đóng độc lập.

## Phân lớp
- System Prompt: vai trò Agent, mục tiêu, nguyên tắc, giới hạn và quy trình sử dụng Skill.
- Knowledge Base: tri thức dạng văn bản, hình ảnh, OCR và nguồn tham chiếu.
- Skill Registry: metadata, phiên bản, phụ thuộc, capability, trạng thái kiểm thử và độ tin cậy.
- Skill Runtime: nạp Skill và chuyển yêu cầu thành hành động có kiểu.
- ActionExecutor: đầu mối duy nhất chuyển yêu cầu hợp lệ tới Accessibility Runtime.
- Vision Memory: ảnh gốc, crop, pHash, nhãn, tọa độ và đặc trưng thị giác.
- CADSS: lưu trạng thái, Skill, kiến thức, lịch sử và tham chiếu tài nguyên.
- CASL: điều kiện, IF/ELSE, vòng lặp giới hạn và phối hợp Skill.

## Skill lifecycle
1. Research: thu thập nguồn văn bản/ảnh và trạng thái giao diện.
2. Draft: AI tạo mô tả, đầu vào/đầu ra, phụ thuộc và quy trình khai báo.
3. Testing: kiểm thử trên dữ liệu mẫu hoặc môi trường thử nghiệm.
4. Verified: chỉ gán khi bộ kiểm thử đạt.
5. Experimental: thử nghiệm có giám sát và ghi kết quả.
6. Disabled: vô hiệu hóa Skill lỗi thời hoặc không tương thích.

Gemini-generated Skill luôn bắt đầu ở Draft. Skill là dữ liệu khai báo, không phải mã Kotlin/Java hay script tùy ý. Registry kiểm tra ID, độ dài, phiên bản, capability và giới hạn dữ liệu. Draft không được nhảy thẳng sang Verified.

## Nhóm Skill cấp cao
- Observation & Research: ScreenObserver, ScreenshotCapture, ImageGridAnalyzer, RegionCropper, OCRReader, UIElementDetector, GameStateRecognizer, UnknownScreenResearch, KnowledgeExtractor, SkillResearcher.
- Interaction: AutoClick, SmartClick, LongPress, Swipe, DragAndDrop, WaitForState, WaitForText, ActionVerifier, RecoveryAction.
- Multi-account: AccountDiscovery, AccountProfileReader, AccountSwitcher, AccountStateSync, AccountScheduler, AccountTaskQueue, AccountIsolation.
- Attack analysis: BaseScout, BaseClassifier, DefenseRecognizer, TrapAnalyzer, ArmyInspector, ArmyBuilder, AttackPlanner, EntryPointSelector, DeploymentPlanner, SpellTimingPlanner, BattleObserver, AttackVerifier, AttackLearning.
- Farming: LootScanner, LootEstimator, TargetFinder, TargetEvaluator, FarmPlanner, ResourceTracker, UpgradePlanner, BuilderManager, ResearchManager, FarmPerformanceAnalyzer.
- Village: BuildingRecognizer, BuildingLevelReader, UpgradeSelector, ConstructionTracker, ArmyTrainingManager, HeroStatusManager, PetManager, EquipmentManager, VillageProgressAnalyzer.
- Agent: GoalManager, WorkflowComposer, TaskScheduler, DeepSleepManager, GeminiRetryManager, LocalFallback, FailureAnalyzer, SkillGenerator, SkillComposer, SkillTester, SkillImprover, SkillVersionManager, SkillRollback.

Danh mục trên là định danh năng lực mục tiêu; không đồng nghĩa các Skill đã được triển khai hoặc xác minh.

## HVCS – Hybrid Visual Coordinate System
HVCS kết hợp:
- Grid cell ID để Gemini tham chiếu vùng ảnh bằng nhãn ổn định.
- Adaptive crop chia nhỏ vùng nghi vấn.
- Bounding box để xác định đối tượng.
- RGB/HSV, luminance và edge density làm đặc trưng.
- OCR text + bounding box để gắn chữ vào vùng ảnh.
- pHash/template reference để tìm mẫu tương tự.
- Tọa độ chuẩn hóa 0..1000 để độc lập kích thước màn hình.

Luồng: ảnh toàn màn hình → grid thưa → chọn vùng nghi vấn → crop → grid dày hơn → OCR/template → bounding box → chuẩn hóa tọa độ → ActionExecutor kiểm tra kích thước và trạng thái mới nhất.

Grid không thay thế bounding box. Màu không được dùng làm căn cứ duy nhất để tự động chạm. Mọi tọa độ phải gắn với kích thước và thời điểm chụp ảnh.

## Quyền tương tác
Skill khai báo capability như OBSERVE_SCREEN, READ_TEXT, ANALYZE_IMAGE, TAP, SWIPE, WAIT và VERIFY_STATE. Skill chỉ gửi yêu cầu có kiểu; ActionExecutor kiểm tra quyền Accessibility, kích thước màn hình, giới hạn bước/thời lượng và trạng thái xác minh. Không cho phép Skill sinh lệnh hệ thống hoặc mã thực thi tùy ý.

## CADSS và tương thích
- Skill registry dùng collection riêng, có ID ổn định và version tăng đơn điệu.
- Thay đổi metadata tương thích có thể bổ sung trường tùy chọn.
- Thay đổi không tương thích cần schema version mới và migration.
- Khi tích hợp đầy đủ, Skill metadata cần khai báo minAgentVersion, maxAgentVersion và dependency version.
- Backup cần giữ metadata, phiên bản, nguồn và trạng thái kiểm thử; ảnh được lưu riêng và tham chiếu bằng ID.

## Trạng thái hiện tại
Đã có nền tảng registry cục bộ, lifecycle có kiểm tra chuyển trạng thái, capability enum, request có kiểu và HVCS grid analyzer với RGB/HSV/luminance/edge density, tọa độ chuẩn hóa và crop. Chưa có Gemini tool-calling tích hợp với registry, full ActionExecutor, OCR-to-bounding-box fusion, UI editor và end-to-end device tests.

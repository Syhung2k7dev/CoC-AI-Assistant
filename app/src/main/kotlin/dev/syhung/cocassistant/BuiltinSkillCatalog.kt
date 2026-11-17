package dev.syhung.cocassistant

/** Initial declarative catalog. Entries describe capabilities; they are not executable workflows. */
object BuiltinSkillCatalog {
    private data class Definition(
        val id: String, val name: String, val category: String, val description: String,
        val inputs: List<String> = emptyList(), val outputs: List<String> = emptyList(),
        val dependencies: List<String> = emptyList(),
        val capabilities: Set<SkillActionCapability> = emptySet()
    )

    private val definitions = listOf(
        Definition("screen.observe", "Screen Observer", "observation", "Phân tích trạng thái màn hình hiện tại.", outputs = listOf("screen_state"), capabilities = setOf(SkillActionCapability.OBSERVE_SCREEN)),
        Definition("screen.capture", "Screenshot Capture", "observation", "Yêu cầu ảnh chụp màn hình theo quyền Android.", outputs = listOf("bitmap"), capabilities = setOf(SkillActionCapability.OBSERVE_SCREEN)),
        Definition("vision.grid", "Image Grid Analyzer", "vision", "Tạo lưới HVCS và đặc trưng từng ô.", inputs = listOf("bitmap"), outputs = listOf("grid_metadata"), dependencies = listOf("screen.capture"), capabilities = setOf(SkillActionCapability.ANALYZE_IMAGE)),
        Definition("vision.crop", "Region Cropper", "vision", "Cắt vùng ảnh bằng tọa độ chuẩn hóa.", inputs = listOf("bitmap", "normalized_bounds"), outputs = listOf("crop"), capabilities = setOf(SkillActionCapability.ANALYZE_IMAGE)),
        Definition("vision.ocr", "OCR Reader", "vision", "Đọc văn bản và vị trí văn bản từ ảnh.", inputs = listOf("bitmap"), outputs = listOf("text_regions"), dependencies = listOf("screen.capture"), capabilities = setOf(SkillActionCapability.READ_TEXT)),
        Definition("vision.template", "Template Matcher", "vision", "So khớp vùng ảnh với mẫu đã xác minh.", inputs = listOf("crop", "template_id"), outputs = listOf("match_score", "bounds"), capabilities = setOf(SkillActionCapability.ANALYZE_IMAGE)),
        Definition("vision.state", "Game State Recognizer", "observation", "Phân loại màn hình và trạng thái giao diện.", inputs = listOf("grid_metadata", "text_regions"), outputs = listOf("state"), dependencies = listOf("vision.grid", "vision.ocr"), capabilities = setOf(SkillActionCapability.ANALYZE_IMAGE)),
        Definition("research.unknown_screen", "Unknown Screen Research", "research", "Thu thập và phân loại bằng chứng cho màn hình chưa biết.", inputs = listOf("bitmap", "grid_metadata"), outputs = listOf("research_record"), capabilities = setOf(SkillActionCapability.ANALYZE_IMAGE)),
        Definition("research.knowledge", "Knowledge Extractor", "research", "Trích xuất kiến thức có nguồn và độ tin cậy.", inputs = listOf("documents", "images"), outputs = listOf("knowledge_record")),
        Definition("research.skill", "Skill Researcher", "research", "Tìm hiểu năng lực còn thiếu và tạo đề xuất Skill.", inputs = listOf("goal", "skill_inventory"), outputs = listOf("skill_draft")),
        Definition("skill.generate", "Skill Generator", "agent", "Tạo Skill khai báo ở trạng thái Draft.", inputs = listOf("skill_spec"), outputs = listOf("draft_skill")),
        Definition("skill.test", "Skill Tester", "agent", "Kiểm thử schema, dependency và kết quả mẫu.", inputs = listOf("draft_skill", "test_cases"), outputs = listOf("test_report")),
        Definition("skill.compose", "Skill Composer", "agent", "Kết hợp các Skill tương thích thành workflow.", inputs = listOf("skill_ids"), outputs = listOf("workflow_draft")),
        Definition("skill.improve", "Skill Improver", "agent", "Đề xuất cải tiến dựa trên lỗi và kết quả đo.", inputs = listOf("skill_id", "test_report"), outputs = listOf("new_version_draft")),
        Definition("action.tap", "Auto Click", "interaction", "Yêu cầu chạm tại tọa độ chuẩn hóa đã xác minh.", inputs = listOf("normalized_point"), outputs = listOf("action_result"), capabilities = setOf(SkillActionCapability.TAP, SkillActionCapability.VERIFY_STATE)),
        Definition("action.swipe", "Swipe", "interaction", "Yêu cầu vuốt với điểm đầu và cuối hợp lệ.", inputs = listOf("normalized_path"), outputs = listOf("action_result"), capabilities = setOf(SkillActionCapability.SWIPE, SkillActionCapability.VERIFY_STATE)),
        Definition("action.wait_state", "Wait For State", "interaction", "Chờ trạng thái giao diện đạt điều kiện.", inputs = listOf("state_condition", "timeout"), outputs = listOf("wait_result"), capabilities = setOf(SkillActionCapability.WAIT, SkillActionCapability.OBSERVE_SCREEN)),
        Definition("action.verify", "Action Verifier", "interaction", "Đối chiếu trạng thái sau thao tác.", inputs = listOf("expected_state", "observed_state"), outputs = listOf("verified"), capabilities = setOf(SkillActionCapability.VERIFY_STATE)),
        Definition("account.discover", "Account Discovery", "account", "Nhận diện hồ sơ đang hoạt động mà không đọc bí mật xác thực.", outputs = listOf("account_profile")),
        Definition("account.switch", "Account Switcher", "account", "Điều phối chuyển hồ sơ đã được người dùng thiết lập.", inputs = listOf("account_profile"), outputs = listOf("switch_result")),
        Definition("account.sync", "Account State Sync", "account", "Đồng bộ tiến trình vào CADSS theo từng hồ sơ.", inputs = listOf("account_profile", "observed_state"), outputs = listOf("saved_state")),
        Definition("account.schedule", "Account Scheduler", "account", "Xếp hàng tác vụ theo hồ sơ và lịch người dùng.", inputs = listOf("account_id", "task"), outputs = listOf("scheduled_task")),
        Definition("base.scout", "Base Scout", "attack_analysis", "Phân tích bố cục căn cứ từ dữ liệu thị giác.", inputs = listOf("grid_metadata", "text_regions"), outputs = listOf("base_map"), dependencies = listOf("vision.grid", "vision.ocr")),
        Definition("base.classify", "Base Classifier", "attack_analysis", "Phân loại mẫu bố cục và vùng chức năng.", inputs = listOf("base_map"), outputs = listOf("base_class")),
        Definition("army.inspect", "Army Inspector", "attack_analysis", "Đọc thành phần quân đội và trạng thái sẵn sàng.", inputs = listOf("screen_state"), outputs = listOf("army_inventory")),
        Definition("army.build", "Army Builder", "attack_analysis", "Đề xuất thành phần quân đội theo mục tiêu và dữ liệu hiện có.", inputs = listOf("goal", "army_inventory"), outputs = listOf("army_plan")),
        Definition("attack.plan", "Attack Planner", "attack_analysis", "Tạo kế hoạch trận đánh có cấu trúc để người dùng xem lại.", inputs = listOf("base_map", "army_plan"), outputs = listOf("attack_plan")),
        Definition("battle.observe", "Battle Observer", "attack_analysis", "Theo dõi trạng thái trận đánh qua các lần quan sát.", inputs = listOf("screen_state"), outputs = listOf("battle_state")),
        Definition("battle.learn", "Attack Learning", "learning", "Ghi nhận kết quả và rút kinh nghiệm có nguồn.", inputs = listOf("attack_plan", "battle_result"), outputs = listOf("learning_record")),
        Definition("loot.scan", "Loot Scanner", "farming", "Đọc chỉ số tài nguyên hiển thị.", inputs = listOf("text_regions", "grid_metadata"), outputs = listOf("loot_snapshot")),
        Definition("loot.evaluate", "Target Evaluator", "farming", "Đánh giá mục tiêu theo tiêu chí do người dùng đặt.", inputs = listOf("loot_snapshot", "goal"), outputs = listOf("target_score")),
        Definition("farm.plan", "Farm Planner", "farming", "Lập kế hoạch thu thập tài nguyên dựa trên trạng thái quan sát.", inputs = listOf("resource_state", "goal"), outputs = listOf("farm_plan")),
        Definition("resource.track", "Resource Tracker", "farming", "Theo dõi thay đổi tài nguyên theo thời gian.", inputs = listOf("resource_snapshots"), outputs = listOf("resource_delta")),
        Definition("village.buildings", "Building Recognizer", "village", "Nhận diện công trình và trạng thái hiển thị.", inputs = listOf("grid_metadata"), outputs = listOf("building_map")),
        Definition("village.upgrade", "Upgrade Planner", "village", "Sắp xếp đề xuất nâng cấp theo mục tiêu.", inputs = listOf("building_map", "resource_state"), outputs = listOf("upgrade_plan")),
        Definition("village.training", "Army Training Manager", "village", "Theo dõi hàng đợi huấn luyện và trạng thái quân đội.", inputs = listOf("screen_state"), outputs = listOf("training_state")),
        Definition("village.progress", "Village Progress Analyzer", "village", "Tổng hợp tiến trình làng theo dữ liệu đã quan sát.", inputs = listOf("account_state"), outputs = listOf("progress_report")),
        Definition("memory.read", "Memory Reader", "primitive", "Đọc dữ liệu có cấu trúc từ CADSS theo collection và khóa.", inputs = listOf("collection", "key"), outputs = listOf("record")),
        Definition("memory.write", "Memory Writer", "primitive", "Ghi hoặc cập nhật bản ghi có schema vào CADSS.", inputs = listOf("collection", "key", "value"), outputs = listOf("record_id")),
        Definition("memory.append", "Memory Event Writer", "primitive", "Ghi sự kiện bất biến vào lịch sử thực thi.", inputs = listOf("event"), outputs = listOf("event_id")),
        Definition("calculator.evaluate", "Calculator", "primitive", "Tính biểu thức an toàn từ biến đầu vào đã định kiểu.", inputs = listOf("expression", "variables"), outputs = listOf("value")),
        Definition("time.wait", "Time Wait", "primitive", "Chờ theo đồng hồ Workflow Engine với giới hạn thời gian.", inputs = listOf("duration_ms"), outputs = listOf("elapsed_ms"), capabilities = setOf(SkillActionCapability.WAIT)),
        Definition("workflow.checkpoint", "Workflow Checkpoint", "primitive", "Lưu trạng thái và vị trí thực thi để phục hồi Workflow.", inputs = listOf("run_id", "state", "step_index"), outputs = listOf("checkpoint_id")),
        Definition("scheduler.schedule", "Schedule Workflow", "primitive", "Đăng ký Workflow theo trigger hoặc lịch do người dùng đặt.", inputs = listOf("workflow_id", "version", "trigger"), outputs = listOf("schedule_id")),
        Definition("scheduler.cancel", "Cancel Schedule", "primitive", "Hủy lịch Workflow theo schedule ID.", inputs = listOf("schedule_id"), outputs = listOf("cancelled")),
        Definition("agent.goal", "Goal Manager", "agent", "Phân rã mục tiêu người dùng thành nhiệm vụ có thể xác minh.", inputs = listOf("user_goal"), outputs = listOf("task_graph")),
        Definition("agent.workflow", "Workflow Composer", "agent", "Tạo workflow CASL từ các Skill tương thích.", inputs = listOf("task_graph", "skill_inventory"), outputs = listOf("workflow_draft")),
        Definition("agent.retry", "Gemini Retry Manager", "agent", "Quản lý retry có backoff khi dịch vụ AI tạm thời không khả dụng.", inputs = listOf("request", "error"), outputs = listOf("retry_decision")),
        Definition("agent.fallback", "Local Fallback", "agent", "Chọn xử lý cục bộ khi Gemini tạm thời không sẵn sàng.", inputs = listOf("task", "local_capabilities"), outputs = listOf("fallback_plan")),
        Definition("agent.failure", "Failure Analyzer", "learning", "Phân tích nguyên nhân workflow thất bại.", inputs = listOf("execution_trace"), outputs = listOf("failure_report")),
        Definition("agent.scheduler", "Task Scheduler", "agent", "Lập lịch tác vụ có giới hạn và khả năng tiếp tục.", inputs = listOf("task_graph", "schedule"), outputs = listOf("scheduled_work"))
    )

    fun all(): List<AgentSkill> = definitions.map { item ->
        AgentSkill(
            id = item.id, name = item.name, description = item.description,
            category = item.category, level = levelFor(item.id), version = 1, status = SkillLifecycle.DRAFT,
            inputs = item.inputs, outputs = item.outputs, dependencies = item.dependencies,
            capabilities = item.capabilities, source = "builtin_catalog", confidence = 0f
        )
    }

    fun install(registry: AgentSkillRegistry): Int {
        var added = 0
        all().forEach { skill ->
            val existing = registry.find(skill.id)
            if (existing == null) {
                if (registry.saveDraft(skill)) added++
            } else if (existing.source == "builtin_catalog" && existing.level != skill.level) {
                registry.updateBuiltinLevel(skill.id, skill.level)
            }
        }
        return added
    }

    private fun levelFor(id: String): SkillLevel = when (id) {
        "screen.observe", "screen.capture", "vision.crop", "vision.template",
        "action.tap", "action.swipe", "action.wait_state", "action.verify",
        "memory.read", "memory.write", "memory.append", "calculator.evaluate",
        "time.wait", "workflow.checkpoint", "scheduler.schedule", "scheduler.cancel" ->
            SkillLevel.L0_PRIMITIVE

        "vision.state", "base.scout", "skill.compose", "skill.generate", "skill.test",
        "skill.improve", "farm.plan" -> SkillLevel.L2_COMPOSITE

        "attack.plan", "agent.goal", "agent.workflow", "agent.scheduler" ->
            SkillLevel.L3_ORCHESTRATOR

        else -> SkillLevel.L1_ATOMIC
    }
}

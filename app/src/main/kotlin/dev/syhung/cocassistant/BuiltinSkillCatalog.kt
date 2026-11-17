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
        Definition("screen.observe", "Quan sát màn hình", "observation", "Phân tích trạng thái màn hình hiện tại.", outputs = listOf("screen_state"), capabilities = setOf(SkillActionCapability.OBSERVE_SCREEN)),
        Definition("screen.capture", "Chụp màn hình", "observation", "Yêu cầu ảnh chụp màn hình theo quyền Android.", outputs = listOf("bitmap"), capabilities = setOf(SkillActionCapability.OBSERVE_SCREEN)),
        Definition("vision.grid", "Phân tích lưới ảnh", "vision", "Tạo lưới HVCS và đặc trưng từng ô.", inputs = listOf("bitmap"), outputs = listOf("grid_metadata"), dependencies = listOf("screen.capture"), capabilities = setOf(SkillActionCapability.ANALYZE_IMAGE)),
        Definition("vision.crop", "Cắt vùng ảnh", "vision", "Cắt vùng ảnh bằng tọa độ chuẩn hóa.", inputs = listOf("bitmap", "normalized_bounds"), outputs = listOf("crop"), capabilities = setOf(SkillActionCapability.ANALYZE_IMAGE)),
        Definition("vision.ocr", "Nhận diện văn bản", "vision", "Đọc văn bản và vị trí văn bản từ ảnh.", inputs = listOf("bitmap"), outputs = listOf("text_regions"), dependencies = listOf("screen.capture"), capabilities = setOf(SkillActionCapability.READ_TEXT)),
        Definition("vision.template", "So khớp mẫu hình ảnh", "vision", "So khớp vùng ảnh với mẫu đã xác minh.", inputs = listOf("crop", "template_id"), outputs = listOf("match_score", "bounds"), capabilities = setOf(SkillActionCapability.ANALYZE_IMAGE)),
        Definition("vision.state", "Nhận diện trạng thái trò chơi", "observation", "Phân loại màn hình và trạng thái giao diện.", inputs = listOf("grid_metadata", "text_regions"), outputs = listOf("state"), dependencies = listOf("vision.grid", "vision.ocr"), capabilities = setOf(SkillActionCapability.ANALYZE_IMAGE)),
        Definition("research.unknown_screen", "Nghiên cứu màn hình lạ", "research", "Thu thập và phân loại bằng chứng cho màn hình chưa biết.", inputs = listOf("bitmap", "grid_metadata"), outputs = listOf("research_record"), capabilities = setOf(SkillActionCapability.ANALYZE_IMAGE)),
        Definition("research.knowledge", "Trích xuất tri thức", "research", "Trích xuất kiến thức có nguồn và độ tin cậy.", inputs = listOf("documents", "images"), outputs = listOf("knowledge_record")),
        Definition("research.skill", "Nghiên cứu kỹ năng", "research", "Tìm hiểu năng lực còn thiếu và tạo đề xuất Skill.", inputs = listOf("goal", "skill_inventory"), outputs = listOf("skill_draft")),
        Definition("skill.generate", "Tạo kỹ năng", "agent", "Tạo Skill khai báo ở trạng thái Draft.", inputs = listOf("skill_spec"), outputs = listOf("draft_skill")),
        Definition("skill.test", "Kiểm thử kỹ năng", "agent", "Kiểm thử schema, dependency và kết quả mẫu.", inputs = listOf("draft_skill", "test_cases"), outputs = listOf("test_report")),
        Definition("skill.compose", "Ghép kỹ năng", "agent", "Kết hợp các Skill tương thích thành workflow.", inputs = listOf("skill_ids"), outputs = listOf("workflow_draft")),
        Definition("skill.improve", "Cải tiến kỹ năng", "agent", "Đề xuất cải tiến dựa trên lỗi và kết quả đo.", inputs = listOf("skill_id", "test_report"), outputs = listOf("new_version_draft")),
        Definition("action.tap", "Chạm tự động", "interaction", "Yêu cầu chạm tại tọa độ chuẩn hóa đã xác minh.", inputs = listOf("normalized_point"), outputs = listOf("action_result"), capabilities = setOf(SkillActionCapability.TAP, SkillActionCapability.VERIFY_STATE)),
        Definition("action.swipe", "Vuốt màn hình", "interaction", "Yêu cầu vuốt với điểm đầu và cuối hợp lệ.", inputs = listOf("normalized_path"), outputs = listOf("action_result"), capabilities = setOf(SkillActionCapability.SWIPE, SkillActionCapability.VERIFY_STATE)),
        Definition("action.wait_state", "Chờ trạng thái", "interaction", "Chờ trạng thái giao diện đạt điều kiện.", inputs = listOf("state_condition", "timeout"), outputs = listOf("wait_result"), capabilities = setOf(SkillActionCapability.WAIT, SkillActionCapability.OBSERVE_SCREEN)),
        Definition("action.verify", "Xác minh thao tác", "interaction", "Đối chiếu trạng thái sau thao tác.", inputs = listOf("expected_state", "observed_state"), outputs = listOf("verified"), capabilities = setOf(SkillActionCapability.VERIFY_STATE)),
        Definition("account.discover", "Nhận diện tài khoản", "account", "Nhận diện hồ sơ đang hoạt động mà không đọc bí mật xác thực.", outputs = listOf("account_profile")),
        Definition("account.switch", "Chuyển tài khoản", "account", "Điều phối chuyển hồ sơ đã được người dùng thiết lập.", inputs = listOf("account_profile"), outputs = listOf("switch_result")),
        Definition("account.sync", "Đồng bộ trạng thái tài khoản", "account", "Đồng bộ tiến trình vào CADSS theo từng hồ sơ.", inputs = listOf("account_profile", "observed_state"), outputs = listOf("saved_state")),
        Definition("account.schedule", "Lập lịch tài khoản", "account", "Xếp hàng tác vụ theo hồ sơ và lịch người dùng.", inputs = listOf("account_id", "task"), outputs = listOf("scheduled_task")),
        Definition("base.scout", "Khảo sát căn cứ", "attack_analysis", "Phân tích bố cục căn cứ từ dữ liệu thị giác.", inputs = listOf("grid_metadata", "text_regions"), outputs = listOf("base_map"), dependencies = listOf("vision.grid", "vision.ocr")),
        Definition("base.classify", "Phân loại căn cứ", "attack_analysis", "Phân loại mẫu bố cục và vùng chức năng.", inputs = listOf("base_map"), outputs = listOf("base_class")),
        Definition("army.inspect", "Kiểm tra quân đội", "attack_analysis", "Đọc thành phần quân đội và trạng thái sẵn sàng.", inputs = listOf("screen_state"), outputs = listOf("army_inventory")),
        Definition("army.build", "Xây dựng đội quân", "attack_analysis", "Đề xuất thành phần quân đội theo mục tiêu và dữ liệu hiện có.", inputs = listOf("goal", "army_inventory"), outputs = listOf("army_plan")),
        Definition("attack.plan", "Lập kế hoạch trận đánh", "attack_analysis", "Tạo kế hoạch trận đánh có cấu trúc để người dùng xem lại.", inputs = listOf("base_map", "army_plan"), outputs = listOf("attack_plan")),
        Definition("battle.observe", "Quan sát trận đánh", "attack_analysis", "Theo dõi trạng thái trận đánh qua các lần quan sát.", inputs = listOf("screen_state"), outputs = listOf("battle_state")),
        Definition("battle.learn", "Học từ kết quả trận đánh", "learning", "Ghi nhận kết quả và rút kinh nghiệm có nguồn.", inputs = listOf("attack_plan", "battle_result"), outputs = listOf("learning_record")),
        Definition("loot.scan", "Quét tài nguyên", "farming", "Đọc chỉ số tài nguyên hiển thị.", inputs = listOf("text_regions", "grid_metadata"), outputs = listOf("loot_snapshot")),
        Definition("loot.evaluate", "Đánh giá mục tiêu", "farming", "Đánh giá mục tiêu theo tiêu chí do người dùng đặt.", inputs = listOf("loot_snapshot", "goal"), outputs = listOf("target_score")),
        Definition("farm.plan", "Lập kế hoạch thu thập", "farming", "Lập kế hoạch thu thập tài nguyên dựa trên trạng thái quan sát.", inputs = listOf("resource_state", "goal"), outputs = listOf("farm_plan")),
        Definition("resource.track", "Theo dõi tài nguyên", "farming", "Theo dõi thay đổi tài nguyên theo thời gian.", inputs = listOf("resource_snapshots"), outputs = listOf("resource_delta")),
        Definition("village.buildings", "Nhận diện công trình", "village", "Nhận diện công trình và trạng thái hiển thị.", inputs = listOf("grid_metadata"), outputs = listOf("building_map")),
        Definition("village.upgrade", "Lập kế hoạch nâng cấp", "village", "Sắp xếp đề xuất nâng cấp theo mục tiêu.", inputs = listOf("building_map", "resource_state"), outputs = listOf("upgrade_plan")),
        Definition("village.training", "Quản lý huấn luyện", "village", "Theo dõi hàng đợi huấn luyện và trạng thái quân đội.", inputs = listOf("screen_state"), outputs = listOf("training_state")),
        Definition("village.progress", "Phân tích tiến trình làng", "village", "Tổng hợp tiến trình làng theo dữ liệu đã quan sát.", inputs = listOf("account_state"), outputs = listOf("progress_report")),
        Definition("memory.read", "Đọc bộ nhớ", "primitive", "Đọc dữ liệu có cấu trúc từ CADSS theo collection và khóa.", inputs = listOf("collection", "key"), outputs = listOf("record")),
        Definition("memory.write", "Ghi bộ nhớ", "primitive", "Ghi hoặc cập nhật bản ghi có schema vào CADSS.", inputs = listOf("collection", "key", "value"), outputs = listOf("record_id")),
        Definition("memory.append", "Ghi sự kiện bộ nhớ", "primitive", "Ghi sự kiện bất biến vào lịch sử thực thi.", inputs = listOf("event"), outputs = listOf("event_id")),
        Definition("calculator.evaluate", "Máy tính", "primitive", "Tính biểu thức an toàn từ biến đầu vào đã định kiểu.", inputs = listOf("expression", "variables"), outputs = listOf("value")),
        Definition("time.wait", "Chờ theo thời gian", "primitive", "Chờ theo đồng hồ Workflow Engine với giới hạn thời gian.", inputs = listOf("duration_ms"), outputs = listOf("elapsed_ms"), capabilities = setOf(SkillActionCapability.WAIT)),
        Definition("workflow.checkpoint", "Lưu điểm khôi phục quy trình", "primitive", "Lưu trạng thái và vị trí thực thi để phục hồi Workflow.", inputs = listOf("run_id", "state", "step_index"), outputs = listOf("checkpoint_id")),
        Definition("scheduler.schedule", "Lập lịch quy trình", "primitive", "Đăng ký Workflow theo trigger hoặc lịch do người dùng đặt.", inputs = listOf("workflow_id", "version", "trigger"), outputs = listOf("schedule_id")),
        Definition("scheduler.cancel", "Hủy lịch quy trình", "primitive", "Hủy lịch Workflow theo schedule ID.", inputs = listOf("schedule_id"), outputs = listOf("cancelled")),
        Definition("agent.goal", "Quản lý mục tiêu", "agent", "Phân rã mục tiêu người dùng thành nhiệm vụ có thể xác minh.", inputs = listOf("user_goal"), outputs = listOf("task_graph")),
        Definition("agent.workflow", "Tạo quy trình", "agent", "Tạo workflow CASL từ các Skill tương thích.", inputs = listOf("task_graph", "skill_inventory"), outputs = listOf("workflow_draft")),
        Definition("agent.retry", "Quản lý thử lại Gemini", "agent", "Quản lý retry có backoff khi dịch vụ AI tạm thời không khả dụng.", inputs = listOf("request", "error"), outputs = listOf("retry_decision")),
        Definition("agent.fallback", "Phương án dự phòng cục bộ", "agent", "Chọn xử lý cục bộ khi Gemini tạm thời không sẵn sàng.", inputs = listOf("task", "local_capabilities"), outputs = listOf("fallback_plan")),
        Definition("agent.failure", "Phân tích lỗi", "learning", "Phân tích nguyên nhân workflow thất bại.", inputs = listOf("execution_trace"), outputs = listOf("failure_report")),
        Definition("agent.scheduler", "Lập lịch tác vụ", "agent", "Lập lịch tác vụ có giới hạn và khả năng tiếp tục.", inputs = listOf("task_graph", "schedule"), outputs = listOf("scheduled_work"))
    )

    fun all(): List<AgentSkill> = definitions.map { item ->
        AgentSkill(
            id = item.id, name = item.name, description = item.description,
            category = item.category, version = 1, status = SkillLifecycle.RESEARCH_REQUIRED,
            inputs = item.inputs, outputs = item.outputs, dependencies = item.dependencies,
            capabilities = item.capabilities, source = "builtin_catalog", confidence = 0f
        )
    }

    fun install(registry: AgentSkillRegistry): Int {
        var added = 0
        all().forEach {
            if (registry.find(it.id) == null && registry.saveCatalogSkill(it)) added++
            else registry.markCatalogForResearch(it)
        }
        return added
    }
}

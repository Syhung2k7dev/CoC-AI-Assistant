package dev.syhung.cocassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun SkillLibraryPanel(
    registry: AgentSkillRegistry,
    apiKey: String,
) {
    val context = LocalContext.current
    val skillsState = remember { mutableStateOf(registry.all()) }
    val goalState = remember { mutableStateOf("") }
    val statusState = remember { mutableStateOf("") }
    val activeJob = remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val busy = activeJob.value != null
    val skills = skillsState.value.groupBy { it.id }.values.mapNotNull { versions ->
        versions.maxByOrNull { it.version }
    }.sortedWith(compareBy<AgentSkill> { it.category }.thenBy { it.name })
    val categories = skills.map { it.category }.distinct()
    val pendingCount = skills.count { it.status == SkillLifecycle.RESEARCH_REQUIRED }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("THƯ VIỆN KỸ NĂNG", color = Color(0xFF4DD8D0), fontSize = 10.sp)
        Text("${skills.size} kỹ năng · ${categories.size} nhóm", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Text(
            "Research Agent tự mở CoC, chụp màn hình, gửi Gemini, suy luận và thực hiện thao tác khám phá trong game. Mỗi vòng đều lưu bằng chứng và cập nhật database.",
            color = Color(0xFF9AA8C0), fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkillCountChip("Chưa nghiên cứu", pendingCount)
            SkillCountChip("Đã nghiên cứu", skills.count { it.status == SkillLifecycle.RESEARCHED })
            SkillCountChip("Đã xác minh", skills.count { it.status == SkillLifecycle.VERIFIED })
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF202D43))
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AUTONOMOUS SKILL RESEARCH", color = Color(0xFF4DD8D0), fontSize = 10.sp)
                Text(
                    "Vòng lặp quan sát → Gemini suy luận → thao tác trong CoC → chụp lại → xác minh → cập nhật Skill/CASL/database. Nghiên cứu hàng loạt chạy tuần tự từng Skill.",
                    color = Color.White, fontSize = 12.sp
                )
                Button(
                    onClick = {
                        activeJob.value = scope.launch {
                            try {
                                statusState.value = "Đang chuẩn bị hàng đợi nghiên cứu..."
                                val report = AutonomousSkillResearchLoop.researchPendingBatch(
                                    context, apiKey, registry
                                ) { completed, total, label ->
                                    skillsState.value = registry.all()
                                    statusState.value = "Hàng loạt $completed/$total · $label"
                                }
                                skillsState.value = registry.all()
                                statusState.value = if (report.total == 0) {
                                    report.failures.firstOrNull()?.second ?: "Không có Skill đang chờ nghiên cứu."
                                } else {
                                    "Hoàn tất: ${report.succeeded}/${report.total} Skill nghiên cứu thành công; ${report.failures.size} thất bại." +
                                        if (report.failures.isEmpty()) "" else
                                            "\n" + report.failures.take(6).joinToString("\n") { "${it.first}: ${it.second}" }
                                }
                            } catch (error: Exception) {
                                statusState.value = "Nghiên cứu hàng loạt đã dừng: ${error.message ?: "lỗi không xác định"}"
                            } finally {
                                skillsState.value = registry.all()
                                activeJob.value = null
                            }
                        }
                    },
                    enabled = !busy && pendingCount > 0 && apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B7CFF))
                ) {
                    Text(if (busy) "Research Agent đang chạy..." else "Nghiên cứu hàng loạt ($pendingCount)")
                }
                if (busy) {
                    OutlinedButton(
                        onClick = {
                            CoCAccessibilityService.stop()
                            activeJob.value?.cancel()
                            statusState.value = "Đã gửi yêu cầu dừng phiên nghiên cứu."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Dừng nghiên cứu ngay") }
                }
                if (apiKey.isBlank()) {
                    Text("Cần nhập Gemini API Key trong Cài đặt. Khóa chỉ tồn tại trong bộ nhớ phiên.", color = Color(0xFFFFC66D), fontSize = 10.sp)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B263A))
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("NGHIÊN CỨU SKILL MỚI", color = Color(0xFF4DD8D0), fontSize = 10.sp)
                OutlinedTextField(
                    value = goalState.value,
                    onValueChange = { goalState.value = it.take(1600) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mục tiêu nghiên cứu") },
                    minLines = 3
                )
                Button(
                    onClick = {
                        activeJob.value = scope.launch {
                            statusState.value = "Đang mở CoC và bắt đầu nghiên cứu từ màn hình thực tế..."
                            try {
                                when (val result = AutonomousSkillResearchLoop.researchNewGoal(
                                    context, apiKey, goalState.value, registry
                                ) { stage -> statusState.value = stage }) {
                                    is SkillDraftOutcome.Created -> {
                                        skillsState.value = registry.all()
                                        statusState.value = "Đã nghiên cứu Skill ${result.skill.name} v${result.skill.version}. Đã lưu quy trình, CASL và bằng chứng; cần kiểm thử trước khi xác minh."
                                    }
                                    is SkillDraftOutcome.Failed -> statusState.value = result.reason
                                }
                            } catch (error: Exception) {
                                statusState.value = "Research Agent đã dừng: ${error.message ?: "lỗi không xác định"}"
                            } finally {
                                skillsState.value = registry.all()
                                activeJob.value = null
                            }
                        }
                    },
                    enabled = !busy && goalState.value.isNotBlank() && apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B7CFF))
                ) { Text("Bắt đầu nghiên cứu tự động") }
            }
        }

        categories.forEach { category ->
            Text(skillCategoryLabel(category), color = Color(0xFF4DD8D0), fontSize = 10.sp)
            skills.filter { it.category == category }.forEach { skill ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2B))
                ) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(skill.name, color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(skillStatusLabel(skill.status), color = Color(0xFFFFC66D), fontSize = 9.sp)
                        }
                        Text(skill.description, color = Color(0xFF9AA8C0), fontSize = 11.sp)
                        Text("Mã: ${skill.id} · phiên bản ${skill.version}", color = Color(0xFF71819D), fontSize = 9.sp)
                        if (skill.status == SkillLifecycle.RESEARCH_REQUIRED) {
                            OutlinedButton(
                                onClick = {
                                    activeJob.value = scope.launch {
                                        statusState.value = "Đang khởi động Research Agent cho ${skill.name}..."
                                        try {
                                            when (val result = AutonomousSkillResearchLoop.researchExisting(
                                                context, apiKey, skill.id, registry
                                            ) { stage -> statusState.value = "${skill.name} · $stage" }) {
                                                is SkillDraftOutcome.Created -> statusState.value =
                                                    "Đã nghiên cứu ${result.skill.name} v${result.skill.version}; CASL và bằng chứng đã lưu. Chưa được phép thực thi."
                                                is SkillDraftOutcome.Failed -> statusState.value = result.reason
                                            }
                                        } catch (error: Exception) {
                                            statusState.value = "Nghiên cứu thất bại: ${error.message}"
                                        } finally {
                                            skillsState.value = registry.all()
                                            activeJob.value = null
                                        }
                                    }
                                },
                                enabled = !busy && apiKey.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Nghiên cứu Skill bằng CoC Agent") }
                        }
                        if (skill.status == SkillLifecycle.RESEARCHED) {
                            Text("Đã có hồ sơ thực nghiệm và CASL. Cần chạy kiểm thử độc lập trước khi xác minh.", color = Color(0xFFFFC66D), fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        if (statusState.value.isNotBlank()) {
            Text(statusState.value, color = Color(0xFF4DD8D0), fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SkillCountChip(label: String, count: Int) {
    Surface(color = Color(0xFF1B263A), shape = RoundedCornerShape(30.dp)) {
        Text(label + ": " + count, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Color.White, fontSize = 10.sp)
    }
}

private fun skillStatusLabel(status: SkillLifecycle): String = when (status) {
    SkillLifecycle.RESEARCH_REQUIRED -> "CHƯA NGHIÊN CỨU"
    SkillLifecycle.RESEARCHING -> "ĐANG NGHIÊN CỨU"
    SkillLifecycle.RESEARCHED -> "ĐÃ NGHIÊN CỨU · CHỜ KIỂM THỬ"
    SkillLifecycle.DRAFT -> "BẢN NHÁP"
    SkillLifecycle.TESTING -> "ĐANG KIỂM THỬ"
    SkillLifecycle.VERIFIED -> "ĐÃ XÁC MINH"
    SkillLifecycle.EXPERIMENTAL -> "THỬ NGHIỆM"
    SkillLifecycle.DISABLED -> "ĐÃ TẮT"
}

private fun skillCategoryLabel(category: String): String = when (category) {
    "observation" -> "QUAN SÁT"
    "vision" -> "THỊ GIÁC"
    "research" -> "NGHIÊN CỨU"
    "agent" -> "ĐIỀU PHỐI AI"
    "interaction" -> "TƯƠNG TÁC"
    "account" -> "TÀI KHOẢN"
    "attack_analysis" -> "PHÂN TÍCH TRẬN ĐÁNH"
    "learning" -> "HỌC HỎI"
    "farming" -> "THU THẬP TÀI NGUYÊN"
    "village" -> "QUẢN LÝ LÀNG"
    "primitive" -> "NĂNG LỰC NỀN"
    else -> category.replace('_', ' ').uppercase()
}

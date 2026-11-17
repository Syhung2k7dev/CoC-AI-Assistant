package dev.syhung.cocassistant

import android.graphics.Bitmap
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun SkillLibraryPanel(
    registry: AgentSkillRegistry,
    apiKey: String,
    referenceImage: Bitmap?
) {
    val skillsState = remember { mutableStateOf(registry.all()) }
    val goalState = remember { mutableStateOf("Nghiên cứu và đề xuất một Skill mới dựa trên mục tiêu này.") }
    val statusState = remember { mutableStateOf("") }
    val busyState = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val skills = skillsState.value.groupBy { it.id }.values.mapNotNull { versions -> versions.maxByOrNull { it.version } }.sortedWith(compareBy<AgentSkill> { it.category }.thenBy { it.name })
    val categories = skills.map { it.category }.distinct()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("THƯ VIỆN KỸ NĂNG", color = Color(0xFF4DD8D0), fontSize = 10.sp)
        Text("${skills.size} kỹ năng · ${categories.size} nhóm", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Text(
            "Kỹ năng chưa nghiên cứu chỉ là mục tiêu. Kỹ năng đã nghiên cứu vẫn phải qua kiểm thử trước khi được phép thao tác trên thiết bị.",
            color = Color(0xFF9AA8C0), fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkillCountChip("Chưa nghiên cứu", skills.count { it.status == SkillLifecycle.RESEARCH_REQUIRED })
            SkillCountChip("Đã nghiên cứu", skills.count { it.status == SkillLifecycle.RESEARCHED })
            SkillCountChip("Đã xác minh", skills.count { it.status == SkillLifecycle.VERIFIED })
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B263A))
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI NGHIÊN CỨU KỸ NĂNG", color = Color(0xFF4DD8D0), fontSize = 10.sp)
                Text("Tạo kỹ năng mới từ mục tiêu", color = Color.White, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = goalState.value,
                    onValueChange = { goalState.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mục tiêu cần nghiên cứu") },
                    minLines = 3
                )
                Text(
                    if (apiKey.isBlank()) "Hãy nhập Gemini API Key trong Cài đặt trước khi nghiên cứu."
                    else if (referenceImage != null) "Ảnh tham chiếu đã chọn sẽ được gửi cùng yêu cầu."
                    else "Có thể chọn ảnh tham chiếu ở trang Thị giác trước khi nghiên cứu.",
                    color = Color(0xFF9AA8C0), fontSize = 10.sp
                )
                Button(
                    onClick = {
                        if (apiKey.isBlank()) {
                            statusState.value = "Chưa có Gemini API Key. Mở Cài đặt để nhập khóa."
                        } else {
                            busyState.value = true
                            statusState.value = "Gemini đang nghiên cứu và tạo bản nháp..."
                            scope.launch {
                                when (val result = GeminiSkillAuthoring.createDraft(
                                    apiKey, goalState.value, registry, referenceImage, WorkflowEngine.AVAILABLE_API_NAMES
                                )) {
                                    is SkillDraftOutcome.Created -> {
                                        skillsState.value = registry.all()
                                        statusState.value = "Đã lưu Draft: " + result.skill.name +
                                            if (result.missingDependencies.isEmpty()) "" else
                                                " · Thiếu dependency: " + result.missingDependencies.joinToString()
                                    }
                                    is SkillDraftOutcome.Failed -> statusState.value = result.reason
                                }
                                busyState.value = false
                            }
                        }
                    },
                    enabled = !busyState.value && goalState.value.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B7CFF))
                ) {
                    Text(if (busyState.value) "Đang nghiên cứu..." else "Nghiên cứu kỹ năng mới")
                }
                if (statusState.value.isNotBlank()) {
                    Text(statusState.value, color = Color(0xFF4DD8D0), fontSize = 11.sp)
                }
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
                            Button(
                                onClick = {
                                    busyState.value = true
                                    statusState.value = "Đang nghiên cứu quy trình và điều kiện của: ${skill.name}"
                                    scope.launch {
                                        when (val result = GeminiSkillAuthoring.researchExistingSkill(
                                            apiKey, skill.id, registry, WorkflowEngine.AVAILABLE_API_NAMES
                                        )) {
                                            is SkillDraftOutcome.Created -> {
                                                skillsState.value = registry.all()
                                                statusState.value = "Đã nghiên cứu và lưu phiên bản ${result.skill.version}: ${result.skill.name}. Chưa được phép thực thi."
                                            }
                                            is SkillDraftOutcome.Failed -> statusState.value = result.reason
                                        }
                                        busyState.value = false
                                    }
                                },
                                enabled = !busyState.value && apiKey.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B7CFF))
                            ) { Text(if (busyState.value) "Đang nghiên cứu…" else "Nghiên cứu kỹ năng này") }
                        }
                        if (skill.status == SkillLifecycle.RESEARCHED) {
                            Text("Đã có quy trình và CASL Draft. Cần kiểm thử đầy đủ trước khi xác minh và thực thi.", color = Color(0xFFFFC66D), fontSize = 10.sp)
                        }
                    }
                }
            }
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

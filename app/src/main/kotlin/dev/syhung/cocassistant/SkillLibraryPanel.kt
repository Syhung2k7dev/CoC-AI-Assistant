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
    val skills = skillsState.value.sortedWith(compareBy<AgentSkill> { it.category }.thenBy { it.name })
    val categories = skills.map { it.category }.distinct()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("THƯ VIỆN SKILL", color = Color(0xFF4DD8D0), fontSize = 10.sp)
        Text("${skills.size} năng lực khai báo · ${categories.size} nhóm", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Text(
            "Các mục dưới đây là năng lực khai báo. Chúng chỉ trở thành khả năng thực thi sau khi có workflow, kiểm thử và runtime tương ứng.",
            color = Color(0xFF9AA8C0), fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkillCountChip("Draft", skills.count { it.status == SkillLifecycle.DRAFT })
            SkillCountChip("Verified", skills.count { it.status == SkillLifecycle.VERIFIED })
            SkillCountChip("Disabled", skills.count { it.status == SkillLifecycle.DISABLED })
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B263A))
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI SKILL RESEARCHER", color = Color(0xFF4DD8D0), fontSize = 10.sp)
                Text("Tạo Skill Draft từ yêu cầu và ảnh tham chiếu", color = Color.White, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = goalState.value,
                    onValueChange = { goalState.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mục tiêu nghiên cứu") },
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
                    Text(if (busyState.value) "Đang nghiên cứu..." else "Nghiên cứu và tạo Skill Draft")
                }
                if (statusState.value.isNotBlank()) {
                    Text(statusState.value, color = Color(0xFF4DD8D0), fontSize = 11.sp)
                }
            }
        }
        categories.forEach { category ->
            Text(category.replace('_', ' ').uppercase(), color = Color(0xFF4DD8D0), fontSize = 10.sp)
            skills.filter { it.category == category }.forEach { skill ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2B))
                ) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(skill.name, color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(skill.status.name, color = Color(0xFFFFC66D), fontSize = 9.sp)
                        }
                        Text(skill.description, color = Color(0xFF9AA8C0), fontSize = 11.sp)
                        Text("${skill.level.name} · ID: ${skill.id} · v${skill.version}", color = Color(0xFF71819D), fontSize = 9.sp)
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

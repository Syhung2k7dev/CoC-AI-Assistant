package dev.syhung.cocassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
fun WorkflowStudioPanel(
    registry: CaslWorkflowRegistry,
    skills: AgentSkillRegistry,
    apiKey: String
) {
    val goal = remember { mutableStateOf("Tạo Workflow mới theo mục tiêu của tôi.") }
    val workflows = remember { mutableStateOf(registry.all()) }
    val status = remember { mutableStateOf("") }
    val busy = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("WORKFLOW STUDIO", color = Color(0xFF4DD8D0), fontSize = 10.sp)
        Text("Workflow là công việc chính", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Text(
            "Gemini tạo hoặc sửa Workflow bằng CASL. Mỗi bản mới được lưu riêng ở Draft; Workflow chỉ chạy sau khi được kiểm tra và xác minh.",
            color = Color(0xFF9AA8C0), fontSize = 12.sp
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B263A))
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = goal.value,
                    onValueChange = { goal.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mục tiêu Workflow") },
                    minLines = 3
                )
                Button(
                    onClick = {
                        if (apiKey.isBlank()) {
                            status.value = "Chưa có Gemini API Key. Mở Cài đặt để nhập khóa."
                        } else {
                            busy.value = true
                            status.value = "Gemini đang thiết kế Workflow..."
                            scope.launch {
                                when (val result = GeminiWorkflowAuthoring.createDraft(
                                    apiKey, goal.value, registry, skills
                                )) {
                                    is WorkflowDraftOutcome.Created -> {
                                        workflows.value = registry.all()
                                        status.value = "Đã lưu Draft " + result.workflow.optString("id") +
                                            if (result.missingSkills.isEmpty()) "" else
                                                " · Thiếu Skill: " + result.missingSkills.joinToString()
                                    }
                                    is WorkflowDraftOutcome.Failed -> status.value = result.reason
                                }
                                busy.value = false
                            }
                        }
                    },
                    enabled = !busy.value && goal.value.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B7CFF))
                ) {
                    Text(if (busy.value) "Đang thiết kế..." else "Gemini tạo Workflow Draft")
                }
                if (status.value.isNotBlank()) {
                    Text(status.value, color = Color(0xFF4DD8D0), fontSize = 11.sp)
                }
            }
        }
        Text("CÁC PHIÊN BẢN WORKFLOW", color = Color(0xFF4DD8D0), fontSize = 10.sp)
        if (workflows.value.isEmpty()) {
            Text("Chưa có Workflow nào được lưu.", color = Color(0xFF9AA8C0), fontSize = 11.sp)
        } else {
            workflows.value.sortedByDescending { it.optLong("updatedAt") }.forEach { workflow ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2B))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(workflow.optString("id"), color = Color.White, style = MaterialTheme.typography.titleSmall)
                            Text("v" + workflow.optInt("version"), color = Color(0xFFFFC66D), fontSize = 10.sp)
                            Text(workflow.optString("lifecycle", "DRAFT"), color = Color(0xFF9AA8C0), fontSize = 10.sp)
                        }
                        Text("CASL schema " + workflow.optInt("schemaVersion") +
                            " · " + (workflow.optJSONArray("steps")?.length() ?: 0) + " bước",
                            color = Color(0xFF9AA8C0), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

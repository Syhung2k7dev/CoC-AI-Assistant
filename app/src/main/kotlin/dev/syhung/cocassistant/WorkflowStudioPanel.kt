package dev.syhung.cocassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
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
    val selectedWorkflow = remember { mutableStateOf<Pair<String, Int>?>(null) }
    val busy = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val showRunConfirm = remember { mutableStateOf(false) }
    val runStatus = remember { mutableStateOf("") }
    val activeSchedule = remember { mutableStateOf("") }
    val selectedRecord = selectedWorkflow.value?.let { pair -> workflows.value.firstOrNull { it.optString("id") == pair.first && it.optInt("version") == pair.second } }

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
                                    apiKey, goal.value, registry, skills, WorkflowEngine.AVAILABLE_API_NAMES
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
                if (selectedWorkflow.value != null) {
                    val selected = selectedWorkflow.value!!
                    if (selectedRecord?.optString("lifecycle") == WorkflowLifecycle.VERIFIED.name) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showRunConfirm.value = true }, enabled = !busy.value, modifier = Modifier.weight(1f)) { Text("Chạy ngay") }
                            Button(onClick = {
                                activeSchedule.value = WorkflowScheduler.scheduleOnce(context, selected.first, selected.second, System.currentTimeMillis() + 300_000L)
                                runStatus.value = "Đã lên lịch chạy một lần sau 5 phút."
                            }, modifier = Modifier.weight(1f)) { Text("Sau 5 phút") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                activeSchedule.value = WorkflowScheduler.schedulePeriodic(context, selected.first, selected.second, 15)
                                runStatus.value = "Đã lên lịch lặp 15 phút."
                            }, modifier = Modifier.weight(1f)) { Text("Lặp 15 phút") }
                            Button(onClick = {
                                if (activeSchedule.value.isNotBlank()) WorkflowScheduler.cancel(context, activeSchedule.value)
                                activeSchedule.value = ""
                                runStatus.value = "Đã hủy lịch."
                            }, enabled = activeSchedule.value.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Hủy lịch") }
                        }
                    } else if (selectedRecord?.optString("lifecycle") in listOf(WorkflowLifecycle.DRAFT.name, WorkflowLifecycle.TESTING.name)) {
                        Button(
                            onClick = {
                                busy.value = true
                                runStatus.value = "Đang chạy test cases với API mock..."
                                scope.launch {
                                    try {
                                        val report = WorkflowTestRunner(context).runAndVerify(selected.first, selected.second)
                                        workflows.value = registry.all()
                                        runStatus.value = "Kiểm thử: ${report.passed}/${report.total}. " +
                                            if (report.verified) "Workflow đã VERIFIED." else "Chưa đủ điều kiện xác minh."
                                    } catch (error: Exception) {
                                        runStatus.value = "Kiểm thử thất bại: ${error.message ?: "lỗi không xác định"}"
                                    } finally { busy.value = false }
                                }
                            },
                            enabled = !busy.value,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF267D75))
                        ) { Text("Kiểm thử & xác minh bằng API giả lập") }
                        Text("Test runner không chạm màn hình hoặc gọi API thiết bị thật; chỉ khi toàn bộ test và Skill dependency hợp lệ mới chuyển sang VERIFIED.", color = Color(0xFF9AA8C0), fontSize = 10.sp)
                    } else {
                        Text("Workflow không ở trạng thái có thể chạy hoặc kiểm thử.", color = Color(0xFFFFC66D), fontSize = 10.sp)
                    }
                    if (runStatus.value.isNotBlank()) Text(runStatus.value, color = Color(0xFF4DD8D0), fontSize = 11.sp)
                    Text("Đang chọn " + selected.first + " · v" + selected.second, color = Color(0xFFFFC66D), fontSize = 11.sp)
                    Button(
                        onClick = {
                            if (apiKey.isBlank()) {
                                status.value = "Chưa có Gemini API Key."
                            } else {
                                busy.value = true
                                status.value = "Gemini đang sửa Workflow và tạo phiên bản mới..."
                                scope.launch {
                                    when (val result = GeminiWorkflowAuthoring.reviseDraft(
                                        apiKey, selected.first, selected.second, goal.value, registry, skills, WorkflowEngine.AVAILABLE_API_NAMES
                                    )) {
                                        is WorkflowDraftOutcome.Created -> {
                                            workflows.value = registry.all()
                                            status.value = "Đã tạo phiên bản " + result.workflow.optInt("version") +
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF267D75))
                    ) {
                        Text("Gemini sửa Workflow thành phiên bản mới")
                    }
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
                        Button(
                            onClick = {
                                selectedWorkflow.value = workflow.optString("id") to workflow.optInt("version")
                                goal.value = "Cải tiến Workflow này theo yêu cầu mới, giữ nguyên phần đang hoạt động."
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26354D))
                        ) {
                            Text("Chọn để AI sửa", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
    if (showRunConfirm.value && selectedWorkflow.value != null) {
        val selected = selectedWorkflow.value!!
        AlertDialog(
            onDismissRequest = { showRunConfirm.value = false },
            title = { Text("Xác nhận chạy Workflow") },
            text = { Text("Chạy phiên bản VERIFIED ${selected.first} · v${selected.second}?") },
            confirmButton = {
                TextButton(onClick = {
                    showRunConfirm.value = false
                    busy.value = true
                    runStatus.value = "Workflow đang chạy..."
                    scope.launch {
                        try {
                            val result = WorkflowEngine(context).execute(selected.first, selected.second, approved = true)
                            runStatus.value = "Hoàn tất · ${result.executedSteps} bước."
                        } catch (error: Exception) {
                            runStatus.value = "Workflow thất bại: ${error.message ?: "lỗi không xác định"}"
                        } finally { busy.value = false }
                    }
                }) { Text("Xác nhận chạy") }
            },
            dismissButton = { TextButton(onClick = { showRunConfirm.value = false }) { Text("Hủy") } }
        )
    }
}

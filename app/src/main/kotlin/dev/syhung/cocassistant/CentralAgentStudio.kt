package dev.syhung.cocassistant

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private val AgentBg = Color(0xFF141B2B)
private val AgentText = Color(0xFFF4F6FC)
private val AgentMuted = Color(0xFF9AA8C0)
private val AgentAccent = Color(0xFF8B7CFF)
private val AgentCyan = Color(0xFF4DD8D0)

private const val GAME_PACKAGE = "com.supercell.clashofclans"
private const val DEFAULT_AGENT_RULES = """
Bạn là CoC Agent, trợ lý điều phối tự động hóa Clash of Clans.
Luôn phân biệt dữ liệu đã quan sát, suy luận và điều chưa biết.
Không bịa tọa độ, trạng thái màn hình, kết quả thao tác hoặc khả năng Skill.
Ưu tiên quan sát cục bộ, chỉ gửi ảnh lên AI khi người dùng bật lựa chọn chia sẻ.
Không tự ý sửa bộ nguyên tắc này. Mọi thay đổi phải được người dùng xem và xác nhận riêng.
Chỉ đề xuất thao tác khi có đủ bằng chứng; hành động thiết bị phải đi qua Skill đã kiểm chứng và quyền Android.
Sau mỗi hành động cần quan sát lại và xác minh kết quả; nếu không chắc chắn thì dừng.
Trả lời bằng tiếng Việt, ngắn gọn, nêu rõ trạng thái và bước tiếp theo.
"""

private data class AgentChatMessage(val role: String, val text: String)

@Composable
fun CentralAgentStudio(
    skillRegistry: AgentSkillRegistry,
    apiKey: String,
    onStatus: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("coc_agent_preferences", Context.MODE_PRIVATE) }
    var goal by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var agentJob by remember { mutableStateOf<Job?>(null) }
    var shareScreenshot by remember { mutableStateOf(false) }
    var systemRules by remember { mutableStateOf(prefs.getString("system_rules", DEFAULT_AGENT_RULES) ?: DEFAULT_AGENT_RULES) }
    var editedRules by remember { mutableStateOf(systemRules) }
    var showRulesEditor by remember { mutableStateOf(false) }
    var showRulesApproval by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Đang chờ yêu cầu") }
    val messages = remember { mutableStateListOf<AgentChatMessage>().apply {
        add(AgentChatMessage("agent", "Tôi là AI điều phối trung tâm. Hãy mô tả mục tiêu; tôi sẽ quan sát CoC, phân tích bằng chứng và báo rõ việc nào đã làm, việc nào mới là đề xuất."))
    } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = AgentBg)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFF25365A), shape = RoundedCornerShape(12.dp)) {
                    androidx.compose.material3.Icon(
                        Icons.Default.AutoAwesome, contentDescription = null,
                        tint = AgentCyan, modifier = Modifier.padding(10.dp)
                    )
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("TRUNG TÂM AI", color = AgentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text("CoC Agent Studio", color = AgentText, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                    Text(status, color = AgentMuted, fontSize = 11.sp)
                }
                Text("Gemini", color = AgentCyan, fontSize = 11.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AgentStateChip("CADSS", "BỘ NHỚ")
                AgentStateChip("CASL", "ĐIỀU PHỐI")
                AgentStateChip("SKILL", "${skillRegistry.all().size} MỤC")
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bạn muốn Agent làm gì?", color = AgentText, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it.take(1600) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = { Text("Ví dụ: mở CoC, kiểm tra trạng thái làng và báo công trình nào cần chú ý…") }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = shareScreenshot, onCheckedChange = { shareScreenshot = it })
                    Column(Modifier.weight(1f)) {
                        Text("Cho phép gửi ảnh màn hình lên Gemini", color = AgentText, fontSize = 12.sp)
                        Text("Tắt: chỉ gửi phần văn bản OCR đã nhận diện cục bộ.", color = AgentMuted, fontSize = 10.sp)
                    }
                }
                Button(
                    onClick = {
                        if (goal.isBlank()) {
                            status = "Hãy nhập mục tiêu trước khi phân tích."
                            return@Button
                        }
                        if (apiKey.isBlank()) {
                            status = "Chưa có Gemini API Key. Hãy nhập khóa trong Cài đặt."
                            messages.add(AgentChatMessage("agent", status))
                            return@Button
                        }
                        if (!CoCAccessibilityService.isConnected()) {
                            status = "Chưa kết nối Trợ năng. Hãy bật dịch vụ trong Cài đặt Android."
                            messages.add(AgentChatMessage("agent", status))
                            return@Button
                        }
                        busy = true
                        messages.add(AgentChatMessage("user", goal))
                        status = "Đang mở Clash of Clans…"
                        scope.launch {
                            var capturedScreenshot: android.graphics.Bitmap? = null
                            try {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(GAME_PACKAGE)
                                    ?: error("Không tìm thấy Clash of Clans trên thiết bị.")
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(launchIntent)
                                delay(2200L)
                                status = "Đang tự chụp màn hình…"
                                val screenshot = suspendCancellableCoroutine<android.graphics.Bitmap?> { continuation ->
                                    val accepted = CoCAccessibilityService.captureScreen { bitmap ->
                                        if (continuation.isActive) continuation.resume(bitmap)
                                    }
                                    if (!accepted && continuation.isActive) continuation.resume(null)
                                } ?: error("Không chụp được màn hình. Kiểm tra quyền Trợ năng hoặc cửa sổ đang hiển thị.")
                                capturedScreenshot = screenshot
                                val ocrBitmap = screenshot.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                val recognizedText = suspendCancellableCoroutine<String?> { continuation ->
                                    LocalOcrProcessor.recognize(ocrBitmap) { result ->
                                        if (continuation.isActive) continuation.resume(result)
                                    }
                                }.orEmpty()
                                status = "Đang phân tích tình huống…"
                                val inventory = skillRegistry.all().joinToString("\n") {
                                    "${it.id} | ${it.name} | ${it.status.name} | ${it.description}"
                                }
                                val prompt = """
                                    $systemRules

                                    MỤC TIÊU NGƯỜI DÙNG:
                                    $goal

                                    VĂN BẢN OCR TỪ MÀN HÌNH HIỆN TẠI:
                                    ${recognizedText.take(5000).ifBlank { "(OCR không nhận diện được văn bản)" }}

                                    DANH MỤC SKILL:
                                    $inventory

                                    Hãy phân tích theo các mục: tình trạng quan sát được, suy luận có độ tin cậy,
                                    thông tin còn thiếu, Skill phù hợp và bước tiếp theo. Không được giả vờ đã click
                                    hoặc hoàn tất tác vụ. Chỉ đề xuất hành động; không trả về tọa độ để tự động click.
                                    """.trimIndent()
                                val result = GeminiClient.generate(apiKey, prompt, if (shareScreenshot) screenshot else null)
                                messages.add(AgentChatMessage("agent", result))
                                status = if (shareScreenshot) "Đã phân tích ảnh và OCR bằng Gemini." else "Đã phân tích OCR; ảnh vẫn ở trên thiết bị."
                                onStatus(status)
                            } catch (error: Exception) {
                                status = "Chưa hoàn tất: ${error.message ?: "lỗi không xác định"}"
                                messages.add(AgentChatMessage("agent", status))
                                onStatus(status)
                            } finally {
                                capturedScreenshot?.recycle()
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && goal.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AgentAccent)
                ) {
                    androidx.compose.material3.Icon(Icons.Default.Gamepad, contentDescription = null)
                    Spacer(Modifier.height(4.dp))
                    Text(if (busy) "Đang xử lý…" else "Phân tích CoC")
                }
                Text(
                    "Chạy Agent là xác nhận cho phép thực hiện tối đa 5 lần chạy Skill và một vòng xác minh cuối. Agent chỉ gọi Skill VERIFIED, chụp lại màn hình sau mỗi Skill và tự dừng khi không xác minh được.",
                    color = AgentMuted, fontSize = 10.sp
                )
                Button(
                    onClick = {
                        if (goal.isBlank()) {
                            status = "Hãy nhập mục tiêu trước khi chạy Agent."
                            return@Button
                        }
                        if (apiKey.isBlank()) {
                            status = "Chưa có Gemini API Key. Hãy nhập khóa trong Cài đặt."
                            messages.add(AgentChatMessage("agent", status))
                            return@Button
                        }
                        if (!CoCAccessibilityService.isConnected()) {
                            status = "Chưa kết nối Trợ năng. Hãy bật dịch vụ trong Cài đặt Android."
                            messages.add(AgentChatMessage("agent", status))
                            return@Button
                        }
                        busy = true
                        messages.add(AgentChatMessage("user", "Chạy Agent: $goal"))
                        status = "Đang khởi động vòng lặp Agent…"
                        agentJob = scope.launch {
                            try {
                                val outcome = CentralAgentLoop.run(
                                    context = context,
                                    apiKey = apiKey,
                                    goal = goal,
                                    registry = skillRegistry,
                                    shareScreenshot = shareScreenshot,
                                    onStatus = { stage ->
                                        status = stage
                                        onStatus(stage)
                                    }
                                )
                                messages.add(AgentChatMessage("agent", outcome))
                                status = outcome.lineSequence().firstOrNull().orEmpty().take(180)
                                onStatus(status)
                            } catch (error: Exception) {
                                status = "Agent đã dừng an toàn: ${error.message ?: "lỗi không xác định"}"
                                messages.add(AgentChatMessage("agent", status))
                                onStatus(status)
                            } finally {
                                busy = false
                                agentJob = null
                            }
                        }
                    },
                    enabled = !busy && goal.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF167C78))
                ) {
                    androidx.compose.material3.Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    Text(if (busy) "Agent đang chạy…" else "Chạy Agent tự động · tối đa 5 Skill")
                }
                if (agentJob != null) {
                    OutlinedButton(
                        onClick = {
                            CoCAccessibilityService.stop()
                            agentJob?.cancel()
                            status = "Đang dừng Agent theo yêu cầu…"
                            messages.add(AgentChatMessage("agent", status))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Dừng Agent ngay") }
                }
                OutlinedButton(
                    onClick = {
                        if (goal.isBlank() || apiKey.isBlank()) {
                            status = if (goal.isBlank()) "Hãy nhập nội dung chat." else "Hãy nhập Gemini API Key trong Cài đặt."
                        } else {
                            busy = true
                            messages.add(AgentChatMessage("user", goal))
                            scope.launch {
                                try {
                                    val inventory = skillRegistry.all().joinToString("\n") {
                                        "${it.id} | ${it.name} | ${it.status.name}"
                                    }
                                    val response = GeminiClient.generate(
                                        apiKey,
                                        "$systemRules\n\nNgữ cảnh Skill hiện tại:\n$inventory\n\nYêu cầu người dùng: $goal"
                                    )
                                    messages.add(AgentChatMessage("agent", response))
                                    status = "AI đã phản hồi."
                                } catch (error: Exception) {
                                    status = "Lỗi AI: ${error.message}"
                                    messages.add(AgentChatMessage("agent", status))
                                } finally { busy = false }
                            }
                        }
                    },
                    enabled = !busy && goal.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    Text("Chat với AI")
                }
            }

            if (messages.isNotEmpty()) {
                Text("HỘI THOẠI", color = AgentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                messages.takeLast(8).forEach { message ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .background(if (message.role == "user") Color(0xFF202B43) else Color(0xFF101725), RoundedCornerShape(12.dp))
                            .padding(11.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(if (message.role == "user") "BẠN" else "COC AGENT", color = AgentCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(message.text, color = AgentText, fontSize = 12.sp)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(Icons.Default.Lock, contentDescription = null, tint = AgentMuted)
                TextButton(onClick = {
                    editedRules = systemRules
                    showRulesEditor = true
                }) { Text("Xem / sửa nguyên tắc Agent", color = AgentCyan) }
            }
            Text(
                "Giới hạn hiện tại: Agent tự mở game, chụp màn hình và phân tích. Thao tác chạm chỉ được chạy qua Workflow/Skill đã kiểm chứng; chưa tự click từ câu trả lời Gemini.",
                color = AgentMuted, fontSize = 10.sp
            )
        }
    }

    if (showRulesEditor) {
        AlertDialog(
            onDismissRequest = { showRulesEditor = false },
            title = { Text("Nguyên tắc hệ thống của Agent") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Thay đổi sẽ chưa có hiệu lực cho đến khi bạn xem và xác nhận ở bước tiếp theo.", fontSize = 12.sp)
                    OutlinedTextField(
                        value = editedRules,
                        onValueChange = { editedRules = it.take(8000) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 8
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showRulesEditor = false; showRulesApproval = true }) {
                    Text("Xem thay đổi")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRulesEditor = false }) { Text("Hủy") }
            }
        )
    }
    if (showRulesApproval) {
        AlertDialog(
            onDismissRequest = { showRulesApproval = false },
            title = { Text("Xác nhận thay đổi nguyên tắc") },
            text = { Text("Bạn xác nhận áp dụng nội dung nguyên tắc mới này cho các phiên Agent tiếp theo trên thiết bị?") },
            confirmButton = {
                TextButton(onClick = {
                    systemRules = editedRules
                    prefs.edit().putString("system_rules", systemRules).apply()
                    showRulesApproval = false
                    status = "Đã cập nhật nguyên tắc theo xác nhận của bạn."
                }) { Text("Đồng ý áp dụng") }
            },
            dismissButton = {
                TextButton(onClick = { showRulesApproval = false }) { Text("Không áp dụng") }
            }
        )
    }
}

@Composable
private fun AgentStateChip(name: String, value: String) {
    Surface(color = Color(0xFF26344B), shape = RoundedCornerShape(30.dp)) {
        Text("$name · $value", modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = AgentText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

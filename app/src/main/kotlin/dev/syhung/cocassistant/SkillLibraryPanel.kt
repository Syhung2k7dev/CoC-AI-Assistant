package dev.syhung.cocassistant

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun SkillLibraryPanel(
    registry: AgentSkillRegistry,
    apiKey: String,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val debugEvents = ResearchDebugLog.events.collectAsState().value
    val skillsState = remember { mutableStateOf(registry.all()) }
    val goalState = remember { mutableStateOf("") }
    val statusState = remember { mutableStateOf("") }
    val activeJob = remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val busy = activeJob.value != null
    val projectionReady = remember { mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || ScreenCaptureService.isReady()) }
    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenCaptureService.start(context, result.resultCode, data)
            scope.launch {
                repeat(20) {
                    delay(250L)
                    if (ScreenCaptureService.isReady()) {
                        projectionReady.value = true
                        statusState.value = "Đã cấp quyền chụp màn hình. Có thể bắt đầu nghiên cứu."
                        return@launch
                    }
                }
                statusState.value = "Android chưa khởi tạo được phiên chụp màn hình. Hãy thử cấp quyền lại."
            }
        } else {
            statusState.value = "Bạn chưa cấp quyền chia sẻ màn hình; Agent chưa thể quan sát CoC trên Android 10."
        }
    }
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
        if (!projectionReady.value) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2D1B))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("ANDROID 10 · CẦN QUYỀN CHỤP MÀN HÌNH", color = Color(0xFFFFC66D), fontSize = 11.sp)
                    Text("Android 10 chưa hỗ trợ Accessibility screenshot. Cấp quyền chia sẻ màn hình để Agent quan sát CoC; thao tác vẫn đi qua Accessibility.", color = Color.White, fontSize = 11.sp)
                    Button(onClick = {
                        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        projectionLauncher.launch(manager.createScreenCaptureIntent())
                    }, modifier = Modifier.fillMaxWidth()) { Text("Cấp quyền chụp màn hình") }
                }
            }
        }
        if (projectionReady.value && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            OutlinedButton(
                onClick = {
                    ScreenCaptureService.stop(context)
                    projectionReady.value = false
                    statusState.value = "Đã dừng chia sẻ màn hình."
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Dừng chia sẻ màn hình") }
        }
        ResearchDebugConsole(
            events = debugEvents,
            onCopy = { clipboard.setText(AnnotatedString(ResearchDebugLog.exportText())) },
            onClear = { ResearchDebugLog.clearAll(context) },
            clearEnabled = !busy
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
                    enabled = !busy && pendingCount > 0 && apiKey.isNotBlank() && projectionReady.value,
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
                    enabled = !busy && goalState.value.isNotBlank() && apiKey.isNotBlank() && projectionReady.value,
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
                                enabled = !busy && apiKey.isNotBlank() && projectionReady.value,
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


@Composable
private fun ResearchDebugConsole(
    events: List<ResearchDebugLog.Event>,
    onCopy: () -> Unit,
    onClear: () -> Int,
    clearEnabled: Boolean
) {
    val showClearConfirmation = remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101827))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("RESEARCH DEBUG CONSOLE", color = Color(0xFF4DD8D0), fontSize = 10.sp)
                    Text("${events.size} sự kiện · lưu cục bộ trên thiết bị", color = Color.White, fontSize = 12.sp)
                }
                OutlinedButton(onClick = onCopy) { Text("Sao chép log") }
                OutlinedButton(onClick = { showClearConfirmation.value = true }, enabled = clearEnabled) { Text("Xoá log") }
            }
            if (events.isEmpty()) {
                Text("Chưa có log. Bắt đầu một phiên nghiên cứu để theo dõi từng bước.", color = Color(0xFF9AA8C0), fontSize = 11.sp)
            } else {
                var shownScreenshotPreviews = 0
                events.takeLast(60).asReversed().forEach { event ->
                    Column(
                        Modifier.fillMaxWidth()
                            .background(Color(0xFF1B263A), RoundedCornerShape(10.dp))
                            .padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date(event.time))} · ${event.stage}",
                            color = Color(0xFF4DD8D0), fontSize = 9.sp
                        )
                        Text(event.message, color = Color.White, fontSize = 11.sp)
                        if (event.details.isNotBlank()) {
                            Text(event.details, color = Color(0xFFB5C2D8), fontSize = 9.sp)
                        }
                        event.imageName?.takeIf { shownScreenshotPreviews < 4 }?.let {
                            ResearchDebugScreenshot(it)
                            shownScreenshotPreviews++
                        }
                    }
                }
            }
            Text(
                "Log không ghi API key hoặc nội dung header xác thực. Có thể xem ảnh đã chụp, kích thước payload, HTTP status, latency, phản hồi AI và hành động.",
                color = Color(0xFF71819D), fontSize = 9.sp
            )
        }
    }
    if (showClearConfirmation.value) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation.value = false },
            title = { Text("Xoá toàn bộ nhật ký nghiên cứu?") },
            text = { Text("Xoá các tệp JSONL trong bộ nhớ riêng của ứng dụng và làm trống bảng log. Ảnh bằng chứng trong Vision Memory không bị xoá.") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    showClearConfirmation.value = false
                }) { Text("Xoá vĩnh viễn") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation.value = false }) { Text("Huỷ") }
            }
        )
    }
}

@Composable
private fun ResearchDebugScreenshot(fileName: String) {
    val context = LocalContext.current
    val bitmap = remember(fileName) {
        val file = VisionMemory.resolve(context, fileName)
        if (file == null) null else {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(file.absolutePath, options)
        }
    }
    if (bitmap != null && !bitmap.isRecycled) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Ảnh màn hình CoC đã chụp: $fileName",
            modifier = Modifier.fillMaxWidth().height(170.dp),
            contentScale = ContentScale.Fit
        )
    } else {
        Text("Không đọc được ảnh: $fileName", color = Color(0xFFFFC66D), fontSize = 9.sp)
    }
}

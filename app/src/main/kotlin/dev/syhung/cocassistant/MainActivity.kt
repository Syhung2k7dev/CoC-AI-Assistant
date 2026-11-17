package dev.syhung.cocassistant

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val Bg = Color(0xFF090D18)
private val Panel = Color(0xFF141B2B)
private val Panel2 = Color(0xFF1B263A)
private val Accent = Color(0xFF8B7CFF)
private val Cyan = Color(0xFF4DD8D0)
private val Muted = Color(0xFF9AA8C0)
private val White = Color(0xFFF4F6FC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CoCAssistantApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoCAssistantApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { CadssStore(context) }
    val visionLearning = remember { VisionLearningManager(context) }
    val skillRegistry = remember { AgentSkillRegistry(context).also { BuiltinSkillCatalog.install(it) } }
    val workflowRegistry = remember { CaslWorkflowRegistry(context) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var note by rememberSaveable { mutableStateOf(store.getText("note", "")) }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("Phân tích hình ảnh và tóm tắt những thông tin quan trọng.") }
    var aiResult by remember { mutableStateOf("") }
    var visionResult by remember { mutableStateOf("") }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Sẵn sàng") }
    var timerSeconds by rememberSaveable { mutableIntStateOf(0) }
    var timerRunning by rememberSaveable { mutableStateOf(false) }
    var showQuickPanel by remember { mutableStateOf(false) }
    var visionLabel by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(timerRunning, timerSeconds) {
        while (timerRunning && timerSeconds > 0) {
            delay(1000L)
            timerSeconds -= 1
        }
        if (timerSeconds == 0) timerRunning = false
    }
    var templateCount by remember { mutableIntStateOf(visionLearning.listTemplates().size) }
    var history by remember { mutableStateOf(visionLearning.listTemplates(includeReviewRequired = true) + store.getRecords("vision_memory")) }
    var pendingCandidates by remember { mutableStateOf(visionLearning.listCandidates()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) { decodeImage(context, uri) }
                    if (bitmap != null) {
                        selectedBitmap = bitmap
                        val outcome = withContext(Dispatchers.Default) {
                            visionLearning.observe(bitmap, suggestedLabel = visionLabel)
                        }
                        history = visionLearning.listTemplates(includeReviewRequired = true) + store.getRecords("vision_memory")
                        pendingCandidates = visionLearning.listCandidates()
                        templateCount = visionLearning.listTemplates().size
                        visionResult = when (outcome) {
                            is VisionObservationResult.Known ->
                                "Đã nhận diện template VERIFIED: ${outcome.template.optString("label")}\nKhoảng cách pHash: ${outcome.distance}/64"
                            is VisionObservationResult.NeedsReview ->
                                if (outcome.distance == Int.MAX_VALUE) {
                                    "Đã tạo candidate mới. Candidate phải được duyệt trước khi trở thành template hoạt động.\npHash: ${outcome.candidate.optString("hash")}"
                                } else {
                                    "Ảnh gần với candidate đang chờ duyệt (${outcome.distance}/64). Đã tăng số lần quan sát."
                                }
                        }
                        status = "Vision Memory cục bộ đang hoạt động"
                    } else visionResult = "Không thể đọc hình ảnh đã chọn."
                } catch (e: Exception) {
                    visionResult = "Lỗi xử lý hình ảnh: ${e.message ?: "không xác định"}"
                } finally { busy = false }
            }
        }
    }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runCatching { BackupManager.export(context, uri, store) }
            .onSuccess { status = "Đã sao lưu dữ liệu CADSS" }
            .onFailure { status = "Sao lưu backup thất bại: ${it.message}" }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { BackupManager.restore(context, uri, store) }
            .onSuccess {
                note = store.getText("note", "")
                history = visionLearning.listTemplates(includeReviewRequired = true) + store.getRecords("vision_memory")
                pendingCandidates = visionLearning.listCandidates()
                templateCount = visionLearning.listTemplates().size
                status = "Đã khôi phục dữ liệu CADSS"
            }
            .onFailure { status = "Khôi phục thất bại: ${it.message}" }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, secondary = Cyan, background = Bg, surface = Panel,
            onPrimary = White, onSecondary = Bg, onBackground = White, onSurface = White
        )
    ) {
        Scaffold(
            containerColor = Bg,
            floatingActionButton = {
                FloatingActionButton(onClick = { showQuickPanel = true }, containerColor = Accent, contentColor = White) {
                    Icon(Icons.Default.Dashboard, contentDescription = "Bảng điều khiển nhanh")
                }
            },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("CoC AI", fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, color = White)
                            Text("TRUNG TÂM TRÍ TUỆ CÁ NHÂN", fontSize = 9.sp, letterSpacing = 1.8.sp, color = Muted)
                        }
                    },
                    actions = {
                        Surface(color = Color(0xFF17352F), shape = RoundedCornerShape(30.dp)) {
                            Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).background(Cyan, CircleShape))
                                Spacer(Modifier.width(7.dp))
                                Text("TRÊN THIẾT BỊ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Cyan)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Panel, contentColor = White) {
                    val items = listOf(
                        "Tổng quan" to Icons.Default.Dashboard,
                        "Thị giác" to Icons.Default.Visibility,
                        "CASL" to Icons.Default.AccountTree,
                        "Skills" to Icons.Default.AutoAwesome,
                        "Lịch" to Icons.Default.Schedule,
                        "Cài đặt" to Icons.Default.Settings
                    )
                    items.forEachIndexed { index, pair ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(pair.second, contentDescription = pair.first) },
                            label = { Text(pair.first, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = White, selectedTextColor = White,
                                indicatorColor = Accent.copy(alpha = .28f),
                                unselectedIconColor = Muted, unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        item {
                            Box(Modifier.fillMaxWidth().background(
                                Brush.linearGradient(listOf(Color(0xFF292B59), Color(0xFF172D43))),
                                RoundedCornerShape(24.dp)
                            ).padding(20.dp)) {
                                Column {
                                    Text("KHÔNG GIAN LÀM VIỆC", color = Cyan, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(10.dp))
                                    Text("Xin chào!", color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                    Text("Mọi công cụ AI và dữ liệu cá nhân trong một nơi.", color = Color(0xFFCFD7E8), fontSize = 13.sp)
                                    Spacer(Modifier.height(18.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        StatusChip("CADSS", "SẴN SÀNG")
                                        StatusChip("CASL", "SẴN SÀNG")
                                        StatusChip("VISION", if (templateCount > 0) "HOẠT ĐỘNG" else "CHỜ")
                                    }
                                }
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MetricCard("Mẫu hình ảnh", templateCount.toString(), "mẫu đã lưu", Icons.Default.Memory, Modifier.weight(1f))
                                MetricCard("Phiên bản", "1.0", "Phiên bản CADSS", Icons.Default.Verified, Modifier.weight(1f))
                            }
                        }
                        item { SectionHeading("TRUNG TÂM ĐIỀU KHIỂN", "Lối tắt nhanh") }
                        item { PanelCard { AutomationControlPanel(onStatus = { status = it }) } }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                QuickCard("Phân tích hình ảnh", "OpenCV cục bộ", Icons.Default.ImageSearch, Modifier.weight(1f)) { selectedTab = 1 }
                                QuickCard("Quy tắc tự động", "Ngôn ngữ quy tắc JSON CASL", Icons.Default.AccountTree, Modifier.weight(1f)) { selectedTab = 2 }
                            }
                        }
                        item {
                            PanelCard {
                                SectionHeading("GHI CHÚ CÁ NHÂN", "Lưu trên thiết bị")
                                OutlinedTextField(
                                    value = note, onValueChange = { note = it },
                                    modifier = Modifier.fillMaxWidth(), minLines = 3,
                                    placeholder = { Text("Ghi lại mục tiêu, ý tưởng hoặc tiến độ...", color = Muted) },
                                    colors = darkFieldColors()
                                )
                                Button(
                                    onClick = { store.putText("note", note); status = "Đã lưu ghi chú" },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                                ) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Lưu ghi chú") }
                            }
                        }
                        item { PanelCard { Text("TÌNH TRẠNG HỆ THỐNG", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); Text(status, color = Cyan, fontWeight = FontWeight.SemiBold); Text("Dữ liệu lưu trên thiết bị. AI chỉ kết nối khi bạn chủ động gửi yêu cầu.", color = Muted, fontSize = 12.sp) } }
                    }
                    1 -> {
                        item { PageIntro("Không gian thị giác", "Phân tích hình ảnh cục bộ và lưu dấu vân tay thị giác.") }
                        item {
                            PanelCard {
                                Icon(Icons.Default.AutoAwesome, null, tint = Cyan, modifier = Modifier.size(30.dp))
                                Text("Bộ máy xử lý ảnh OpenCV", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                Text("Chọn hình ảnh từ thiết bị. Ứng dụng tạo pHash cục bộ; mẫu mới sẽ ở trạng thái chờ duyệt, không tự động trở thành template hoạt động.", color = Muted, fontSize = 13.sp)
                                OutlinedTextField(
                                    value = visionLabel,
                                    onValueChange = { visionLabel = it.take(120) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Nhãn mẫu (tùy chọn)") },
                                    singleLine = true,
                                    colors = darkFieldColors()
                                )
                                Button(onClick = { picker.launch("image/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                                    Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "Đang xử lý..." else "Chọn hình ảnh để phân tích")
                                }
                                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan)
                                if (visionResult.isNotBlank()) Text(visionResult, color = Cyan, fontSize = 12.sp)
                            }
                        }
                        item { SectionHeading("AUTO-LEARNING", "Candidate chờ xác nhận") }
                        if (pendingCandidates.isEmpty()) item { EmptyState("Không có candidate", "Ảnh mới lạ sẽ được lưu ở trạng thái chờ duyệt.") }
                        items(pendingCandidates.take(20)) { candidate ->
                            val candidateId = candidate.optString("id")
                            val suggested = candidate.optString("suggestedLabel")
                            PanelCard {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(suggested.ifBlank { "Candidate ${candidateId.take(8)}" }, color = White, fontWeight = FontWeight.SemiBold)
                                    Text("pHash: ${candidate.optString("hash")} · Số lần quan sát: ${candidate.optInt("sightings", 1)}", color = Muted, fontSize = 11.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                val label = suggested.ifBlank { "Mẫu ${candidateId.take(8)}" }
                                                runCatching { visionLearning.approve(candidateId, label) }
                                                    .onSuccess {
                                                        pendingCandidates = visionLearning.listCandidates()
                                                        history = visionLearning.listTemplates(includeReviewRequired = true) + store.getRecords("vision_memory")
                                                        templateCount = visionLearning.listTemplates().size
                                                        status = "Đã duyệt template: $label"
                                                    }
                                                    .onFailure { status = "Không thể duyệt template: ${it.message}" }
                                            },
                                            enabled = !busy,
                                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                                        ) { Text("Duyệt template") }
                                        TextButton(
                                            onClick = {
                                                if (visionLearning.reject(candidateId, "Người dùng từ chối")) {
                                                    pendingCandidates = visionLearning.listCandidates()
                                                    status = "Đã từ chối candidate"
                                                }
                                            },
                                            enabled = !busy
                                        ) { Text("Từ chối", color = Muted) }
                                    }
                                }
                            }
                        }
                        item { SectionHeading("BỘ NHỚ HÌNH ẢNH", "Template đã xác minh và lịch sử") }
                        if (history.isEmpty()) item { EmptyState("Chưa có dữ liệu", "Template được duyệt và ảnh đã phân tích sẽ xuất hiện tại đây.") }
                        items(history.take(30)) { obj ->
                            PanelCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Fingerprint, null, tint = Accent)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(obj.optString("label", "Vision record"), fontWeight = FontWeight.SemiBold)
                                        Text(obj.optString("hash", ""), color = Muted, fontSize = 11.sp)
                                    }
                                    Text(obj.optString("status", "CỤC BỘ"), color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    2 -> {
                        item { PageIntro("Trình quản lý quy tắc CASL", "Tạo và kiểm tra luồng IF / ELSE bằng JSON DSL an toàn.") }
                        item {
                            PanelCard {
                                Text("Ví dụ quy tắc", color = Cyan, fontWeight = FontWeight.Bold)
                                Text("NẾU điều kiện đúng → THỰC HIỆN nhánh ĐÚNG → NGƯỢC LẠI thực hiện nhánh SAI", color = White, fontSize = 15.sp)
                                Text("CASL hỗ trợ all / any / not, so sánh kiểu dữ liệu, truy cập object/array, set/output, call API allow-list, gọi Skill theo version pin, IF/ELSE, repeat, wait, assert và return. Mã tùy ý, reflection và shell không được thực thi.", color = Muted, fontSize = 12.sp)
                                Button(onClick = {
                                    val rule = JSONObject()
                                        .put("condition", JSONObject().put("path", "vision.confidence").put("op", "greater_than").put("value", 0.8))
                                        .put("then", JSONObject().put("type", "save_note").put("value", "Ảnh đạt ngưỡng tin cậy"))
                                        .put("else", JSONObject().put("type", "notify").put("value", "Cần kiểm tra lại ảnh"))
                                    val result = CaslEngine.evaluate(rule, JSONObject().put("vision", JSONObject().put("confidence", 0.92)))
                                    aiResult = "Kết quả kiểm tra: ${result.branch}\nHành động: ${result.action}"
                                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Chạy thử quy tắc mẫu")
                                }
                                if (aiResult.isNotBlank()) Text(aiResult, color = Cyan, fontSize = 12.sp)
                            }
                        }
                        item {
                            PanelCard {
                                Text("Định dạng quy tắc", color = White, fontWeight = FontWeight.Bold)
                                Text("Điều kiện: đường dẫn dữ liệu, phép so sánh, giá trị\nNhánh ĐÚNG: loại hành động, nội dung\nNhánh SAI: loại hành động, nội dung", color = Muted, fontSize = 12.sp)
                                Text("Quy tắc được kiểm tra trước khi lưu. Không hỗ trợ mã tùy ý hoặc thực thi lệnh hệ thống.", color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                    3 -> {
                        item { PageIntro("Agent Workbench", "Tạo công việc bằng Workflow và mở rộng năng lực bằng Skill.") }
                        item { WorkflowStudioPanel(workflowRegistry, skillRegistry, apiKey) }
                        item { SkillLibraryPanel(skillRegistry, apiKey, selectedBitmap) }
                    }
                    4 -> {
                        item { PageIntro("Lịch tác vụ", "Theo dõi tác vụ định kỳ và chế độ tiết kiệm pin, tài nguyên.") }
                        item {
                            PanelCard {
                                Icon(Icons.Default.Bedtime, null, tint = Cyan, modifier = Modifier.size(30.dp))
                                Text("Bộ lập lịch Workflow", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                Text("Workflow đã VERIFIED có thể chạy một lần hoặc định kỳ bằng Android WorkManager. Hệ thống không giữ vòng lặp nền liên tục; Doze và chính sách pin có thể trì hoãn thời điểm chạy.", color = Muted, fontSize = 13.sp)
                                Button(onClick = { selectedTab = 3 }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                                    Icon(Icons.Default.AccountTree, null); Spacer(Modifier.width(8.dp)); Text("Mở Workflow Studio để lập lịch")
                                }
                                Text("Lịch chạy được cấu hình trên từng Workflow đã xác minh.", color = Cyan, fontSize = 12.sp)
                            }
                        }
                        item {
                            PanelCard {
                                Text("Đếm thời gian phiên", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(if (timerSeconds == 0) "00:00" else String.format("%02d:%02d", timerSeconds / 60, timerSeconds % 60), color = Cyan, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                                Text(if (timerRunning) "ĐANG CHẠY" else "ĐÃ DỪNG", color = Muted, fontSize = 10.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { timerSeconds = 30 * 60; timerRunning = true }, modifier = Modifier.weight(1f)) { Text("30 phút") }
                                    Button(onClick = { timerSeconds = 60 * 60; timerRunning = true }, modifier = Modifier.weight(1f)) { Text("60 phút") }
                                    OutlinedButton(onClick = { timerRunning = false; timerSeconds = 0 }, modifier = Modifier.weight(1f)) { Text("Đặt lại") }
                                }
                                Text("Đếm thời gian phiên thủ công; không can thiệp ứng dụng khác.", color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                    else -> {
                        item { PageIntro("Cài đặt", "Quản lý kết nối AI, dữ liệu và quyền riêng tư.") }
                        item {
                            PanelCard {
                                Text("Gemini 3.8 Flash", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text("Gemini có thể phân tích văn bản và hình ảnh đang chọn. Khóa API chỉ được dùng trong phiên hiện tại và không lưu vào CADSS.", color = Muted, fontSize = 12.sp)
                                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Khóa API Gemini") }, singleLine = true, colors = darkFieldColors())
                                OutlinedTextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nội dung cần phân tích") }, minLines = 3, colors = darkFieldColors())
                                Button(onClick = {
                                    if (apiKey.isBlank()) { aiResult = "Vui lòng nhập khóa API để kết nối Gemini."; return@Button }
                                    busy = true
                                    scope.launch {
                                        aiResult = try { GeminiClient.generate(apiKey, prompt, selectedBitmap) }
                                        catch (e: Exception) { "Gemini chưa phản hồi: ${e.message ?: "lỗi kết nối"}" }
                                        busy = false
                                    }
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                                    Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "Đang kết nối..." else if (selectedBitmap != null) "Phân tích hình ảnh + văn bản" else "Gửi yêu cầu đến Gemini")
                                }
                                if (aiResult.isNotBlank()) Text(aiResult, color = Cyan, fontSize = 12.sp)
                            }
                        }
                        item {
                            PanelCard {
                                Text("CADSS / CASL", fontWeight = FontWeight.Bold)
                                Text("Phiên bản cấu trúc dữ liệu: ${CadssStore.SCHEMA_VERSION}\nLưu trữ: JSON có phiên bản, ưu tiên dữ liệu cục bộ\nBộ nhớ hình ảnh: metadata + pHash\nSao lưu: xuất / nhập dữ liệu qua trình chọn tệp.", color = Muted, fontSize = 12.sp)
                            }
                        }
                        item {
                            PanelCard {
                                Text("Sao lưu / Khôi phục CADSS", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text("Sao lưu hoặc khôi phục dữ liệu CADSS cùng hình ảnh gốc, hình ảnh cắt và thông tin liên quan trong một tệp ZIP có phiên bản.", color = Muted, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { exportPicker.launch("coc-cadss-backup.zip") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                                        Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(6.dp)); Text("Sao lưu")
                                    }
                                    OutlinedButton(onClick = { importPicker.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(6.dp)); Text("Khôi phục")
                                    }
                                }
                            }
                        }
                        item {
                            PanelCard {
                                Text("Quyền riêng tư", fontWeight = FontWeight.Bold)
                                Text("Quyền Trợ năng chỉ được sử dụng sau khi người dùng chủ động bật trong Android Settings. Workflow phải được xác nhận trước khi chạy và có Pause/Resume/Stop. Ảnh màn hình chỉ được chụp theo yêu cầu; OCR và phân tích ảnh xử lý cục bộ theo mặc định.", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        if (showQuickPanel) {
            AlertDialog(
                onDismissRequest = { showQuickPanel = false },
                containerColor = Panel,
                title = { Text("Bảng điều khiển nổi", color = White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CADSS · schema ${CadssStore.SCHEMA_VERSION}", color = Cyan)
                        Text("Mẫu hình ảnh · $templateCount", color = White)
                        Text("Lịch tác vụ · WorkManager", color = White)
                        Text("Phiên làm việc · " + if (timerRunning) "đang chạy" else "không hoạt động", color = Muted)
                        Text("Trạng thái · $status", color = Muted, fontSize = 12.sp)
                    }
                },
                confirmButton = { TextButton(onClick = { showQuickPanel = false }) { Text("Đóng", color = Cyan) } }
            )
        }
    }
}

private fun decodeImage(context: android.content.Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (
        bounds.outWidth / sampleSize > 8_192 ||
        bounds.outHeight / sampleSize > 8_192 ||
        (bounds.outWidth / sampleSize).toLong() * (bounds.outHeight / sampleSize).toLong() > 32_000_000L
    ) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
}

@Composable private fun PageIntro(title: String, subtitle: String) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = White)
        Text(subtitle, fontSize = 13.sp, color = Muted)
    }
}
@Composable private fun SectionHeading(kicker: String, title: String) {
    Column {
        Text(kicker, color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        Text(title, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
@Composable private fun StatusChip(name: String, state: String) {
    Surface(color = Color.White.copy(alpha = .10f), shape = RoundedCornerShape(30.dp)) {
        Text("$name · $state", modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}
@Composable private fun MetricCard(title: String, value: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    PanelCard(modifier) {
        Icon(icon, null, tint = Cyan, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(10.dp))
        Text(value, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = White)
        Text(title, fontSize = 12.sp, color = White, fontWeight = FontWeight.SemiBold)
        Text(sub, fontSize = 10.sp, color = Muted)
    }
}
@Composable private fun QuickCard(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PanelCard(modifier.clickable(onClick = onClick)) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(25.dp))
        Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(sub, color = Muted, fontSize = 11.sp)
    }
}
@Composable private fun PanelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
}
@Composable private fun EmptyState(title: String, sub: String) {
    PanelCard { Icon(Icons.Default.Inbox, null, tint = Muted); Text(title, color = White, fontWeight = FontWeight.Bold); Text(sub, color = Muted, fontSize = 12.sp) }
}
@Composable private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = White, unfocusedTextColor = White,
    focusedBorderColor = Accent, unfocusedBorderColor = Panel2,
    focusedLabelColor = Cyan, unfocusedLabelColor = Muted,
    cursorColor = Cyan
)

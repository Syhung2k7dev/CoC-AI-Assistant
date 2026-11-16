package dev.syhung.cocassistant

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AutomationControlPanel(onStatus: (String) -> Unit) {
    val context = LocalContext.current
    var x by remember { mutableStateOf("540") }
    var y by remember { mutableStateOf("900") }
    var showConfirm by remember { mutableStateOf(false) }
    val enabled = isAutomationAccessibilityEnabled(context)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text("COC AGENT · ĐIỀU KHIỂN TỰ ĐỘNG")
        Text(if (enabled) "Accessibility đã được bật." else "Cần bật Accessibility Service trong Cài đặt Android.")
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, modifier = Modifier.fillMaxWidth()) { Text("Mở cài đặt Accessibility") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = x, onValueChange = { x = it.take(6) }, label = { Text("Tọa độ X") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = y, onValueChange = { y = it.take(6) }, label = { Text("Tọa độ Y") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        Button(onClick = { showConfirm = true }, enabled = enabled && x.toFloatOrNull() != null && y.toFloatOrNull() != null, modifier = Modifier.fillMaxWidth()) {
            Text("Xác nhận và chạy thử thao tác chạm")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = {
                onStatus(if (CoCAccessibilityService.pauseOrResume()) "Đã đổi trạng thái Pause/Resume." else "Không có workflow đang chạy.")
            }, modifier = Modifier.weight(1f)) { Text("Pause / Resume") }
            OutlinedButton(onClick = { CoCAccessibilityService.stop(); onStatus("Đã gửi lệnh Stop.") }, modifier = Modifier.weight(1f)) { Text("Stop") }
        }
        Spacer(Modifier.height(2.dp))
        Text("Giới hạn: 50 bước, 20 lần lặp, đếm ngược 5 giây; tự dừng nếu tọa độ ngoài màn hình hoặc Android từ chối thao tác.")
    }
    if (showConfirm) AlertDialog(
        onDismissRequest = { showConfirm = false },
        title = { Text("Xác nhận chạy thử") },
        text = { Text("Chờ 5 giây rồi chạm một lần tại X=${x}, Y=${y}. Hãy chắc chắn màn hình đang ở đúng vị trí.") },
        confirmButton = { TextButton(onClick = {
            showConfirm = false
            val started = CoCAccessibilityService.startWorkflow(context, listOf(AutomationStep.Tap(x.toFloat(), y.toFloat())))
            onStatus(if (started) "Workflow đã được xếp lịch; bắt đầu sau 5 giây." else "Không thể khởi chạy. Kiểm tra Accessibility Service.")
        }) { Text("Bắt đầu") } },
        dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Hủy") } }
    )
}

private fun isAutomationAccessibilityEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == context.packageName &&
            it.resolveInfo.serviceInfo.name == CoCAccessibilityService::class.java.name }
}

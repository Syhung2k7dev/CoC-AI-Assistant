package dev.syhung.cocassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SkillLibraryPanel(registry: AgentSkillRegistry) {
    val skills = remember { registry.all().sortedWith(compareBy<AgentSkill> { it.category }.thenBy { it.name }) }
    val categories = skills.map { it.category }.distinct()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("THƯ VIỆN SKILL", color = Color(0xFF4DD8D0), fontSize = 10.sp)
        Text("${skills.size} năng lực khai báo · ${categories.size} nhóm", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Text(
            "Các mục dưới đây là mô tả năng lực để Agent lựa chọn và phối hợp. Skill chỉ trở thành khả năng thực thi sau khi có workflow, kiểm thử và runtime tương ứng.",
            color = Color(0xFF9AA8C0), fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkillCountChip("Draft", skills.count { it.status == SkillLifecycle.DRAFT })
            SkillCountChip("Verified", skills.count { it.status == SkillLifecycle.VERIFIED })
            SkillCountChip("Disabled", skills.count { it.status == SkillLifecycle.DISABLED })
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
                        Text("ID: ${skill.id} · v${skill.version}", color = Color(0xFF71819D), fontSize = 9.sp)
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
        Text("$label: $count", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Color.White, fontSize = 10.sp)
    }
}

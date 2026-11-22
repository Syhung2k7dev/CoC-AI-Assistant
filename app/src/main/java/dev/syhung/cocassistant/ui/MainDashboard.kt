package dev.syhung.cocassistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.syhung.cocassistant.engine.CentralAgentLoop

@Composable
fun MainDashboard(agentLoop: CentralAgentLoop? = null) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Agent Control", "Skills & Research", "System & Settings")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }
        
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            when (selectedTab) {
                0 -> AgentTab(agentLoop)
                1 -> SkillsTab()
                2 -> SystemTab()
            }
        }
    }
}

@Composable
fun AgentTab(agentLoop: CentralAgentLoop?) {
    var status by remember { mutableStateOf("IDLE") }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Agent Status: $status", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Button(onClick = { 
                status = "RUNNING"
                agentLoop?.start() 
            }) { Text("Start Agent") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { status = "IDLE" }) { Text("Stop") }
        }
    }
}

@Composable
fun SkillsTab() {
    Column {
        Text("Learned Skills", style = MaterialTheme.typography.titleLarge)
        LazyColumn {
            // Placeholder for list of skills
        }
    }
}

@Composable
fun SystemTab() {
    Column {
        Text("System Settings", style = MaterialTheme.typography.titleLarge)
        TextField(
            value = "",
            onValueChange = {},
            label = { Text("Gemini API Key") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}


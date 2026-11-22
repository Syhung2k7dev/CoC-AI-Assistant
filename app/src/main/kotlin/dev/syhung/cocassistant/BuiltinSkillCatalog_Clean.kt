package dev.syhung.cocassistant

import org.json.JSONArray
import org.json.JSONObject

object BuiltinSkillCatalog_Clean {
    private val retiredSchedulerSkills = setOf(
        "account.schedule",
        "scheduler.schedule",
        "scheduler.cancel",
        "agent.scheduler"
    )
    const val version = 1
}


package dev.syhung.cocassistant

import android.content.Context
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

sealed class WorkflowRunOutcome {
    data class Completed(val result: CaslExecutionResult) : WorkflowRunOutcome()
    data class Failed(val reason: String) : WorkflowRunOutcome()
}

/** Connects verified CADSS Workflow versions to the shared CASL Skill/API runtime. */
class CaslWorkflowRunner(
    context: Context,
    private val apiRegistry: CaslApiRegistry
) {
    private val workflows = CaslWorkflowRegistry(context)
    private val skills = AgentSkillRegistry(context)
    private val skillLoader = SkillLoader(skills)

    suspend fun execute(
        workflowId: String,
        version: Int,
        inputs: JSONObject = JSONObject(),
        userApproved: Boolean = false
    ): WorkflowRunOutcome {
        if (!userApproved) return WorkflowRunOutcome.Failed("Cần xác nhận trước khi chạy Workflow.")
        val program = workflows.findVerified(workflowId, version)
            ?: return WorkflowRunOutcome.Failed("Không tìm thấy Workflow Verified đúng phiên bản.")
        val runtime = CaslScriptRuntime(
            apiRegistry = apiRegistry,
            skillScriptProvider = { skillId, skillVersion ->
                skillLoader.loadScript(skillId, skillVersion)
            }
        )
        return try {
            WorkflowRunOutcome.Completed(
                runtime.execute(program, inputs, userApproved = true)
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            WorkflowRunOutcome.Failed(error.message ?: "Workflow thất bại.")
        }
    }
}

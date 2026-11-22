package dev.syhung.cocassistant.navigation

import dev.syhung.cocassistant.database.dao.StateTransitionDao
import dev.syhung.cocassistant.database.entity.StateTransitionEntity
import dev.syhung.cocassistant.detector.Scene
import java.util.LinkedList
import java.util.Queue

/**
 * A navigation engine that uses historical state transitions to find a path between scenes.
 */
package dev.syhung.cocassistant.navigation

import dev.syhung.cocassistant.database.dao.StateTransitionDao
import dev.syhung.cocassistant.database.entity.StateTransitionEntity
import dev.syhung.cocassistant.detector.Scene
import java.util.LinkedList
import java.util.Queue

/**
 * A navigation engine that uses historical state transitions to find a path between scenes.
 */
class GraphNavigationEngine(
    private val transitionDao: StateTransitionDao,
    private val currentSceneProvider: () -> String,
    private val actionExecutor: suspend (String, String, String?) -> Boolean // skillId, actionType, args
) : NavigationEngine {

    override suspend fun navigateTo(targetScene: Scene) {
        val transitions = transitionDao.getAllTransitions()
        val graph = buildGraph(transitions)
        val current = currentSceneProvider()

        val path = findPath(graph, current, targetScene.name)
        
        if (path != null) {
            for (transition in path) {
                // Parse skillId from actionType (e.g., "TAP:skill_abc")
                val parts = transition.actionType.split(":")
                val skillId = parts.getOrNull(1) ?: ""
                val success = actionExecutor(skillId, transition.actionType, transition.args)
                if (!success) {
                    println("Failed to execute action: ${transition.actionType}")
                    break
                }
            }
        } else {
            println("No path found from $current to ${targetScene.name}")
        }
    }

    private fun buildGraph(transitions: List<StateTransitionEntity>): Map<String, List<StateTransitionEntity>> {
        return transitions.groupBy { it.fromState }
    }

    private fun findPath(graph: Map<String, List<StateTransitionEntity>>, start: String, end: String): List<StateTransitionEntity>? {
        if (start == end) return emptyList()

        val queue: Queue<Pair<String, List<StateTransitionEntity>>> = LinkedList()
        queue.add(start to emptyList())
        val visited = mutableSetOf<String>()

        while (queue.isNotEmpty()) {
            val (current, path) = queue.poll()!!
            
            if (current == end) return path
            
            if (visited.add(current)) {
                val neighbors = graph[current] ?: emptyList()
                for (transition in neighbors) {
                    if (transition.toState !in visited) {
                        queue.add(transition.toState to (path + transition))
                    }
                }
            }
        }
        return null
    }
}

package dev.syhung.cocassistant.touch

import android.graphics.Path
import android.graphics.PointF
import kotlin.random.Random

object TouchActionUtils {
    
    fun getHumanLikePoint(x: Float, y: Float, variance: Float = 5f): PointF {
        val randomX = x + Random.nextFloat() * variance * 2 - variance
        val randomY = y + Random.nextFloat() * variance * 2 - variance
        return PointF(randomX, randomY)
    }
    
    fun generateBezierPath(start: PointF, end: PointF): Path {
        val path = Path()
        path.moveTo(start.x, start.y)
        
        // Control points for curvature
        val midX = (start.x + end.x) / 2
        val midY = (start.y + end.y) / 2
        val controlX = midX + Random.nextFloat() * 100 - 50
        val controlY = midY + Random.nextFloat() * 100 - 50
        
        path.quadTo(controlX, controlY, end.x, end.y)
        return path
    }
}
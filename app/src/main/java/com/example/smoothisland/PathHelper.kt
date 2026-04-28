package com.example.smoothisland

import android.graphics.Path
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

object PathHelper {
    @JvmStatic
    fun createSmoothPath(width: Float, height: Float, r: Float): Path {
        // 使用 AndroidX Shapes 官方的 G2 连续性算法
        val rounding = CornerRounding(r, 1.0f)
        
        val polygon = RoundedPolygon(
            numVertices = 4,
            centerX = 0f,
            centerY = 0f,
            rounding = rounding,
            innerRadius = 0f,
            radius = r // 这里需要简化，直接构建一个多边形
        )
        // 矩形特殊处理：AndroidX Shapes 推荐直接用 rectangle 扩展方法
        val rectPolygon = RoundedPolygon.rectangle(width, height, rounding, rounding, rounding, rounding)
        
        return rectPolygon.toPath()
    }
}

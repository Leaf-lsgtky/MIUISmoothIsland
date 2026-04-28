package com.example.smoothisland

import android.graphics.Path
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.graphics.shapes.Point

object PathHelper {
    @JvmStatic
    fun createSmoothPath(width: Float, height: Float, r: Float): Path {
        // 使用标准的 RoundedPolygon 构造器手动定义四个顶点
        val rounding = CornerRounding(r, 1.0f)
        
        // 定义四个点，以中心为原点
        val vertices = listOf(
            Point(-width / 2f, -height / 2f),
            Point(width / 2f, -height / 2f),
            Point(width / 2f, height / 2f),
            Point(-width / 2f, height / 2f)
        )
        
        // 使用顶点列表构建多边形，这在所有版本中均是 Public API
        val polygon = RoundedPolygon(
            vertices = vertices,
            rounding = rounding
        )
        
        return polygon.toPath()
    }
}

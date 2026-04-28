package com.example.smoothisland

import android.graphics.Path
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

object PathHelper {
    @JvmStatic
    fun createSmoothPath(width: Float, height: Float, r: Float): Path {
        // 使用公开 API：RoundedPolygon 构造器或预定义形状
        // 确保使用完全公开的 API，避开 internal 构造函数
        val rounding = CornerRounding(r, 1.0f)
        
        // 使用 RoundedPolygon 的构造器来创建一个矩形
        // 这里的参数顺序是公开 API 标准
        val polygon = RoundedPolygon.Companion.rectangle(
            width = width,
            height = height,
            rounding = rounding
        )
        
        return polygon.toPath()
    }
}

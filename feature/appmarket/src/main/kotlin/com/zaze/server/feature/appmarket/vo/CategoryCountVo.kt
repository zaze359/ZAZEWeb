package com.zaze.server.feature.appmarket.vo

/**
 * 分类计数（门户左侧 rail 用）：分类名 label + 该分类下的应用数 count。
 * 字段命名对齐前端 CatItem { label, count }。
 */
data class CategoryCountVo(
    val label: String,
    val count: Int
)

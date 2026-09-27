package com.zaze.server.feature.appmarket.dto

/**
 * 通用分页结果信封：列表数据 list + 总量 total + 当前页 page + 每页大小 size。
 * 门户滚动加载与后台分页共用，前端据此判断是否还有下一页。
 */
data class PageResult<T>(
    val list: List<T>,
    val total: Int,
    val page: Int,
    val size: Int
)

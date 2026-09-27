package com.zaze.server.feature.appmarket.service

import com.zaze.server.feature.appmarket.dto.PageResult
import com.zaze.server.feature.appmarket.vo.AppDetailVo
import com.zaze.server.feature.appmarket.vo.AppVo
import com.zaze.server.feature.appmarket.vo.CategoryCountVo

interface AppMarketService {
    fun listApps(): List<AppVo>
    fun getAppDetail(appId: Long): AppDetailVo?
    /** 按关键词搜索应用（名称/包名/开发者/分类/简介），空关键词返回全部 */
    fun searchApps(keyword: String): List<AppVo>

    /** 门户分页列表：支持关键词 / 分类 / 排序，返回分页结果（服务端分页） */
    fun listAppsPaged(
        page: Int,
        size: Int,
        keyword: String?,
        category: String?,
        sort: String?
    ): PageResult<AppVo>

    /** 门户分类 rail 的计数（各分类数量，按数量降序） */
    fun listCategoryCounts(): List<CategoryCountVo>
}

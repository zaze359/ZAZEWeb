package com.zaze.server.feature.appmarket.service

import com.zaze.server.feature.appmarket.vo.AppDetailVo
import com.zaze.server.feature.appmarket.vo.AppVo

interface AppMarketService {
    fun listApps(): List<AppVo>
    fun getAppDetail(appId: Long): AppDetailVo?
    /** 按关键词搜索应用（名称/包名/开发者/分类/简介），空关键词返回全部 */
    fun searchApps(keyword: String): List<AppVo>
}

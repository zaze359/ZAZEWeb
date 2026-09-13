package com.zaze.server.feature.appmarket.service

import com.zaze.server.feature.appmarket.vo.AppDetailVo
import com.zaze.server.feature.appmarket.vo.AppVo

interface AppMarketService {
    fun listApps(): List<AppVo>
    fun getAppDetail(appId: Long): AppDetailVo?
}

package com.zaze.server.feature.appmarket.controller

import com.zaze.server.common.aop.LoggerManage
import com.zaze.server.common.controller.BaseController
import com.zaze.server.common.controller.Response
import com.zaze.server.feature.appmarket.service.AppMarketService
import com.zaze.server.feature.appmarket.vo.AppDetailVo
import com.zaze.server.feature.appmarket.vo.AppVo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/appmarket")
class AppMarketApiController : BaseController() {

    @Autowired
    private lateinit var appMarketService: AppMarketService

    @GetMapping("/apps")
    @LoggerManage(description = "获取应用市场应用列表")
    @ResponseBody
    fun listApps(@RequestParam(required = false) keyword: String?): Response<List<AppVo>> {
        return if (keyword.isNullOrBlank()) Response(appMarketService.listApps())
        else Response(appMarketService.searchApps(keyword))
    }

    @GetMapping("/apps/{id}")
    @LoggerManage(description = "获取应用市场应用详情（含版本与下载源）")
    @ResponseBody
    fun getAppDetail(@PathVariable id: Long): Response<AppDetailVo?> {
        return Response(appMarketService.getAppDetail(id))
    }
}

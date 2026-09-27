package com.zaze.server.feature.appmarket.controller

import com.zaze.server.common.aop.LoggerManage
import com.zaze.server.common.controller.BaseController
import com.zaze.server.common.controller.Response
import com.zaze.server.feature.appmarket.dto.PageResult
import com.zaze.server.feature.appmarket.service.AppMarketService
import com.zaze.server.feature.appmarket.vo.AppDetailVo
import com.zaze.server.feature.appmarket.vo.AppVo
import com.zaze.server.feature.appmarket.vo.CategoryCountVo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/appmarket")
class AppMarketApiController : BaseController() {

    @Autowired
    private lateinit var appMarketService: AppMarketService

    @GetMapping("/apps")
    @LoggerManage(description = "获取应用市场应用列表（服务端分页）")
    @ResponseBody
    fun listApps(
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "9") size: Int,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false, defaultValue = "latest") sort: String?
    ): Response<PageResult<AppVo>> {
        return Response(appMarketService.listAppsPaged(page, size, keyword, category, sort))
    }

    @GetMapping("/apps/categories")
    @LoggerManage(description = "获取应用市场分类计数")
    @ResponseBody
    fun listCategories(): Response<List<CategoryCountVo>> {
        return Response(appMarketService.listCategoryCounts())
    }

    @GetMapping("/apps/{id}")
    @LoggerManage(description = "获取应用市场应用详情（含版本与下载源）")
    @ResponseBody
    fun getAppDetail(@PathVariable id: Long): Response<AppDetailVo?> {
        return Response(appMarketService.getAppDetail(id))
    }
}

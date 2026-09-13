package com.zaze.server.feature.appmarket.controller

import com.zaze.server.common.aop.LoggerManage
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/appmarket")
class AppMarketPageController {

    @GetMapping(value = ["", "/"])
    @LoggerManage(description = "加载应用市场门户首页")
    fun index(): String {
        return "appmarket/index"
    }

    @GetMapping("/{id}")
    @LoggerManage(description = "加载应用市场应用详情页")
    fun detail(@PathVariable id: Long, model: Model): String {
        model.addAttribute("appId", id)
        return "appmarket/detail"
    }
}

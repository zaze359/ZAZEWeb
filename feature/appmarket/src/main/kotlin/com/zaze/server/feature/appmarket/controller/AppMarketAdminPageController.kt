package com.zaze.server.feature.appmarket.controller

import com.zaze.server.common.aop.LoggerManage
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 应用市场 - 管理后台页面入口。
 *
 * 注意：本类映射的 /appmarket/admin 是字面量路径，会比门户的 /appmarket/{id} 模板路径优先匹配。
 */
@Controller
@RequestMapping("/appmarket/admin")
class AppMarketAdminPageController {

    @GetMapping(value = ["", "/"])
    @LoggerManage(description = "加载应用市场管理后台")
    fun index(): String {
        return "appmarket/admin"
    }
}

package com.zaze.server.feature.auth.controller

import com.zaze.server.common.aop.LoggerManage
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

/** 登录页面入口（模板位于 root 的 templates/auth/login.html） */
@Controller
@RequestMapping("/login")
class AuthPageController {

    @GetMapping(value = ["", "/"])
    @LoggerManage(description = "加载登录页")
    fun index(): String {
        return "auth/login"
    }
}

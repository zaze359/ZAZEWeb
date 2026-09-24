package com.zaze.server.config

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * 试点 POC：把 Vue3 构建产物（static/vue/index.html）挂在 /vue 前缀下。
 *
 * 用途：history 路由的回退。当浏览器直接访问或刷新 `/vue/appmarket` 等不含文件扩展名
 * 的前端路由时，统一转发到 index.html，由前端 Vue Router 接管。
 *
 * 仅匹配**不含 "."** 的路径，因此 `/vue/index.html`、`/vue/assets/xxx.js` 等真实静态资源
 * 不会命中此映射，由 Spring 静态资源处理器正常返回，避免转发死循环。
 *
 * 该 Controller 仅为 POC 验证用，确认 Vue3 + Spring 集成方式后可整体删除
 * （连同 frontend/ 目录与 AuthInterceptor 中对应的 /vue 放行项）。
 */
@Controller
class VueSpaController {

    @GetMapping("/vue", "/vue/", "/vue/{path:[^\\.]*}")
    fun spa(): String = "forward:/vue/index.html"
}

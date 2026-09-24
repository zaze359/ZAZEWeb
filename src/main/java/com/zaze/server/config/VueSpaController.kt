package com.zaze.server.config

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * 生产环境 SPA 宿主：Vue3 构建产物（static/index.html）挂在站点根 /。
 *
 * history 路由回退：浏览器直接访问或刷新 /appmarket、/admin、/appmarket/123 等
 * 不含文件扩展名的前端路由时，统一转发到 index.html，由前端 Vue Router 接管。
 *
 * 仅匹配不含点号的路径，因此 /index.html、/assets/xxx.js、/favicon.ico 等
 * 真实静态资源不命中此映射，由 Spring 静态资源处理器返回，避免转发死循环。
 * 接口类请求（如 /api/ 下的 @RestController）优先级高于本回退。
 */
@Controller
class VueSpaController {

    @GetMapping("/", "/{path:[^\\.]*}")
    fun spa(): String = "forward:/index.html"
}

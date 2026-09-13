package com.zaze.server.config

import com.zaze.server.feature.auth.service.AuthService
import com.zaze.server.feature.auth.vo.UserVo
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

/**
 * 门户全局模型属性：让所有 Thymeleaf 页面都能拿到当前登录用户与管理员标记，
 * 用于渲染导航条（管理后台入口只对管理员显示）。
 *
 * 放在 root 的 `@ControllerAdvice` 而不是各个 feature 控制器里，
 * 是为了避免 feature 模块之间产生 `appmarket -> auth` 这类横向依赖。
 *
 * 说明：会话仍由 [AuthService] 内部通过注入的请求代理获取，不进入方法签名，
 * 避免被 `@LoggerManage` 序列化进日志。
 */
@ControllerAdvice
class PortalModelAdvice(
    private val authService: AuthService
) {

    /** 当前登录用户；未登录为 null */
    @ModelAttribute("user")
    fun user(): UserVo? = authService.currentUser()

    /** 当前登录用户是否为管理员 */
    @ModelAttribute("isAdmin")
    fun isAdmin(): Boolean = authService.isAdmin()
}

package com.zaze.server.config

import com.zaze.server.common.controller.Response
import com.zaze.server.common.utils.JsonUtil
import com.zaze.server.feature.auth.service.AuthService
import org.springframework.http.MediaType
import org.springframework.util.AntPathMatcher
import org.springframework.web.servlet.HandlerInterceptor
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * 会话鉴权拦截。
 *
 * 分两级：
 * 1. [ADMIN_PATTERNS] —— 必须登录**且**为 ADMIN（管理后台页面 + admin 接口）。
 * 2. [LOGIN_PATTERNS] —— 只要登录即可（首页、应用市场门户及其接口）。
 *
 * 未通过时：接口类请求返回 JSON（沿用 `Response` 信封，HTTP 状态同步为 401/403），
 * 页面类请求重定向到 `/login`。
 *
 * 放行的路径在 [WebMvcConfig] 的 exclude 中集中配置（登录页、登录接口、静态资源等）。
 */
class AuthInterceptor(
    private val authService: AuthService
) : HandlerInterceptor {

    private val matcher = AntPathMatcher()

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val uri = request.requestURI
        val session = request.getSession(false)
        val user = authService.currentUser(session)

        if (ADMIN_PATTERNS.any { matcher.match(it, uri) }) {
            if (user == null) {
                return deny(request, response, 401, "未登录", loggedIn = false)
            }
            if (!authService.isAdmin(session)) {
                return deny(request, response, 403, "需要管理员权限", loggedIn = true)
            }
            return true
        }

        if (LOGIN_PATTERNS.any { matcher.match(it, uri) } && user == null) {
            return deny(request, response, 401, "未登录", loggedIn = false)
        }
        return true
    }

    private fun deny(
        request: HttpServletRequest,
        response: HttpServletResponse,
        code: Int,
        msg: String,
        loggedIn: Boolean
    ): Boolean {
        if (wantsJson(request)) {
            response.status = code
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = "UTF-8"
            response.writer.write(JsonUtil.objToJson(Response<Any?>(code, null, msg)) ?: "")
        } else {
            // 页面访问：未登录回登录页；已登录但权限不足回首页（避免把已登录用户踢回登录页）
            response.sendRedirect(request.contextPath + (if (loggedIn) "/" else "/login"))
        }
        return false
    }

    private fun wantsJson(request: HttpServletRequest): Boolean {
        if (request.requestURI.startsWith("/api/")) return true
        val accept = request.getHeader("Accept")
        return accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE)
    }

    companion object {
        /** 仅管理员 */
        private val ADMIN_PATTERNS = listOf(
            "/appmarket/admin",
            "/appmarket/admin/**",
            "/api/v1/appmarket/admin/**",
            "/admin",
            "/admin/**"
        )

        /** 登录即可（非管理员也能访问） */
        private val LOGIN_PATTERNS = listOf(
            "/",
            "/appmarket",
            "/appmarket/**",
            "/api/v1/appmarket/**"
        )
    }
}

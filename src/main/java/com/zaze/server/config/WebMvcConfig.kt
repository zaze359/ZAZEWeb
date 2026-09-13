package com.zaze.server.config

import com.zaze.server.feature.auth.service.AuthService
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 注册会话鉴权拦截器。
 *
 * 放行规则集中在这里维护（[AuthInterceptor] 只描述“受保护的路径”）：
 * - `/login`、`/api/v1/auth/` 下的登录页与登录、登出、当前用户接口；
 * - 静态资源（css / js / images / vendor / webjars …）与错误页。
 *
 * 其余路径默认不拦截（例如 `/center` 这类既有示例页），
 * 只有命中 [AuthInterceptor] 中显式配置的路径才会校验登录态或管理员角色。
 */
@Configuration
class WebMvcConfig(
    private val authService: AuthService
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(AuthInterceptor(authService))
            .addPathPatterns("/**")
            .excludePathPatterns(*PUBLIC_PATTERNS)
    }

    companion object {
        /** 无需登录即可访问 */
        private val PUBLIC_PATTERNS = arrayOf(
            "/login",
            "/api/v1/auth/**",
            "/error",
            "/favicon.ico",
            "/css/**",
            "/js/**",
            "/images/**",
            "/img/**",
            "/vendor/**",
            "/webjars/**",
            "/static/**",
            "/sql/**"
        )
    }
}

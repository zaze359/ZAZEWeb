package com.zaze.server.feature.auth.controller

import com.zaze.server.common.aop.LoggerManage
import com.zaze.server.common.controller.BaseController
import com.zaze.server.common.controller.Response
import com.zaze.server.feature.auth.dto.LoginForm
import com.zaze.server.feature.auth.service.AuthService
import com.zaze.server.feature.auth.vo.UserVo
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthApiController(
    private val authService: AuthService
) : BaseController() {

    /**
     * 登录。
     *
     * **刻意不加 `@LoggerManage`**：`@LoggerManage` 切面会序列化方法的全部入参，
     * 一旦加上就会把明文密码写进日志。其余不涉及口令的接口仍按约定加切面。
     */
    @PostMapping("/login")
    fun login(@RequestBody form: LoginForm): Response<UserVo?> {
        val user = authService.login(form.username, form.password)
        return if (user != null) {
            Response(user)
        } else {
            Response<UserVo?>(401, null, "用户名或密码错误")
        }
    }

    @PostMapping("/logout")
    @LoggerManage(description = "用户登出")
    fun logout(): Response<Boolean> {
        return Response(authService.logout())
    }

    @GetMapping("/me")
    @LoggerManage(description = "获取当前登录用户")
    fun me(): Response<UserVo?> {
        return Response(authService.currentUser())
    }
}

package com.zaze.server.feature.auth.dto

/** 登录表单（明文密码仅用于本次校验，不落库、不回显、不记日志） */
data class LoginForm(
    val username: String? = null,
    val password: String? = null
)

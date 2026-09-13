package com.zaze.server.feature.auth.service

import com.zaze.server.feature.auth.vo.UserVo
import javax.servlet.http.HttpSession

/** 登录态在 Session 中的键 */
const val AUTH_SESSION_KEY = "AUTH_USER"

/**
 * 轻量会话认证。
 *
 * 刻意**不使用** HttpSession 作为方法参数：控制器方法会被 `@LoggerManage` 切面序列化全部入参，
 * 一旦把 session（或登录接口的明文密码）作为入参，就会被写进日志。
 * 这里通过注入的 [javax.servlet.http.HttpServletRequest] 代理取会话，控制器签名保持干净。
 */
interface AuthService {

    /** 校验账号密码；成功则建立会话并返回用户，失败返回 null */
    fun login(username: String?, password: String?): UserVo?

    /** 当前登录用户，未登录返回 null */
    fun currentUser(): UserVo?

    /** 当前登录用户（指定会话），供拦截器使用 */
    fun currentUser(session: HttpSession?): UserVo?

    /** 清除登录态 */
    fun logout(): Boolean

    fun isAdmin(): Boolean

    fun isAdmin(session: HttpSession?): Boolean
}

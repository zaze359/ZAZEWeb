package com.zaze.server.feature.auth.service.impl

import com.zaze.server.feature.auth.model.asVo
import com.zaze.server.feature.auth.pojo.UserRole
import com.zaze.server.feature.auth.repository.UserRepository
import com.zaze.server.feature.auth.service.AUTH_SESSION_KEY
import com.zaze.server.feature.auth.service.AuthService
import com.zaze.server.feature.auth.vo.UserVo
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession

/**
 * 会话认证实现：账号密码用 BCrypt 校验，登录态存 Session。
 *
 * 注意：密码校验只做 `matches`，明文不落库、不回显；
 * 登录接口的控制器方法不要加 `@LoggerManage`（切面会序列化入参，会把明文密码写进日志）。
 */
@Service
class AuthServiceImpl(
    private val userRepository: UserRepository
) : AuthService {

    @Autowired
    private lateinit var request: HttpServletRequest

    private val log = LoggerFactory.getLogger(AuthServiceImpl::class.java)

    private val encoder = BCryptPasswordEncoder()

    override fun login(username: String?, password: String?): UserVo? {
        val name = username?.trim()
        if (name.isNullOrEmpty() || password.isNullOrEmpty()) return null

        val user = userRepository.findByUsername(name) ?: return null
        if (!user.enabled) return null

        val hash = user.passwordHash
        if (hash.isNullOrBlank()) return null
        val matched = try {
            encoder.matches(password, hash)
        } catch (e: Exception) {
            // 非 BCrypt 格式的旧哈希（或脏数据）不应让登录 500
            log.warn("密码哈希校验异常 user={}", name, e)
            false
        }
        if (!matched) return null

        val vo = user.asVo()
        request.getSession(true).setAttribute(AUTH_SESSION_KEY, vo)
        return vo
    }

    override fun currentUser(): UserVo? = currentUser(request.getSession(false))

    override fun currentUser(session: HttpSession?): UserVo? {
        return session?.getAttribute(AUTH_SESSION_KEY) as? UserVo
    }

    override fun logout(): Boolean {
        request.getSession(false)?.removeAttribute(AUTH_SESSION_KEY)
        return true
    }

    override fun isAdmin(): Boolean = currentUser()?.role == UserRole.ADMIN.name

    override fun isAdmin(session: HttpSession?): Boolean = currentUser(session)?.role == UserRole.ADMIN.name
}

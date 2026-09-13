package com.zaze.server.feature.auth.bootstrap

import com.zaze.server.common.ext.jsonToList
import com.zaze.server.common.utils.FileUtil
import com.zaze.server.feature.auth.pojo.User
import com.zaze.server.feature.auth.pojo.UserRole
import com.zaze.server.feature.auth.repository.UserRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.core.io.ClassPathResource
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import java.util.Locale

/**
 * 内置种子账号（本项目不开放注册）。
 *
 * 仅在 `app_user` 表为空时执行：读取 classpath `data/auth_seed.json`，
 * 把明文口令用 BCrypt 加密后入库 —— 种子文件里的明文只存在于配置文件中，从不写进数据库。
 */
@Component
class AuthSeedLoader(
    private val userRepository: UserRepository
) : CommandLineRunner {

    private val encoder = BCryptPasswordEncoder()

    override fun run(vararg args: String?) {
        if (userRepository.count() > 0) return
        val resource = ClassPathResource("data/auth_seed.json")
        if (!resource.exists()) return
        val json = FileUtil.readByBytes(resource.inputStream).toString()
        val seeds = json.jsonToList(UserSeedDto::class.java) ?: return

        for (seed in seeds) {
            val username = seed.username?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val rawPassword = seed.password?.takeIf { it.isNotEmpty() } ?: continue
            if (userRepository.findByUsername(username) != null) continue
            userRepository.save(
                User(
                    username = username,
                    passwordHash = encoder.encode(rawPassword),
                    displayName = seed.displayName?.takeIf { it.isNotBlank() } ?: username,
                    role = parseRole(seed.role),
                    enabled = seed.enabled ?: true
                )
            )
        }
    }

    private fun parseRole(raw: String?): UserRole {
        val value = raw?.trim()?.uppercase(Locale.US) ?: return UserRole.USER
        return runCatching { UserRole.valueOf(value) }.getOrDefault(UserRole.USER)
    }
}

data class UserSeedDto(
    val username: String? = null,
    /** 仅用于初始化加密，不落库 */
    val password: String? = null,
    val displayName: String? = null,
    /** ADMIN / USER */
    val role: String? = null,
    val enabled: Boolean? = null
)

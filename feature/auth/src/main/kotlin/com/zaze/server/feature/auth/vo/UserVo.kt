package com.zaze.server.feature.auth.vo

/**
 * 对外暴露的用户信息。
 *
 * 刻意不包含 passwordHash —— 实体不得出服务层，且密码哈希绝不出现在任何响应里。
 */
data class UserVo(
    val id: Long = -1,
    val username: String? = null,
    val displayName: String? = null,
    /** ADMIN / USER */
    val role: String? = null,
    val enabled: Boolean = true
)

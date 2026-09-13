package com.zaze.server.feature.auth.pojo

import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.util.*
import javax.persistence.*

/** 角色：管理员可见管理后台，普通用户仅可见应用市场 */
enum class UserRole {
    ADMIN,
    USER
}

/**
 * 应用市场门户 - 用户
 *
 * 仅存密码哈希（BCrypt），明文密码不落库、不出服务层。
 * 角色以字符串形式持久化（[EnumType.STRING]），避免改枚举顺序导致语义漂移。
 */
@Entity
@Table(name = "app_user")
data class User(
    @Id
    @GeneratedValue
    val id: Long = -1,
    @Column(updatable = false)
    @CreationTimestamp
    val createTime: Date = Date(),
    @UpdateTimestamp
    val updateTime: Date = Date(),
    @Column(nullable = false, unique = true)
    val username: String? = null,
    /** BCrypt 哈希，不是明文 */
    @Column(nullable = false)
    val passwordHash: String? = null,
    @Column
    val displayName: String? = null,
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val role: UserRole = UserRole.USER,
    @Column
    val enabled: Boolean = true
)

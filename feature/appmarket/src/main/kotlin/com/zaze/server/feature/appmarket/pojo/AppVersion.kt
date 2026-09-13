package com.zaze.server.feature.appmarket.pojo

import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.util.*
import javax.persistence.*

/**
 * 应用市场 - 版本（隶属于某个 App，通过 appId 关联）
 */
@Entity
@Table(name = "appmarket_version")
data class AppVersion(
    @Id
    @GeneratedValue
    val id: Long = -1,
    @Column(updatable = false)
    @CreationTimestamp
    val createTime: Date = Date(),
    @UpdateTimestamp
    val updateTime: Date = Date(),
    @Column
    val appId: Long = -1,
    @Column
    val versionName: String? = null,
    @Column
    val versionCode: Long? = null,
    @Column
    @Temporal(TemporalType.DATE)
    val releaseDate: Date? = null,
    @Column
    val sizeMb: Long? = null,
    @Column(columnDefinition = "TEXT")
    val changelog: String? = null
)

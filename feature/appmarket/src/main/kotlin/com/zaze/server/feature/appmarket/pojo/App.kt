package com.zaze.server.feature.appmarket.pojo

import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.util.*
import javax.persistence.*

/**
 * 应用市场 - 应用
 *
 * 扁平外键设计：版本与下载源通过 Long 外键列关联，不使用 JPA 双向关系，
 * 由 Service 层通过 Repository 查询拼装，规避级联 / lazy / equals 复杂度。
 */
@Entity
@Table(name = "appmarket_app")
data class App(
    @Id
    @GeneratedValue
    val id: Long = -1,
    @Column(updatable = false)
    @CreationTimestamp
    val createTime: Date = Date(),
    @UpdateTimestamp
    val updateTime: Date = Date(),
    @Column
    val name: String? = null,
    @Column
    val packageName: String? = null,
    @Column
    val category: String? = null,
    @Column
    val developer: String? = null,
    @Column(columnDefinition = "TEXT")
    val summary: String? = null,
    @Column
    val iconUrl: String? = null,
    @Column
    val officialUrl: String? = null
)

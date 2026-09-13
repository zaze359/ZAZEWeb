package com.zaze.server.feature.appmarket.pojo

import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.util.*
import javax.persistence.*

/**
 * 应用市场 - 下载源（隶属于某个版本，通过 versionId 关联）
 *
 * sourceType 取值约定：GITHUB / FDROID / APKMIRROR / OFFICIAL / COOLAPK / MYAPP / OTHER（纯字符串列，可扩展）
 */
@Entity
@Table(name = "appmarket_source")
data class DownloadSource(
    @Id
    @GeneratedValue
    val id: Long = -1,
    @Column(updatable = false)
    @CreationTimestamp
    val createTime: Date = Date(),
    @UpdateTimestamp
    val updateTime: Date = Date(),
    @Column
    val versionId: Long = -1,
    @Column
    val sourceName: String? = null,
    @Column
    val sourceType: String? = null,
    @Column(columnDefinition = "TEXT")
    val downloadUrl: String? = null,
    @Column
    val region: String? = null,
    @Column
    val note: String? = null
)

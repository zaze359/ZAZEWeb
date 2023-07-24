package com.zaze.server.feature.showcase.pojo

import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.util.*
import javax.persistence.*

/**
 * 展示
 */
@Entity
@Table(name = "showcase")
data class Showcase (
    // "showcaseTitle": "黑色沙漠台服",
    // "showcaseTitleUrl": "https://www.tw.playblackdesert.com/",
    // "showcaseInfo": "Pearl Abyss MMO的開始 黑色沙漠
    // Remastered，更加真實的冒險正在等待著您。超越真實感，與玩家共享感動的黑色沙漠。",
    // "showcaseTag": "Game,官网",
    // "showcaseTagUrl": "https://www.tw.playblackdesert.com/",
    // "showcaseImg":
    // "https://s1.pearlcdn.com/TW/contents/img/common/header_logo_tw.png",
    // "showcaseTime": "2017-09-01"
    @Id
    @GeneratedValue
    val id: Long = -1,
    @Column(updatable = false)
    @CreationTimestamp
    val createTime: Date = Date(),
    @UpdateTimestamp
    val updateTime: Date = Date(),
    @Column
    val title: String? = null,
    @Column
    val url: String? = null,
    @Column
    val info: String? = null,
    @Column
    val tags: String? = null,
    @Column
    val imgUrl: String? = null
)
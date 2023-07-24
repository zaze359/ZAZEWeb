package com.zaze.server.feature.showcase.vo

import java.io.Serializable
import java.util.*


// "showcaseTitle": "黑色沙漠台服",
// "showcaseTitleUrl": "https://www.tw.playblackdesert.com/",
// "showcaseInfo": "Pearl Abyss MMO的開始 黑色沙漠
// Remastered，更加真實的冒險正在等待著您。超越真實感，與玩家共享感動的黑色沙漠。",
// "showcaseTag": "Game,官网",
// "showcaseTagUrl": "https://www.tw.playblackdesert.com/",
// "showcaseImg":
// "https://s1.pearlcdn.com/TW/contents/img/common/header_logo_tw.png",
// "showcaseTime": "2017-09-01"
data class ShowcaseVo(
    val showcaseId: Long?,
    val title: String,
    val url: String?,
    val info: String?,
    val tags: String?,
    val imgUrl: String?,
    val createTime: Long = 0,
    val updateTime: Long = 0,
) : Serializable
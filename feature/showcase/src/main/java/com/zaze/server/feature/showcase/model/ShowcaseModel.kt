package com.zaze.server.feature.showcase.model

import com.zaze.server.feature.showcase.pojo.Showcase
import com.zaze.server.feature.showcase.vo.ShowcaseVo
import java.util.*

fun Showcase.asVo(): ShowcaseVo {
    return ShowcaseVo(
        showcaseId = this.id,
        title = this.title ?: "",
        url = this.url,
        info = this.info,
        tags = this.tags,
        imgUrl = this.imgUrl,
        createTime = this.createTime.time,
        updateTime = this.updateTime.time,
    )
}

fun ShowcaseVo.asPojo(): Showcase {
    return Showcase(
        title = this.title,
        url = this.url,
        info = this.info,
        tags = this.tags,
        imgUrl = this.imgUrl,
        createTime = Date(this.createTime),
        updateTime = Date(this.updateTime),
    )
}
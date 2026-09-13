package com.zaze.server.feature.appmarket.model

import com.zaze.server.feature.appmarket.pojo.App
import com.zaze.server.feature.appmarket.pojo.AppVersion
import com.zaze.server.feature.appmarket.pojo.DownloadSource
import com.zaze.server.feature.appmarket.vo.AppVo
import com.zaze.server.feature.appmarket.vo.AppVersionVo
import com.zaze.server.feature.appmarket.vo.DownloadSourceVo

fun App.asVo(versionCount: Int = 0): AppVo = AppVo(
    id = this.id,
    name = this.name ?: "",
    packageName = this.packageName,
    category = this.category,
    developer = this.developer,
    summary = this.summary,
    iconUrl = this.iconUrl,
    officialUrl = this.officialUrl,
    versionCount = versionCount
)

fun AppVersion.asVo(sources: List<DownloadSourceVo> = emptyList()): AppVersionVo = AppVersionVo(
    id = this.id,
    appId = this.appId,
    versionName = this.versionName,
    versionCode = this.versionCode,
    releaseDate = this.releaseDate?.time,
    sizeMb = this.sizeMb,
    changelog = this.changelog,
    sourceCount = sources.size,
    sources = sources
)

fun DownloadSource.asVo(): DownloadSourceVo = DownloadSourceVo(
    id = this.id,
    versionId = this.versionId,
    sourceName = this.sourceName,
    sourceType = this.sourceType,
    downloadUrl = this.downloadUrl,
    region = this.region,
    note = this.note
)

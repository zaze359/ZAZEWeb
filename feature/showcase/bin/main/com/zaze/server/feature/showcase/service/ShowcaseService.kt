package com.zaze.server.feature.showcase.service

import com.zaze.server.feature.showcase.vo.ShowcaseVo
import org.springframework.data.domain.Pageable

// service 可以定义一个dto 使用，这里直接使用vo
interface ShowcaseService {
    fun saveShowcase(showcase: ShowcaseVo): ShowcaseVo
    fun deleteShowcases(ids: List<Long>)
    fun findByTagsLike(pageable: Pageable, tags: String): List<ShowcaseVo>
    fun getShowcaseList(): List<ShowcaseVo>
}
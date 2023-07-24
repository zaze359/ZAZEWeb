package com.zaze.server.feature.showcase.service.impl

import com.zaze.server.feature.showcase.model.asPojo
import com.zaze.server.feature.showcase.model.asVo
import com.zaze.server.feature.showcase.pojo.Showcase
import com.zaze.server.feature.showcase.repository.ShowcaseRepository
import com.zaze.server.feature.showcase.service.ShowcaseService
import com.zaze.server.feature.showcase.vo.ShowcaseVo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.util.*

@Service
@CacheConfig(cacheNames = ["showcases"])
class ShowcaseServiceImpl : ShowcaseService {

    @Autowired
    private lateinit var showcaseRepository: ShowcaseRepository


    @CacheEvict
    override fun saveShowcase(showcase: ShowcaseVo): ShowcaseVo {
        return showcaseRepository.save(showcase.asPojo()).asVo()
    }

    @CacheEvict
    override fun deleteShowcases(ids: List<Long>) {
        showcaseRepository.deleteByIds(ids)
    }

    @Cacheable
    override fun getShowcaseList(): List<ShowcaseVo> {
        val list = showcaseRepository.findAll()
        list.add(buildShowcase())
        return list.map {
            it.asVo()
        }
    }

    override fun findByTagsLike(pageable: Pageable, tags: String): List<ShowcaseVo> {
        val list = showcaseRepository.findByTagsLike(pageable, "%$tags%")
        for (i in 0 until 100L) {
            list.add(buildShowcase())
        }
        val offset = pageable.offset.toInt()
        return list.subList(
            offset, list.size.coerceAtMost(offset + pageable.pageSize)
        ).map {
            it.asVo()
        }
    }


    private fun buildShowcase(): Showcase {
        return Showcase(
            title = "明日方舟",
            url = "https://ak.hypergryph.com/",
            info = "《明日方舟》（《明日方舟Arknights》）是一款策略手游。你将作为罗德岛的一员,与罗德岛公开领导人阿米娅一同,雇佣人员频繁进入天灾影响后的高危地区,救助受难人群,处理矿石争端,以及对抗未知阻碍—— “罗德岛”的战术头脑,需要您的对策,请指引我们的航向。",
            tags = "Game",
            imgUrl = "https://avatars1.githubusercontent.com/u/2918581?s=200&v=4",
        )
    }
}
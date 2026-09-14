package com.zaze.server.feature.appmarket.service

import com.zaze.server.common.ext.jsonToList
import com.zaze.server.common.utils.FileUtil
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * 国内应用词典条目（应用名 → 包名）。
 */
data class CnDictEntry(
    val name: String,
    val packageName: String,
    val category: String? = null
)

/**
 * 本地「应用名 → 包名」词典。
 *
 * 存在的理由：国内主流商店（应用宝 / 小米 / 华为 / 酷安 / 360 …）均**不在服务端**提供按名搜索
 * 接口——实测其搜索结果都是客户端渲染，或需要签名（华为 `rtnCode 1002`），境外镜像站又不可达。
 * 因此「按名找国内应用」改为内置词典解决：数据随代码发布、加载进内存、查询微秒级、离线可用。
 *
 * 词典**只提供候选包名**，不提供元数据：真实元数据一律以应用宝详情页返回为准，
 * 管理员在导入前可见预览。这样即使词典包名写错，应用宝查不到就会报错，不会写入脏数据。
 *
 * 未命中的应用仍可走「上传 APK → 前端解析包名」的既有路径。
 */
@Component
class AppMarketCnDict {

    private val log = LoggerFactory.getLogger(AppMarketCnDict::class.java)

    /** 是否启用词典（默认 true；可用 -Dappmarket.dict.enabled=false 关闭） */
    val enabled: Boolean = (System.getProperty("appmarket.dict.enabled") ?: "true").toBoolean()

    /** 词典内容（不可变；加载失败时为空列表，不影响启动与其他上游） */
    private val entries: List<CnDictEntry> = load()

    /**
     * 按关键词模糊匹配（名称或包名包含关键词，忽略大小写）。
     * 名称**前缀**匹配排在前面——搜「微信」时「微信」应早于「微信读书」之类。
     */
    fun match(lower: String): List<CnDictEntry> {
        val kw = lower.trim().lowercase()
        if (kw.isEmpty() || !enabled || entries.isEmpty()) return emptyList()
        val byNamePrefix = entries.filter { it.name.lowercase().startsWith(kw) }
        val rest = entries.filter { e ->
            !e.name.lowercase().startsWith(kw) &&
                (e.name.lowercase().contains(kw) || e.packageName.lowercase().contains(kw))
        }
        return (byNamePrefix + rest).take(MAX_DICT_RESULTS)
    }

    private fun load(): List<CnDictEntry> {
        return try {
            val resource = ClassPathResource(DICT_PATH)
            if (!resource.exists()) {
                log.warn("未找到国内应用词典 {}，按名搜索将不含国内应用候选", DICT_PATH)
                return emptyList()
            }
            val json = FileUtil.readByBytes(resource.inputStream).toString()
            val items = json.jsonToList(CnDictItemDto::class.java) ?: emptyList()
            val list = items.mapNotNull { it.toEntry() }
            log.info("已加载国内应用词典 {} 条", list.size)
            list
        } catch (e: Exception) {
            log.warn("加载国内应用词典失败，按名搜索将不含国内应用候选", e)
            emptyList()
        }
    }

    /** JSON 反序列化用：字段一律可空，避免脏数据导致解析期 NPE */
    private data class CnDictItemDto(
        val name: String? = null,
        val packageName: String? = null,
        val category: String? = null
    ) {
        fun toEntry(): CnDictEntry? {
            val n = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val p = packageName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return CnDictEntry(n, p, category?.trim()?.takeIf { it.isNotEmpty() })
        }
    }

    companion object {
        const val DICT_PATH = "data/appmarket_cn_dict.json"
        const val MAX_DICT_RESULTS = 30
        const val SOURCE_LABEL = "本地词典"
    }
}

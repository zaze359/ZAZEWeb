package com.zaze.server.feature.appmarket.dto

/**
 * 分源探测明细：把一次查询里**每个上游各自的表现**单独列出来（互不相混）。
 *
 * 背景：旧实现给所有上游设了一条「统一截止时间」（默认 10s），等待又是按源串行进行的，
 * 于是排在前面的慢源会把预算吃光，导致后面本已成功的上游（如应用宝）被连带取消/丢弃，
 * 表现为「应用宝明明有，却搜不到」。现改为**每个源各自计时、各自超时**（真并行），
 * 并把每个源的状态/耗时/URL 分别返回，便于界面逐源展示与排障。
 */

/** 单个上游的探测状态 */
enum class ProbeStatus {
    /** 命中：连通且返回了可用结果 */
    HIT,

    /** 未命中：连通了，但上游没有该包名 / 没有匹配项 */
    MISS,

    /** 超时：超过该源自身的超时预算被放弃（不影响其它源） */
    TIMEOUT,

    /** 异常：请求或解析过程出错 */
    ERROR
}

/**
 * 上游所属分组。
 *
 * 背景：F-Droid / IzzyOnDroid / APKPure / Aptoide 在**国内基本不可用**（实测常年连接失败/超时），
 * 却又各自带着 5~15s 的超时预算——只要把它们和国内源一起并发查询，一次搜索就要陪着等到最慢的那个源超时，
 * 表现为「搜个国内应用也要十几秒」。因此按**可达性**把上游分成两组：
 * 默认只查 [DOMESTIC]，国内组全部未命中时才降级查 [OVERSEAS]（国外源作为备选）。
 */
enum class SourceGroup {
    /** 国内源：常态可达，默认查询（应用宝 / 本地词典 / Bing发现） */
    DOMESTIC,

    /** 国外备选源：国内组全部未命中时才查询（F-Droid / IzzyOnDroid / APKPure / Aptoide） */
    OVERSEAS
}

/** 单个上游的探测明细 */
data class SourceProbeVo(
    /** 上游名，如「应用宝」「F-Droid」「IzzyOnDroid」「APKPure」「Aptoide」「本地词典」「Bing发现」 */
    val source: String = "",
    val status: ProbeStatus = ProbeStatus.MISS,
    /** 该源自身耗时（ms），与其它源互不影响 */
    val elapsedMs: Long = 0,
    /** 该源实际请求的 URL（便于排障） */
    val url: String? = null,
    /** 未命中/超时/异常时的补充说明 */
    val message: String? = null,
    /** 该源属于哪一组（国内源 / 国外备选源）：界面据此分组展示 */
    val group: SourceGroup = SourceGroup.DOMESTIC
)

/** 按包名精确查询的返回体：预览 + 逐源明细（两者分开，不混在一起） */
data class ExternalLookupVo(
    val preview: ExternalAppPreview? = null,
    val probes: List<SourceProbeVo> = emptyList(),
    /** 本次是否**实际查询过**国外备选源（国内源未命中才会降级查询；命中时国外源根本不发请求） */
    val overseasQueried: Boolean = false
)

/** 按应用名搜索的返回体：候选列表 + 逐源明细（两者分开，不混在一起） */
data class ExternalSearchVo(
    val items: List<ExternalAppPreview> = emptyList(),
    val probes: List<SourceProbeVo> = emptyList(),
    /** 本次是否**实际查询过**国外备选源（国内源未命中才会降级查询；命中时国外源根本不发请求） */
    val overseasQueried: Boolean = false
)

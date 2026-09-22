package com.zaze.server.feature.appmarket.dto

/**
 * 导入链路追踪：把「一键导入 / 批量补全」的全过程拆成可实时推送的步骤。
 *
 * 后台导入要跨多个上游（应用宝 / F-Droid / IzzyOnDroid / APKPure / Aptoide）并写三张表，
 * 单次耗时可达数十秒；旧实现只在最后弹一句「已导入：xxx」，中间无任何状态、失败也只给笼统提示，
 * 排障困难。这里把每一步（请求了哪个上游、URL、耗时、命中与否、写了哪些表）建模为 [ImportStep]，
 * 由 SSE 逐条推送到管理端实时展示。
 */

/** 步骤状态：RUNNING 进行中 / OK 成功 / WARN 未命中或降级 / FAIL 失败 / SKIP 跳过 / INFO 决策信息 */
enum class StepStatus { RUNNING, OK, WARN, FAIL, SKIP, INFO }

/** 单条链路步骤 */
data class ImportStep(
    /** 序号（从 1 开始，同一任务内递增，前端按序渲染） */
    val seq: Int = 0,
    /** 发生时间（epoch millis） */
    val ts: Long = 0,
    /** 阶段名，如「解析包名」「请求上游」「写库」「补商店源」 */
    val phase: String = "",
    /** 涉及的上游/来源名，如「应用宝」「F-Droid」「Bing发现」；非上游步骤为 null */
    val upstream: String? = null,
    /** 明细：URL / 命中情况 / 写入结果 / 错误信息 */
    val detail: String = "",
    val status: StepStatus = StepStatus.INFO,
    /** 该步骤耗时（ms）；非耗时步骤为 null */
    val durationMs: Long? = null
)

/** 任务整体状态 */
enum class TaskStatus { RUNNING, DONE, FAILED }

/** 任务快照（前端首帧回放 + 增量推送都用它） */
data class ImportTaskSnapshot(
    val taskId: String = "",
    /** 任务类型：external-import（一键导入）/ batch-complete（批量补全） */
    val kind: String = "",
    /** 任务标题，如「导入 com.tencent.mm（应用宝）」 */
    val title: String = "",
    val status: TaskStatus = TaskStatus.RUNNING,
    val steps: List<ImportStep> = emptyList(),
    /** 结束时的总结/错误信息 */
    val message: String? = null,
    val startedAt: Long = 0,
    val finishedAt: Long? = null
)

/** 启动任务的返回体 */
data class ImportTaskRef(
    val taskId: String = "",
    val kind: String = "",
    val title: String = ""
)

/** 启动任务的请求体 */
data class ImportTaskRequest(
    /** external-import | batch-complete */
    val kind: String = "",
    val packageName: String? = null,
    val source: String? = null
)

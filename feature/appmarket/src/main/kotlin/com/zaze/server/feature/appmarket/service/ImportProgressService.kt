package com.zaze.server.feature.appmarket.service

import com.zaze.server.feature.appmarket.dto.ImportStep
import com.zaze.server.feature.appmarket.dto.ImportTaskSnapshot
import com.zaze.server.feature.appmarket.dto.StepStatus
import com.zaze.server.feature.appmarket.dto.TaskStatus
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * 埋点器：绑定在当前执行线程上，导入链路中的任意位置都能直接记一步。
 *
 * 之所以用 ThreadLocal 而不是把进度对象逐层传参：导入链路跨越 [AppMarketExternalService] 的
 * 多个私有方法（importApp → importFromXxx → fetchXxx），签名改动面太大；而每个导入任务
 * 都跑在独立的后台线程上，用 ThreadLocal 绑定即可，未绑定时所有 step 都是空操作（不影响既有调用方）。
 */
object ImportTracer {
    private val sink = ThreadLocal<(ImportStep) -> Unit>()

    fun bind(s: (ImportStep) -> Unit) = sink.set(s)
    fun unbind() = sink.remove()
    fun active(): Boolean = sink.get() != null

    /** 记一步（未绑定时静默忽略） */
    fun step(
        phase: String,
        detail: String,
        status: StepStatus = StepStatus.INFO,
        upstream: String? = null,
        durationMs: Long? = null
    ) {
        sink.get()?.invoke(
            ImportStep(
                ts = System.currentTimeMillis(),
                phase = phase,
                upstream = upstream,
                detail = detail,
                status = status,
                durationMs = durationMs
            )
        )
    }

    /**
     * 计时执行一段上游请求并自动记一步：命中 / 未命中 / 异常都会带上 URL 与耗时。
     * 未绑定埋点时直接执行，零开销。
     */
    fun <T> measure(phase: String, upstream: String?, url: String, block: () -> T): T {
        if (!active()) return block()
        val t0 = System.currentTimeMillis()
        return try {
            val r = block()
            val ms = System.currentTimeMillis() - t0
            step(
                phase, "$url → ${if (r != null) "命中" else "未命中"}（${ms}ms）",
                if (r != null) StepStatus.OK else StepStatus.WARN, upstream, ms
            )
            r
        } catch (e: Exception) {
            val ms = System.currentTimeMillis() - t0
            step(phase, "$url → 异常：${e.message}（${ms}ms）", StepStatus.FAIL, upstream, ms)
            throw e
        }
    }
}

/**
 * 导入任务进度中心：启动任务（后台线程执行）、收集链路步骤、向 SSE 订阅者实时广播。
 *
 * 采用「两段式」：POST 启动任务立即拿 taskId（避免长时间同步等待），前端再用 EventSource
 * 订阅 GET 流；后订阅的客户端会先收到一份 [ImportTaskSnapshot] 回放，再增量收 step，
 * 因此「任务已跑了一半才订阅」也不会丢步骤，任务已结束则直接回放 + end 并关闭流。
 */
@Service
class ImportProgressService {

    private class TaskState(
        val taskId: String,
        val kind: String,
        val title: String,
        val startedAt: Long,
        val seq: AtomicInteger = AtomicInteger(0),
        val steps: MutableList<ImportStep> = Collections.synchronizedList(mutableListOf()),
        val emitters: MutableList<SseEmitter> = Collections.synchronizedList(mutableListOf()),
        @Volatile var status: TaskStatus = TaskStatus.RUNNING,
        @Volatile var message: String? = null,
        @Volatile var finishedAt: Long? = null
    )

    private val tasks = ConcurrentHashMap<String, TaskState>()
    private val worker = Executors.newCachedThreadPool { r ->
        Thread(r, "appmarket-import-${WORKER_SEQ.getAndIncrement()}").also { it.isDaemon = true }
    }
    private val evictor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "appmarket-import-evictor").also { it.isDaemon = true }
    }

    /** 启动任务：立即返回 taskId，[block] 在后台线程执行并返回总结语（失败时取其异常 message） */
    fun start(kind: String, title: String, block: () -> String?): String {
        evictIfNeeded()
        val id = UUID.randomUUID().toString().replace("-", "").substring(0, 12)
        val st = TaskState(id, kind, title, System.currentTimeMillis())
        tasks[id] = st
        worker.submit {
            ImportTracer.bind { step -> addStep(id, step) }
            try {
                val msg = block()
                finish(id, TaskStatus.DONE, msg ?: "完成")
            } catch (e: Exception) {
                finish(id, TaskStatus.FAILED, e.message ?: e.toString())
            } finally {
                ImportTracer.unbind()
            }
        }
        return id
    }

    /** 订阅任务流（回放已有步骤 + 后续增量）；任务不存在抛 IllegalArgumentException */
    fun subscribe(taskId: String): SseEmitter {
        val st = tasks[taskId]
            ?: throw IllegalArgumentException("任务不存在或已过期：$taskId")
        // 批量补全可能耗时数分钟，给较长超时（0 = 不超时，由 end 事件显式结束）
        val emitter = SseEmitter(STREAM_TIMEOUT_MS)
        emitter.onCompletion { st.emitters.remove(emitter) }
        emitter.onTimeout { st.emitters.remove(emitter) }
        emitter.onError { st.emitters.remove(emitter) }
        st.emitters += emitter
        try {
            emitter.send(SseEmitter.event().name("snapshot").data(snapOf(st)))
            if (st.status != TaskStatus.RUNNING) {
                emitter.send(SseEmitter.event().name("end").data(snapOf(st)))
                emitter.complete()
            }
        } catch (_: Exception) {
            st.emitters.remove(emitter)
        }
        return emitter
    }

    /** 按 taskId 取快照；任务不存在/已过期返回 null（供非 SSE 的兜底查询用） */
    fun snapshot(taskId: String): ImportTaskSnapshot? = tasks[taskId]?.let { snapOf(it) }

    /** 由已确认存在的任务状态构造快照（非空，供 SSE 推送使用） */
    private fun snapOf(st: TaskState): ImportTaskSnapshot = ImportTaskSnapshot(
        taskId = st.taskId,
        kind = st.kind,
        title = st.title,
        status = st.status,
        steps = st.steps.toList(),
        message = st.message,
        startedAt = st.startedAt,
        finishedAt = st.finishedAt
    )

    private fun addStep(taskId: String, raw: ImportStep) {
        val st = tasks[taskId] ?: return
        val step = raw.copy(seq = st.seq.incrementAndGet())
        st.steps += step
        broadcast(st, SseEmitter.event().name("step").data(step))
    }

    private fun finish(taskId: String, status: TaskStatus, message: String) {
        val st = tasks[taskId] ?: return
        st.status = status
        st.message = message
        st.finishedAt = System.currentTimeMillis()
        broadcast(st, SseEmitter.event().name("end").data(snapOf(st)))
        st.emitters.toList().forEach { runCatching { it.complete() } }
        st.emitters.clear()
        // 结束后延迟清理，避免任务表无限增长（前端仍可在这段时间内回放）
        evictor.schedule({ tasks.remove(taskId) }, KEEP_FINISHED_MINUTES, TimeUnit.MINUTES)
    }

    private fun broadcast(st: TaskState, event: SseEmitter.SseEventBuilder) {
        for (e in st.emitters.toList()) {
            runCatching { e.send(event) }.onFailure { st.emitters.remove(e) }
        }
    }

    /** 粗粒度保护：任务数过多时先清已结束的，防止异常使用下无限堆积 */
    private fun evictIfNeeded() {
        if (tasks.size < MAX_TASKS) return
        tasks.entries.removeIf { it.value.status != TaskStatus.RUNNING }
    }

    private companion object {
        val WORKER_SEQ = AtomicInteger(0)
        const val STREAM_TIMEOUT_MS = 10L * 60 * 1000   // 10 分钟，覆盖批量补全
        const val KEEP_FINISHED_MINUTES = 10L
        const val MAX_TASKS = 200
    }
}

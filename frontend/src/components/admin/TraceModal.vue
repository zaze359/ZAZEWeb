<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { api } from '@/api/client'
import type { TraceStep } from '@/types'

const props = defineProps<{
  open: boolean
  title: string
  kind: string
  packageName?: string
  source?: string
}>()

const emit = defineEmits<{
  (e: 'cancel'): void
  (e: 'finished', payload: { status: string; message?: string; kind?: string }): void
}>()

const steps = ref<TraceStep[]>([])
const statusText = ref('')
const statusTone = ref<'run' | 'ok' | 'fail'>('run')
const elapsedSec = ref(0)
const finished = ref(false)

let es: EventSource | null = null
let timer: number | null = null
let startedAt = 0

// 步骤状态着色，对齐旧后台 trace-st-OK|WARN|FAIL|SKIP|RUNNING|INFO
const STEP_CLS: Record<string, string> = {
  OK: 'border-l-green-500',
  WARN: 'border-l-yellow-500',
  FAIL: 'border-l-red-500',
  SKIP: 'border-l-gray-400',
  RUNNING: 'border-l-indigo-500',
  INFO: 'border-l-gray-300'
}
const STEP_LABEL: Record<string, string> = {
  OK: '完成',
  WARN: '警告',
  FAIL: '失败',
  SKIP: '跳过',
  RUNNING: '进行中',
  INFO: '信息'
}

function openStream(taskId: string) {
  es = new EventSource('/api/v1/appmarket/admin/import-tasks/' + encodeURIComponent(taskId) + '/stream')
  es.addEventListener('snapshot', (e) => {
    try {
      const d = JSON.parse((e as MessageEvent).data)
      steps.value = d.steps || []
    } catch {
      /* ignore */
    }
  })
  es.addEventListener('step', (e) => {
    try {
      steps.value = [...steps.value, JSON.parse((e as MessageEvent).data)]
    } catch {
      /* ignore */
    }
  })
  es.addEventListener('end', (e) => {
    let snap: { steps?: TraceStep[]; status?: string; message?: string; kind?: string } = {}
    try {
      snap = JSON.parse((e as MessageEvent).data)
    } catch {
      /* ignore */
    }
    if (snap.steps) steps.value = snap.steps
    finished.value = true
    const ok = snap.status === 'DONE'
    statusTone.value = ok ? 'ok' : 'fail'
    statusText.value = ok ? '完成：' + (snap.message || '') : '失败：' + (snap.message || '')
    closeStream()
    emit('finished', { status: snap.status || '', message: snap.message, kind: snap.kind })
  })
  es.onerror = () => {
    // EventSource 自带重连；end 后已主动 close，此处的 error 多为正常断连，不打扰用户
    if (!es) return
  }
}

function start() {
  steps.value = []
  statusText.value = '进行中…'
  statusTone.value = 'run'
  finished.value = false
  elapsedSec.value = 0
  startedAt = Date.now()
  timer = window.setInterval(() => {
    elapsedSec.value = Math.round((Date.now() - startedAt) / 1000)
  }, 1000)
  api.admin
    .startImportTask({ kind: props.kind, packageName: props.packageName, source: props.source })
    .then((ref) => {
      if (ref.taskId) openStream(ref.taskId)
      else {
        finished.value = true
        statusTone.value = 'fail'
        statusText.value = ref.title || '启动导入任务失败'
      }
    })
    .catch((err) => {
      finished.value = true
      statusTone.value = 'fail'
      statusText.value = (err as Error).message || '启动导入任务失败'
    })
}

function closeStream() {
  if (es) {
    try {
      es.close()
    } catch {
      /* ignore */
    }
    es = null
  }
  if (timer != null) {
    clearInterval(timer)
    timer = null
  }
}

function close() {
  const stillRunning = !finished.value
  closeStream()
  emit('cancel')
  if (stillRunning) {
    statusText.value = (statusText.value ? statusText.value + ' · ' : '') + '任务仍在后台执行，稍后刷新列表查看结果'
  }
}

watch(
  () => props.open,
  (o) => {
    if (o) start()
    else closeStream()
  }
)

onBeforeUnmount(closeStream)
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-[70] flex items-center justify-center bg-black/40 p-4"
      @click.self="close"
    >
      <div class="flex max-h-[85vh] w-full max-w-lg flex-col rounded-2xl border border-admin-border bg-admin-surface shadow-xl">
        <div class="flex items-center justify-between border-b border-admin-border px-5 py-3">
          <h3 class="font-display text-lg font-semibold text-admin-text">{{ title }}</h3>
          <span class="text-xs text-admin-muted">已耗时 {{ elapsedSec }}s</span>
        </div>

        <!-- 状态条 -->
        <div class="px-5 pt-3">
          <div
            class="rounded-lg border px-3 py-2 text-sm"
            :class="{
              'border-indigo-200 bg-indigo-50 text-indigo-700': statusTone === 'run',
              'border-green-200 bg-green-50 text-green-700': statusTone === 'ok',
              'border-red-200 bg-red-50 text-red-700': statusTone === 'fail'
            }"
          >
            <i
              class="mr-1 fas"
              :class="statusTone === 'run' ? 'fa-spinner fa-spin' : statusTone === 'ok' ? 'fa-check-circle' : 'fa-times-circle'"
            ></i>
            {{ statusText || '已启动，等待后端推送链路…' }}
          </div>
        </div>

        <!-- 链路步骤 -->
        <div class="min-h-0 flex-1 overflow-y-auto px-5 py-3">
          <div v-if="!steps.length" class="py-6 text-center text-sm text-admin-muted">
            已启动，等待后端推送链路…
          </div>
          <div v-else class="space-y-1.5">
            <div
              v-for="(s, i) in steps"
              :key="i"
              class="flex gap-2 border-l-4 bg-admin-bg/60 px-3 py-1.5 text-sm"
              :class="STEP_CLS[(s.status || 'INFO').toUpperCase()] || STEP_CLS.INFO"
            >
              <span class="shrink-0 text-xs font-mono text-admin-muted">{{ s.seq ?? i + 1 }}</span>
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-x-2">
                  <span class="font-medium text-admin-text">{{ s.phase }}</span>
                  <span v-if="s.upstream" class="rounded bg-admin-border px-1.5 py-0.5 text-xs text-admin-muted">{{ s.upstream }}</span>
                  <span
                    class="rounded px-1.5 py-0.5 text-xs"
                    :class="{
                      'bg-green-100 text-green-700': (s.status || 'INFO') === 'OK',
                      'bg-yellow-100 text-yellow-700': (s.status || 'INFO') === 'WARN',
                      'bg-red-100 text-red-700': (s.status || 'INFO') === 'FAIL',
                      'bg-gray-100 text-gray-500': (s.status || 'INFO') === 'SKIP',
                      'bg-indigo-100 text-indigo-700': (s.status || 'INFO') === 'RUNNING',
                      'bg-gray-50 text-gray-400': (s.status || 'INFO') === 'INFO'
                    }"
                  >{{ STEP_LABEL[(s.status || 'INFO').toUpperCase()] || s.status }}</span>
                </div>
                <div v-if="s.detail" class="mt-0.5 break-words text-xs text-admin-muted">{{ s.detail }}</div>
              </div>
              <span v-if="s.durationMs != null" class="shrink-0 text-xs text-admin-muted">{{ s.durationMs }}ms</span>
            </div>
          </div>
        </div>

        <div class="flex justify-end gap-2 border-t border-admin-border px-5 py-3">
          <button
            class="rounded-lg border border-admin-border px-3 py-1.5 text-sm text-admin-muted transition hover:text-admin-text"
            @click="close"
          >
            {{ finished ? '关闭' : '关闭（后台继续）' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

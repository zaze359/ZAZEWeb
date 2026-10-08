<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { analyzeApk } from '@/utils/apk'
import type { ApkAnalysis, AnalyzeProgress } from '@/utils/apk'
import ApkAnalysisPanel from '@/components/apk/ApkAnalysisPanel.vue'

const router = useRouter()
const APK_MAX_BYTES = 200 * 1024 * 1024

const file = ref<File | null>(null)
const state = ref<'idle' | 'analyzing' | 'done' | 'error'>('idle')
const errMsg = ref('')
const analysis = ref<ApkAnalysis | null>(null)
const progress = ref<AnalyzeProgress | null>(null)
const copied = ref(false)

const stageText = computed(() => {
  const s = progress.value?.stage
  return s === 'zip'
    ? '读取文件结构'
    : s === 'manifest'
      ? '解析清单'
      : s === 'hash'
        ? '计算指纹'
        : s === 'done'
          ? '完成'
          : ''
})

function pickFile(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0] ?? null
  if (f) startAnalysis(f)
}

function onDrop(e: DragEvent) {
  const f = e.dataTransfer?.files?.[0] ?? null
  if (f) startAnalysis(f)
}

async function startAnalysis(f: File) {
  if (!/\.apk$/i.test(f.name)) {
    errMsg.value = '请选择 .apk 文件'
    state.value = 'error'
    return
  }
  if (f.size === 0) {
    errMsg.value = '文件为空，无法分析'
    state.value = 'error'
    return
  }
  if (f.size > APK_MAX_BYTES) {
    errMsg.value = '文件过大（超过 200MB），请选择更小的 APK'
    state.value = 'error'
    return
  }
  file.value = f
  errMsg.value = ''
  analysis.value = null
  state.value = 'analyzing'
  try {
    analysis.value = await analyzeApk(f, {
      onProgress: (p) => {
        progress.value = p
      }
    })
    state.value = 'done'
  } catch (e) {
    errMsg.value = 'APK 解析失败：' + ((e as Error).message || '文件可能损坏或经过加固')
    state.value = 'error'
  }
}

function reset() {
  file.value = null
  analysis.value = null
  progress.value = null
  errMsg.value = ''
  state.value = 'idle'
}

async function copyJson() {
  if (!analysis.value) return
  try {
    await navigator.clipboard.writeText(JSON.stringify(analysis.value, null, 2))
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    /* 剪贴板不可用时静默失败 */
  }
}

function downloadJson() {
  if (!analysis.value) return
  const a = analysis.value.basic
  const name = `${a.packageName || 'apk'}-${a.versionName || 'x'}-analysis.json`
  const blob = new Blob([JSON.stringify(analysis.value, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = name
  link.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div class="min-h-screen bg-admin-bg p-6 text-admin-text">
    <div class="mx-auto max-w-5xl">
      <div class="mb-6 flex items-center justify-between">
        <div>
          <p class="text-xs text-admin-muted">// ADMIN CONSOLE</p>
          <h1 class="font-display text-2xl font-bold">APK 分析</h1>
          <p class="mt-1 text-sm text-admin-muted">浏览器本地解析 APK，APK 不会上传到服务器。</p>
        </div>
        <button
          class="rounded-lg border border-admin-border px-3 py-1.5 text-sm text-admin-muted transition hover:text-admin-text"
          @click="router.push('/admin')"
        >
          <i class="fas fa-arrow-left"></i> 返回后台
        </button>
      </div>

      <!-- 选择区 -->
      <div
        v-if="state === 'idle'"
        class="flex flex-col items-center justify-center rounded-2xl border-2 border-dashed border-admin-border bg-admin-surface p-10 text-center"
        @dragover.prevent
        @drop.prevent="onDrop"
      >
        <i class="fas fa-file-archive text-3xl text-admin-accent"></i>
        <p class="mt-3 text-sm text-admin-text">拖拽 APK 到此处，或点击选择文件</p>
        <p class="mt-1 text-xs text-admin-muted">支持最大 200MB；分析全过程在本地完成</p>
        <label
          class="mt-4 cursor-pointer rounded-lg bg-admin-accent px-4 py-2 text-sm text-white transition hover:opacity-90"
        >
          选择 APK 文件
          <input type="file" accept=".apk" class="hidden" @change="pickFile" />
        </label>
      </div>

      <!-- 进度 -->
      <div v-else-if="state === 'analyzing'" class="rounded-2xl border border-admin-border bg-admin-surface p-6">
        <div class="mb-2 flex items-center justify-between text-sm">
          <span class="text-admin-text">{{ stageText }}…</span>
          <span class="text-admin-muted">{{ progress?.percent ?? 0 }}%</span>
        </div>
        <div class="h-2 w-full overflow-hidden rounded-full bg-admin-bg">
          <div
            class="h-full bg-admin-accent transition-all"
            :style="{ width: (progress?.percent ?? 0) + '%' }"
          ></div>
        </div>
        <p v-if="progress?.message" class="mt-2 text-xs text-admin-muted">{{ progress.message }}</p>
      </div>

      <!-- 错误 -->
      <div v-else-if="state === 'error'" class="rounded-2xl border border-red-300 bg-red-50 p-6 text-red-700">
        <p class="text-sm font-medium"><i class="fas fa-times-circle"></i> {{ errMsg }}</p>
        <button class="mt-3 rounded-lg border border-red-300 px-3 py-1.5 text-sm" @click="reset">重新选择</button>
      </div>

      <!-- 结果 -->
      <div v-else-if="state === 'done' && analysis">
        <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
          <div class="text-sm text-admin-muted">已分析 <code>{{ analysis.basic.fileName }}</code></div>
          <div class="flex gap-2">
            <button
              class="rounded-lg border border-admin-border px-3 py-1.5 text-sm text-admin-muted transition hover:text-admin-text"
              @click="copyJson"
            >
              <i class="fas" :class="copied ? 'fa-check' : 'fa-copy'"></i>
              {{ copied ? '已复制' : '复制 JSON' }}
            </button>
            <button
              class="rounded-lg border border-admin-border px-3 py-1.5 text-sm text-admin-muted transition hover:text-admin-text"
              @click="downloadJson"
            >
              <i class="fas fa-download"></i> 下载 JSON
            </button>
            <button
              class="rounded-lg bg-admin-accent px-3 py-1.5 text-sm text-white transition hover:opacity-90"
              @click="reset"
            >
              <i class="fas fa-redo"></i> 重新选择
            </button>
          </div>
        </div>

        <ApkAnalysisPanel :analysis="analysis" variant="full" />
      </div>
    </div>
  </div>
</template>

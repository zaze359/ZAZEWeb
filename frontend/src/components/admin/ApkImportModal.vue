<script setup lang="ts">
import { ref, watch } from 'vue'
import { api } from '@/api/client'
import type { ApkImportInput } from '@/types'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ (e: 'cancel'): void; (e: 'imported'): void }>()

const APK_MAX_BYTES = 200 * 1024 * 1024
const ICON_MAX_BYTES = 100 * 1024
const PARSER_SRC = '/vendor/app-info-parser/app-info-parser.min.js'

const file = ref<File | null>(null)
const status = ref<'idle' | 'loading' | 'parsing' | 'done' | 'error'>('idle')
const errMsg = ref('')
const parsed = ref<ApkImportInput | null>(null)
const iconTooLarge = ref(false)

const inputCls =
  'w-full rounded-lg border border-admin-border bg-admin-surface px-3 py-2 text-sm text-admin-text outline-none focus:border-admin-accent'
const btnGhost =
  'rounded-lg border border-admin-border px-3 py-1.5 text-sm text-admin-muted transition hover:text-admin-text'
const btnPrimary =
  'rounded-lg bg-admin-accent px-3 py-1.5 text-sm text-white transition hover:opacity-90 disabled:opacity-50'

function loadParser(): Promise<void> {
  if ((window as any).AppInfoParser) return Promise.resolve()
  return new Promise((resolve, reject) => {
    const s = document.createElement('script')
    s.src = PARSER_SRC
    s.onload = () => resolve()
    s.onerror = () => reject(new Error('解析库加载失败，请检查网络或联系管理员'))
    document.head.appendChild(s)
  })
}

watch(
  () => props.open,
  (o) => {
    if (!o) return
    file.value = null
    status.value = 'idle'
    errMsg.value = ''
    parsed.value = null
    iconTooLarge.value = false
  }
)

async function onFile(e: Event) {
  const input = e.target as HTMLInputElement
  const f = input.files?.[0] ?? null
  parsed.value = null
  iconTooLarge.value = false
  errMsg.value = ''
  if (!f) return
  if (f.size > APK_MAX_BYTES) {
    errMsg.value = '文件过大（超过 200MB），请选择更小的 APK'
    return
  }
  status.value = 'loading'
  try {
    await loadParser()
    status.value = 'parsing'
    const r = await new (window as any).AppInfoParser(f).parse()
    const pkg = r && r.package
    if (!pkg) {
      errMsg.value = '未能解析出包名，无法导入'
      status.value = 'error'
      return
    }
    const icon = r.icon || ''
    iconTooLarge.value = !!icon && icon.length > ICON_MAX_BYTES
    parsed.value = {
      packageName: pkg,
      name: (r.application && r.application.label) || pkg,
      versionName: r.versionName || null,
      versionCode: r.versionCode != null ? Number(r.versionCode) : null,
      iconDataUri: iconTooLarge.value ? null : icon || null,
      sizeMb: f.size ? Math.round(f.size / 1024 / 1024) : null
    }
    status.value = 'done'
  } catch {
    errMsg.value = 'APK 解析失败：文件可能损坏或经过加固'
    status.value = 'error'
  }
}

async function confirmImport() {
  if (!parsed.value?.packageName) return
  try {
    await api.admin.importFromApk(parsed.value)
    emit('imported')
    emit('cancel')
  } catch (e) {
    errMsg.value = (e as Error).message || '导入失败'
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 p-4"
      @click.self="emit('cancel')"
    >
      <div class="w-full max-w-md rounded-2xl border border-admin-border bg-admin-surface p-5 shadow-xl">
        <h3 class="mb-1 font-display text-lg font-semibold text-admin-text">从 APK 导入</h3>
        <p class="mb-4 text-xs text-admin-muted">
          浏览器本地解析 APK，仅提交解析出的元数据；APK 不会上传到服务器。
        </p>

        <input type="file" accept=".apk" :class="[inputCls, 'mb-3']" @change="onFile" />

        <div v-if="status === 'loading'" class="text-sm text-admin-muted">
          <i class="fas fa-spinner fa-spin"></i> 正在加载解析库…
        </div>
        <div v-else-if="status === 'parsing'" class="text-sm text-admin-muted">
          <i class="fas fa-spinner fa-spin"></i> 正在解析 APK…
        </div>
        <div v-else-if="errMsg" class="rounded-lg border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-700">
          {{ errMsg }}
        </div>

        <div v-if="parsed" class="rounded-xl border border-admin-border p-3">
          <div class="flex items-center gap-3">
            <img v-if="parsed.iconDataUri" :src="parsed.iconDataUri" class="h-12 w-12 rounded object-cover" alt="" />
            <span
              v-else
              class="flex h-12 w-12 items-center justify-center rounded bg-admin-border text-admin-muted"
            >
              <i class="fa fa-cube"></i>
            </span>
            <div class="min-w-0">
              <div class="truncate font-medium text-admin-text">{{ parsed.name }}</div>
              <div class="text-xs text-admin-muted"><code>{{ parsed.packageName }}</code></div>
            </div>
          </div>
          <div class="mt-2 space-y-0.5 text-xs text-admin-muted">
            <div>版本：{{ parsed.versionName || '-' }}<template v-if="parsed.versionCode != null">（{{ parsed.versionCode }}）</template></div>
            <div v-if="parsed.sizeMb != null">大小：{{ parsed.sizeMb }} MB</div>
          </div>
          <div v-if="iconTooLarge" class="mt-1 text-xs text-yellow-600">图标过大（超过 100KB），仅预览不入库</div>
        </div>

        <div class="mt-5 flex justify-end gap-2">
          <button :class="btnGhost" @click="emit('cancel')">取消</button>
          <button :class="btnPrimary" :disabled="status !== 'done'" @click="confirmImport">
            确认导入
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

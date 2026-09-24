<script setup lang="ts">
import { ref, watch } from 'vue'
import { api } from '@/api/client'
import type { ExternalPreview, ExternalLookupResult, ExternalSearchResult, TraceProbe, ImportSource } from '@/types'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{
  (e: 'cancel'): void
  (e: 'import-external', payload: { packageName: string; source: string }): void
}>()

const input = ref('')
const loading = ref(false)
const errMsg = ref('')
const preview = ref<ExternalPreview | null>(null)
const candidates = ref<ExternalPreview[]>([])
const probes = ref<TraceProbe[]>([])
const overseasQueried = ref<boolean | null>(null)
const sources = ref<ImportSource[]>([])

const inputCls =
  'w-full rounded-lg border border-admin-border bg-admin-surface px-3 py-2 text-sm text-admin-text outline-none focus:border-admin-accent'
const btnGhost =
  'rounded-lg border border-admin-border px-3 py-1.5 text-sm text-admin-muted transition hover:text-admin-text'
const btnPrimary =
  'rounded-lg bg-admin-accent px-3 py-1.5 text-sm text-white transition hover:opacity-90 disabled:opacity-50'

// 包名 / 商店链接走精确查询；自由文本（应用名）走模糊搜索
function isPackageOrUrl(raw: string): boolean {
  if (/^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$/.test(raw)) return true
  if (raw.includes('/')) return true
  if (/[?&]id=/.test(raw)) return true
  return false
}

const PROBE_CLS: Record<string, string> = {
  HIT: 'text-green-600',
  MISS: 'text-admin-muted',
  TIMEOUT: 'text-yellow-600',
  ERROR: 'text-red-600'
}
const PROBE_ICON: Record<string, string> = {
  HIT: 'fa-check-circle',
  MISS: 'fa-circle-o',
  TIMEOUT: 'fa-clock-o',
  ERROR: 'fa-exclamation-circle'
}
const PROBE_LABEL: Record<string, string> = {
  HIT: '命中',
  MISS: '未命中',
  TIMEOUT: '超时',
  ERROR: '异常'
}
const GROUP_META: Record<string, { title: string; hint: string; badge: string }> = {
  DOMESTIC: { title: '国内源', hint: '常态可达，默认查询', badge: 'bg-indigo-100 text-indigo-700' },
  OVERSEAS: { title: '国外备选源', hint: '国内源未命中时才降级查询', badge: 'bg-gray-100 text-gray-500' }
}
function groupKeyOf(g?: string): 'DOMESTIC' | 'OVERSEAS' {
  return g === 'OVERSEAS' ? 'OVERSEAS' : 'DOMESTIC'
}

function probeBlocks() {
  const list = probes.value || []
  if (!list.length) return []
  return (['DOMESTIC', 'OVERSEAS'] as const)
    .map((g) => ({ key: g, items: list.filter((p) => groupKeyOf(p.group) === g) }))
    .filter((x) => x.items.length)
}

function candidateGroups() {
  const list = candidates.value || []
  const bySource: Record<string, ExternalPreview[]> = {}
  const order: string[] = []
  list.forEach((p) => {
    const k = p.source || '未知来源'
    if (!bySource[k]) {
      bySource[k] = []
      order.push(k)
    }
    bySource[k].push(p)
  })
  return order.map((k) => ({ source: k, items: bySource[k], group: groupKeyOf(bySource[k][0].group) }))
}

function reset() {
  input.value = ''
  loading.value = false
  errMsg.value = ''
  preview.value = null
  candidates.value = []
  probes.value = []
  overseasQueried.value = null
}

async function loadSources() {
  try {
    sources.value = await api.admin.externalSources()
  } catch {
    /* 列表是辅助信息，失败不阻塞 */
  }
}

watch(
  () => props.open,
  (o) => {
    if (o) {
      reset()
      loadSources()
    }
  }
)

async function lookup() {
  const raw = input.value.trim()
  if (!raw) {
    errMsg.value = '请输入包名、商店链接或应用名'
    return
  }
  loading.value = true
  errMsg.value = ''
  preview.value = null
  candidates.value = []
  probes.value = []
  try {
    if (isPackageOrUrl(raw)) {
      const r: ExternalLookupResult = await api.admin.externalLookup(raw)
      preview.value = r.preview || null
      probes.value = r.probes || []
      overseasQueried.value = r.overseasQueried ?? null
      if (!preview.value && r.messages && r.messages.length) errMsg.value = r.messages.join('；')
    } else {
      const r: ExternalSearchResult = await api.admin.externalSearch(raw)
      candidates.value = r.items || []
      probes.value = r.probes || []
      overseasQueried.value = r.overseasQueried ?? null
      if (!candidates.value.length) {
        const m = r.messages && r.messages[0]
        errMsg.value =
          m && m !== '请求成功'
            ? m
            : '未找到匹配的应用。国内应用可试试常见名称（微信 / 抖音 / 高德地图…），冷门应用请改用「从 APK 导入」上传安装包解析包名'
      }
    }
  } catch (e) {
    errMsg.value = (e as Error).message || '查询失败'
  } finally {
    loading.value = false
  }
}

function doImport(p: ExternalPreview) {
  if (!p.packageName) return
  emit('import-external', { packageName: p.packageName, source: p.source || '' })
  emit('cancel')
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 p-4"
      @click.self="emit('cancel')"
    >
      <div class="flex max-h-[85vh] w-full max-w-lg flex-col rounded-2xl border border-admin-border bg-admin-surface shadow-xl">
        <div class="border-b border-admin-border px-5 py-3">
          <h3 class="font-display text-lg font-semibold text-admin-text">导入外部资源</h3>
          <p class="mt-0.5 text-xs text-admin-muted">
            从外部商店 / 仓库查询应用并一键入库（应用宝、F-Droid、IzzyOnDroid 等）。
          </p>
        </div>

        <div class="min-h-0 flex-1 overflow-y-auto px-5 py-4">
          <!-- 搜索源徽章 -->
          <div v-if="sources.length" class="mb-3 flex flex-wrap items-center gap-1.5">
            <span class="text-xs text-admin-muted">搜索源：</span>
            <span
              v-for="s in sources"
              :key="s.id || s.name"
              class="rounded px-1.5 py-0.5 text-xs"
              :class="s.enabled ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'"
              :title="s.searchUrl || ''"
            >{{ s.name || s.id }}<template v-if="!s.enabled">（已停用）</template></span>
          </div>

          <!-- 输入 -->
          <div class="flex gap-2">
            <input
              v-model="input"
              type="text"
              :class="[inputCls, 'flex-1']"
              placeholder="包名（com.xxx.app）/ 商店链接 / 应用名"
              @keyup.enter="lookup"
            />
            <button :class="btnPrimary" :disabled="loading" @click="lookup">
              <i class="fas" :class="loading ? 'fa-spinner fa-spin' : 'fa-search'"></i> 查询
            </button>
          </div>

          <div v-if="errMsg" class="mt-3 rounded-lg border border-yellow-300 bg-yellow-50 px-3 py-2 text-sm text-yellow-700">
            {{ errMsg }}
          </div>

          <!-- 精确查询预览 -->
          <div v-if="preview" class="mt-3 rounded-xl border border-admin-border p-3">
            <div class="flex items-center gap-3">
              <img v-if="preview.iconSrc" :src="preview.iconSrc" class="h-12 w-12 rounded object-cover" alt="" @error="preview!.iconSrc = ''" />
              <span v-else class="flex h-12 w-12 items-center justify-center rounded bg-admin-border text-admin-muted">
                <i class="fa fa-cube"></i>
              </span>
              <div class="min-w-0">
                <div class="font-medium text-admin-text">{{ preview.name || preview.packageName }}</div>
                <div class="text-xs text-admin-muted"><code>{{ preview.packageName }}</code></div>
              </div>
              <span v-if="preview.source" class="ml-auto shrink-0 rounded bg-indigo-100 px-2 py-0.5 text-xs text-indigo-700">{{ preview.source }}</span>
            </div>
            <p v-if="preview.summary" class="mt-2 text-xs text-admin-muted">{{ preview.summary }}</p>
            <div class="mt-2 flex flex-wrap gap-x-3 text-xs text-admin-muted">
              <span v-if="preview.category">分类：{{ preview.category }}</span>
              <span v-if="preview.developer">开发者：{{ preview.developer }}</span>
              <span v-if="preview.latestVersionName">最新版：v{{ preview.latestVersionName }}<template v-if="preview.sizeMb">（{{ preview.sizeMb }} MB）</template></span>
            </div>
            <div v-if="preview.apkUrl" class="mt-1 text-xs text-admin-muted">
              APK：<a :href="preview.apkUrl" target="_blank" rel="noopener" class="text-admin-accent break-all">{{ preview.apkUrl }}</a>
            </div>
            <button class="mt-3 inline-flex items-center rounded-lg bg-green-600 px-3 py-1.5 text-sm text-white transition hover:opacity-90" @click="doImport(preview)">
              <i class="fas fa-download mr-1"></i> 一键导入
            </button>
          </div>

          <!-- 模糊搜索候选（按来源分区） -->
          <div v-if="candidateGroups().length" class="mt-3 space-y-2">
            <details v-for="g in candidateGroups()" :key="g.source" :open="g.group === 'DOMESTIC'" class="rounded-lg border border-admin-border">
              <summary class="flex cursor-pointer items-center gap-2 px-3 py-2 text-sm">
                <span class="rounded bg-indigo-100 px-1.5 py-0.5 text-xs text-indigo-700">{{ g.source }}</span>
                <span class="rounded px-1.5 py-0.5 text-xs" :class="GROUP_META[g.group].badge">{{ GROUP_META[g.group].title }}</span>
                <span class="text-xs text-admin-muted">{{ g.items.length }} 个候选</span>
              </summary>
              <div class="space-y-2 px-3 pb-3">
                <div v-for="(p, i) in g.items" :key="i" class="flex items-center gap-2 rounded-lg border border-admin-border p-2">
                  <img v-if="p.iconSrc" :src="p.iconSrc" class="h-10 w-10 rounded object-cover" alt="" @error="p.iconSrc = ''" />
                  <span v-else class="flex h-10 w-10 items-center justify-center rounded bg-admin-border text-admin-muted">
                    <i class="fa fa-cube"></i>
                  </span>
                  <div class="min-w-0 flex-1">
                    <div class="truncate text-sm font-medium text-admin-text">{{ p.name || p.packageName }}</div>
                    <div class="truncate text-xs text-admin-muted"><code>{{ p.packageName }}</code><template v-if="p.latestVersionName"> · v{{ p.latestVersionName }}</template></div>
                  </div>
                  <button class="shrink-0 rounded-lg bg-green-600 px-2.5 py-1 text-xs text-white transition hover:opacity-90" @click="doImport(p)">
                    <i class="fas fa-download mr-0.5"></i> 导入
                  </button>
                </div>
              </div>
            </details>
          </div>

          <!-- 逐源探测明细 -->
          <div v-if="probeBlocks().length" class="mt-4">
            <div class="mb-1 text-xs font-semibold text-admin-text">各上游探测明细（每源单独计时，互不影响）</div>
            <div v-for="blk in probeBlocks()" :key="blk.key" class="mb-2">
              <div class="mb-1 flex items-center gap-2 text-xs">
                <span class="rounded px-1.5 py-0.5" :class="GROUP_META[blk.key].badge">{{ GROUP_META[blk.key].title }}</span>
                <span class="text-admin-muted">{{ GROUP_META[blk.key].hint }}</span>
                <span v-if="blk.key === 'OVERSEAS' && overseasQueried === false" class="text-admin-muted">（本次国内源已命中，未查询）</span>
              </div>
              <div class="space-y-1 rounded-lg border border-admin-border p-2">
                <div v-for="(pr, i) in blk.items" :key="i" class="flex flex-wrap items-center gap-x-2 text-xs">
                  <span class="flex w-16 shrink-0 items-center gap-1" :class="PROBE_CLS[pr.status || ''] || 'text-admin-muted'">
                    <i class="fas" :class="PROBE_ICON[pr.status || ''] || 'fa-circle-o'"></i>{{ PROBE_LABEL[pr.status || ''] || pr.status }}
                  </span>
                  <span class="w-24 shrink-0 font-medium text-admin-text">{{ pr.source }}</span>
                  <span class="w-14 shrink-0 text-admin-muted">{{ pr.elapsedMs != null ? pr.elapsedMs + 'ms' : '-' }}</span>
                  <span class="min-w-0 break-all text-admin-muted">{{ pr.message || pr.url || '' }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="flex justify-end gap-2 border-t border-admin-border px-5 py-3">
          <button :class="btnGhost" @click="emit('cancel')">关闭</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

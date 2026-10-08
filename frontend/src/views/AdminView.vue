<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api/client'
import type { AppVo, AppInput } from '@/types'
import AppFormModal from '@/components/admin/AppFormModal.vue'
import VersionManagerModal from '@/components/admin/VersionManagerModal.vue'
import ApkImportModal from '@/components/admin/ApkImportModal.vue'
import ExternalImportModal from '@/components/admin/ExternalImportModal.vue'
import TraceModal from '@/components/admin/TraceModal.vue'

const apps = ref<AppVo[]>([])
const keyword = ref('')
const loading = ref(false)
const alert = ref<{ type: 'ok' | 'warn' | 'error'; msg: string } | null>(null)
const iconFailed = ref<Record<string, boolean>>({})
const router = useRouter()

const formOpen = ref(false)
const editing = ref<AppVo | null>(null)
const verOpen = ref(false)
const verAppId = ref<string>('')
const apkOpen = ref(false)
const extOpen = ref(false)
// SSE 实时链路面板状态：{ open, title, kind, packageName?, source? }
const trace = ref({ open: false, title: '', kind: '', packageName: '', source: '' })

const total = ref(0)
const PAGE_SIZE = 10
const page = ref(1)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))
// 当前页附近的页码窗口（最多 5 个），避免页码过多
const pageList = computed(() => {
  const tp = totalPages.value
  const cur = page.value
  let from = Math.max(1, cur - 2)
  let to = Math.min(tp, cur + 2)
  if (to - from < 4) {
    if (from === 1) to = Math.min(tp, from + 4)
    else if (to === tp) from = Math.max(1, to - 4)
  }
  const list: number[] = []
  for (let i = from; i <= to; i++) list.push(i)
  return list
})
let timer: ReturnType<typeof setTimeout> | undefined
function onSearch() {
  clearTimeout(timer)
  timer = setTimeout(() => load(1), 250)
}
function goPage(p: number) {
  if (p === page.value) return
  load(p)
}

const alertCls = computed(() => {
  const t = alert.value?.type
  if (t === 'ok') return 'border-green-300 bg-green-50 text-green-700'
  if (t === 'warn') return 'border-yellow-300 bg-yellow-50 text-yellow-700'
  if (t === 'error') return 'border-red-300 bg-red-50 text-red-700'
  return ''
})

// 服务端分页：拉指定页（page/size/keyword），数据全在后端切片，前端只渲染当前页
function load(p = 1) {
  loading.value = true
  page.value = p
  api.admin
    .listApps({ page: p, size: PAGE_SIZE, keyword: keyword.value.trim() || undefined })
    .then((res) => {
      apps.value = res.list
      total.value = res.total
    })
    .catch((e) => show('error', (e as Error).message || '加载失败'))
    .finally(() => {
      loading.value = false
    })
}

function show(type: 'ok' | 'warn' | 'error', msg: string) {
  alert.value = { type, msg }
  setTimeout(() => (alert.value = null), 4000)
}

function openNew() {
  editing.value = null
  formOpen.value = true
}
function openEdit(a: AppVo) {
  editing.value = a
  formOpen.value = true
}
async function onSave(v: AppInput) {
  try {
    if (editing.value) await api.admin.updateApp(editing.value.id, v)
    else await api.admin.createApp(v)
    formOpen.value = false
    show('ok', editing.value ? '已更新' : '已新增')
    await load()
  } catch (e) {
    show('error', (e as Error).message || '保存失败')
  }
}
async function delApp(a: AppVo) {
  if (!confirm(`确定删除应用「${a.name}」吗？其下所有版本与下载源将一并删除，且不可恢复。`)) return
  try {
    await api.admin.deleteApp(a.id)
    show('ok', '已删除')
    await load()
  } catch (e) {
    show('error', (e as Error).message || '删除失败')
  }
}
function openVer(a: AppVo) {
  verAppId.value = a.id
  verOpen.value = true
}
async function onCollect() {
  try {
    const r = await api.admin.collect()
    show(
      'ok',
      `采集完成：新增应用 ${r.appsCreated ?? 0}，版本 ${r.versionsAdded ?? 0}，下载源 ${r.sourcesAdded ?? 0}`
    )
    await load()
  } catch (e) {
    show('error', (e as Error).message || '采集失败')
  }
}
function onApkImported() {
  show('ok', 'APK 导入成功')
  load()
}

// 外部资源导入：来自 ExternalImportModal 的点选，启动带 SSE 链路的导入任务
function onImportExternal(p: { packageName: string; source: string }) {
  trace.value = {
    open: true,
    title: '导入外部资源 · ' + (p.packageName || ''),
    kind: 'external-import',
    packageName: p.packageName,
    source: p.source
  }
}

// 批量补全应用宝元数据：逐个应用「补链接 → 抓元数据」，结果实时出现在链路面板
function startBatchComplete() {
  trace.value = {
    open: true,
    title: '批量补全应用宝元数据',
    kind: 'batch-complete',
    packageName: '',
    source: ''
  }
}

// SSE 链路结束：按任务类型回写结果提示并刷新列表
function onTraceFinished(payload: { status: string; message?: string; kind?: string }) {
  if (payload.status === 'DONE') {
    show('ok', payload.kind === 'batch-complete' ? '应用宝元数据补全完成' : '外部资源导入成功')
  } else {
    show('error', (payload.message || '导入链路异常'))
  }
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
      <div>
        <p class="text-sm text-admin-muted">// ADMIN CONSOLE</p>
        <h1 class="font-display text-2xl font-bold text-admin-text">应用市场 · 管理后台</h1>
      </div>
      <div class="flex gap-2">
        <button
          class="rounded-lg border border-admin-border px-3 py-1.5 text-sm text-admin-muted transition hover:text-admin-text"
          @click="load()"
        >
          <i class="fas fa-sync"></i> 刷新
        </button>
        <button
          class="rounded-lg border border-admin-border px-3 py-1.5 text-sm text-admin-muted transition hover:text-admin-text"
          @click="onCollect"
        >
          <i class="fas fa-cloud-download-alt"></i> 从 GitHub 采集开源应用
        </button>
        <button
          class="rounded-lg bg-admin-accent px-3 py-1.5 text-sm text-white transition hover:opacity-90"
          @click="openNew"
        >
          <i class="fas fa-plus"></i> 新增应用
        </button>
        <button
          class="rounded-lg border border-admin-border px-3 py-1.5 text-sm text-admin-muted transition hover:text-admin-text"
          @click="apkOpen = true"
        >
          <i class="fas fa-android"></i> 从 APK 导入
        </button>
        <button
          class="rounded-lg border border-admin-border px-3 py-1.5 text-sm text-admin-muted transition hover:text-admin-text"
          @click="router.push('/admin/apk')"
        >
          <i class="fas fa-search-plus"></i> APK 分析
        </button>
        <button
          class="rounded-lg border border-admin-border px-3 py-1.5 text-sm text-admin-muted transition hover:text-admin-text"
          @click="extOpen = true"
        >
          <i class="fas fa-globe"></i> 导入外部资源
        </button>
        <button
          class="rounded-lg border border-admin-border px-3 py-1.5 text-sm text-admin-muted transition hover:text-admin-text"
          @click="startBatchComplete"
        >
          <i class="fas fa-magic"></i> 联网补全应用宝元数据
        </button>
      </div>
    </div>

    <div
      v-if="alert"
      class="mb-3 rounded-lg border px-3 py-2 text-sm"
      :class="alertCls"
    >
      {{ alert.msg }}
    </div>

    <div class="mb-3 max-w-sm">
      <input
        v-model="keyword"
        type="search"
        @input="onSearch"
        placeholder="搜索名称 / 包名 / 开发者 / 分类"
        class="w-full rounded-lg border border-admin-border bg-admin-surface px-3 py-2 text-sm text-admin-text outline-none focus:border-admin-accent"
      />
    </div>

    <div class="overflow-hidden rounded-2xl border border-admin-border bg-admin-surface">
      <table class="w-full text-left text-sm">
        <thead class="bg-admin-bg text-xs uppercase text-admin-muted">
          <tr>
            <th class="px-3 py-2 font-medium" style="width: 56px">ID</th>
            <th class="px-3 py-2 font-medium">应用</th>
            <th class="px-3 py-2 font-medium">包名</th>
            <th class="px-3 py-2 font-medium" style="width: 90px">分类</th>
            <th class="px-3 py-2 font-medium">开发者</th>
            <th class="px-3 py-2 font-medium" style="width: 72px">版本数</th>
            <th class="px-3 py-2 font-medium" style="width: 240px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7" class="px-3 py-8 text-center text-admin-muted">加载中…</td>
          </tr>
          <tr v-else-if="!total">
            <td colspan="7" class="px-3 py-8 text-center text-admin-muted">
              暂无应用，点击右上角「新增应用」或「从 GitHub 采集开源应用」。
            </td>
          </tr>
          <tr
            v-for="a in apps"
            :key="a.id"
            class="border-t border-admin-border hover:bg-admin-bg/60"
          >
            <td class="px-3 py-2 text-admin-muted">{{ a.id }}</td>
            <td class="px-3 py-2">
              <div class="flex items-center gap-2">
                <img
                  v-if="a.iconUrl && !iconFailed[a.id]"
                  :src="a.iconUrl"
                  :alt="a.name"
                  class="h-8 w-8 rounded object-cover"
                  @error="iconFailed[a.id] = true"
                />
                <span
                  v-else
                  class="flex h-8 w-8 items-center justify-center rounded bg-admin-border text-admin-muted"
                >
                  <i class="fa fa-cube"></i>
                </span>
                <span class="font-medium text-admin-text">{{ a.name }}</span>
              </div>
            </td>
            <td class="px-3 py-2"><code class="text-xs text-admin-muted">{{ a.packageName || '-' }}</code></td>
            <td class="px-3 py-2 text-admin-muted">{{ a.category || '-' }}</td>
            <td class="px-3 py-2 text-admin-muted">{{ a.developer || '-' }}</td>
            <td class="px-3 py-2 text-admin-muted">{{ a.versionCount || 0 }}</td>
            <td class="px-3 py-2">
              <div class="flex flex-wrap gap-1.5">
                <button
                  class="rounded-md border border-admin-border px-2 py-1 text-xs text-admin-muted transition hover:text-admin-text"
                  @click="openVer(a)"
                >
                  版本/下载源
                </button>
                <button
                  class="rounded-md border border-admin-border px-2 py-1 text-xs text-admin-muted transition hover:text-admin-text"
                  @click="openEdit(a)"
                >
                  编辑
                </button>
                <button
                  class="rounded-md border border-admin-border px-2 py-1 text-xs text-admin-muted transition hover:text-red-500"
                  @click="delApp(a)"
                >
                  删除
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页器：简洁方便，不滚动加载 -->
    <div
      v-if="totalPages > 1"
      class="mt-3 flex flex-wrap items-center justify-between gap-3 text-sm text-admin-muted"
    >
      <span>共 {{ total }} 条 · 第 {{ page }} / {{ totalPages }} 页</span>
      <div class="flex items-center gap-1">
        <button
          class="rounded-md border border-admin-border px-2.5 py-1 transition hover:text-admin-text disabled:cursor-not-allowed disabled:opacity-40"
          :disabled="page <= 1"
          @click="goPage(page - 1)"
        >
          上一页
        </button>
        <button
          v-for="p in pageList"
          :key="p"
          class="min-w-[32px] rounded-md border px-2.5 py-1 transition"
          :class="
            p === page
              ? 'border-admin-accent bg-admin-accent text-white'
              : 'border-admin-border hover:text-admin-text'
          "
          @click="goPage(p)"
        >
          {{ p }}
        </button>
        <button
          class="rounded-md border border-admin-border px-2.5 py-1 transition hover:text-admin-text disabled:cursor-not-allowed disabled:opacity-40"
          :disabled="page >= totalPages"
          @click="goPage(page + 1)"
        >
          下一页
        </button>
      </div>
    </div>

    <AppFormModal :open="formOpen" :app="editing" @cancel="formOpen = false" @save="onSave" />
    <VersionManagerModal
      :open="verOpen"
      :app-id="verAppId"
      @cancel="verOpen = false"
      @changed="load"
    />
    <ApkImportModal :open="apkOpen" @cancel="apkOpen = false" @imported="onApkImported" />
    <ExternalImportModal :open="extOpen" @cancel="extOpen = false" @import-external="onImportExternal" />
    <TraceModal
      :open="trace.open"
      :title="trace.title"
      :kind="trace.kind"
      :package-name="trace.packageName"
      :source="trace.source"
      @cancel="trace.open = false"
      @finished="onTraceFinished"
    />
  </div>
</template>

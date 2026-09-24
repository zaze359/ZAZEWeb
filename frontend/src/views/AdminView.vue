<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { api } from '@/api/client'
import type { AppVo, AppInput } from '@/types'
import AppFormModal from '@/components/admin/AppFormModal.vue'
import VersionManagerModal from '@/components/admin/VersionManagerModal.vue'
import ApkImportModal from '@/components/admin/ApkImportModal.vue'

const apps = ref<AppVo[]>([])
const keyword = ref('')
const loading = ref(false)
const alert = ref<{ type: 'ok' | 'warn' | 'error'; msg: string } | null>(null)
const iconFailed = ref<Record<string, boolean>>({})

const formOpen = ref(false)
const editing = ref<AppVo | null>(null)
const verOpen = ref(false)
const verAppId = ref<string>('')
const apkOpen = ref(false)

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return apps.value
  return apps.value.filter((a) =>
    [a.name, a.packageName, a.developer, a.category].some((f) => f && String(f).toLowerCase().includes(kw))
  )
})

const alertCls = computed(() => {
  const t = alert.value?.type
  if (t === 'ok') return 'border-green-300 bg-green-50 text-green-700'
  if (t === 'warn') return 'border-yellow-300 bg-yellow-50 text-yellow-700'
  if (t === 'error') return 'border-red-300 bg-red-50 text-red-700'
  return ''
})

async function load() {
  loading.value = true
  try {
    apps.value = await api.admin.listApps()
  } catch (e) {
    show('error', (e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
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
          @click="load"
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
          <tr v-else-if="!filtered.length">
            <td colspan="7" class="px-3 py-8 text-center text-admin-muted">
              暂无应用，点击右上角「新增应用」或「从 GitHub 采集开源应用」。
            </td>
          </tr>
          <tr
            v-for="a in filtered"
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

    <AppFormModal :open="formOpen" :app="editing" @cancel="formOpen = false" @save="onSave" />
    <VersionManagerModal
      :open="verOpen"
      :app-id="verAppId"
      @cancel="verOpen = false"
      @changed="load"
    />
    <ApkImportModal :open="apkOpen" @cancel="apkOpen = false" @imported="onApkImported" />
  </div>
</template>

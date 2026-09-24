<script setup lang="ts">
import { ref, watch } from 'vue'
import { api } from '@/api/client'
import type { AppVo, AppVersion, VersionInput, SourceInput } from '@/types'
import SourceTypeBadge from './SourceTypeBadge.vue'

const props = defineProps<{ open: boolean; appId: string }>()
const emit = defineEmits<{ (e: 'cancel'): void; (e: 'changed'): void }>()

const app = ref<AppVo | null>(null)
const loading = ref(false)
const verForm = ref<VersionInput>(blankVer())
const sourceForm = ref<SourceInput>(blankSrc())
const activeVersionId = ref<string | null>(null)
const SOURCE_TYPES = ['GITHUB', 'FDROID', 'IZZY', 'APKMIRROR', 'OFFICIAL', 'MYAPP', 'OTHER']

const inputCls =
  'w-full rounded-lg border border-admin-border bg-admin-surface px-2.5 py-1.5 text-sm text-admin-text outline-none focus:border-admin-accent'
const btnGhost =
  'rounded-lg border border-admin-border px-2.5 py-1 text-sm text-admin-muted transition hover:text-admin-text'
const btnPrimary =
  'rounded-lg bg-admin-accent px-2.5 py-1 text-sm text-white transition hover:opacity-90 disabled:opacity-50'

function blankVer(): VersionInput {
  return { versionName: '', versionCode: null, releaseDate: null, sizeMb: null, changelog: '' }
}
function blankSrc(): SourceInput {
  return { sourceName: '', sourceType: 'GITHUB', downloadUrl: '', region: '', note: '' }
}

watch(
  () => props.open,
  async (o) => {
    if (!o) return
    loading.value = true
    try {
      app.value = await api.admin.getApp(props.appId)
    } finally {
      loading.value = false
    }
    verForm.value = blankVer()
    sourceForm.value = blankSrc()
    activeVersionId.value = null
  }
)

function fmtDate(ms?: number): string {
  if (!ms) return ''
  const d = new Date(ms)
  return `${d.getFullYear()}-${('0' + (d.getMonth() + 1)).slice(-2)}-${('0' + d.getDate()).slice(-2)}`
}

async function saveVersion() {
  if (!verForm.value.versionName.trim()) return
  await api.admin.createVersion(props.appId, {
    versionName: verForm.value.versionName.trim(),
    versionCode: verForm.value.versionCode || null,
    releaseDate: verForm.value.releaseDate || null,
    sizeMb: verForm.value.sizeMb || null,
    changelog: verForm.value.changelog
  })
  verForm.value = blankVer()
  app.value = await api.admin.getApp(props.appId)
  emit('changed')
}

async function delVersion(id: string) {
  if (!confirm('确定删除该版本吗？其下所有下载源将一并删除。')) return
  await api.admin.deleteVersion(id)
  app.value = await api.admin.getApp(props.appId)
  emit('changed')
}

function openSource(v: AppVersion) {
  activeVersionId.value = v.id || null
  sourceForm.value = blankSrc()
}

async function saveSource() {
  if (!activeVersionId.value || !sourceForm.value.downloadUrl.trim()) return
  await api.admin.createSource(activeVersionId.value, {
    sourceName: sourceForm.value.sourceName,
    sourceType: sourceForm.value.sourceType,
    downloadUrl: sourceForm.value.downloadUrl.trim(),
    region: sourceForm.value.region,
    note: sourceForm.value.note
  })
  sourceForm.value = blankSrc()
  activeVersionId.value = null
  app.value = await api.admin.getApp(props.appId)
  emit('changed')
}

async function delSource(id: string) {
  if (!confirm('确定删除该下载源吗？')) return
  await api.admin.deleteSource(id)
  app.value = await api.admin.getApp(props.appId)
  emit('changed')
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 p-4"
      @click.self="emit('cancel')"
    >
      <div class="flex max-h-[85vh] w-full max-w-2xl flex-col rounded-2xl border border-admin-border bg-admin-surface shadow-xl">
        <div class="flex items-center justify-between border-b border-admin-border px-5 py-3">
          <h3 class="font-display text-base font-semibold text-admin-text">
            版本与下载源 · <span>{{ app?.name }}</span>
          </h3>
          <button :class="btnGhost" @click="emit('cancel')">关闭</button>
        </div>

        <div class="overflow-y-auto px-5 py-4">
          <div v-if="loading" class="py-8 text-center text-admin-muted">加载中…</div>
          <div v-else-if="!app" class="py-8 text-center text-admin-muted">未找到应用</div>

          <template v-else>
            <!-- 新增版本 -->
            <div class="mb-4 rounded-xl border border-admin-border p-3">
              <div class="mb-2 text-sm font-medium text-admin-text">新增版本</div>
              <div class="grid grid-cols-2 gap-2 sm:grid-cols-4">
                <input v-model="verForm.versionName" :class="inputCls" placeholder="版本名 *" />
                <input v-model="verForm.versionCode" :class="inputCls" type="number" placeholder="版本号" />
                <input v-model="verForm.releaseDate" :class="inputCls" type="date" placeholder="发布日期" />
                <input v-model="verForm.sizeMb" :class="inputCls" type="number" placeholder="体积(MB)" />
              </div>
              <input v-model="verForm.changelog" :class="[inputCls, 'mt-2']" placeholder="更新说明" />
              <button :class="[btnPrimary, 'mt-2']" :disabled="!verForm.versionName.trim()" @click="saveVersion">
                保存版本
              </button>
            </div>

            <!-- 版本列表 -->
            <div v-if="!app.versions?.length" class="py-4 text-center text-sm text-admin-muted">
              暂无版本，点击上方「保存版本」添加。
            </div>
            <div v-for="v in app.versions" :key="v.id" class="mb-3 rounded-xl border border-admin-border p-3">
              <div class="flex items-center justify-between">
                <div>
                  <strong class="text-admin-text">v{{ v.versionName }}</strong>
                  <span class="ml-2 text-xs text-admin-muted">
                    {{ fmtDate(v.releaseDate) }}
                    <template v-if="v.sizeMb"> · {{ v.sizeMb }} MB</template>
                    · {{ v.sourceCount || 0 }} 来源
                  </span>
                </div>
                <div class="flex gap-2">
                  <button :class="btnGhost" @click="openSource(v)">下载源</button>
                  <button :class="btnGhost" @click="delVersion(v.id!)">删除</button>
                </div>
              </div>
              <p v-if="v.changelog" class="mt-1 text-xs text-admin-muted">{{ v.changelog }}</p>

              <div class="mt-2 space-y-1">
                <div v-for="s in v.sources" :key="s.id" class="flex items-center gap-2 text-sm">
                  <a :href="s.downloadUrl" target="_blank" rel="noopener" class="text-admin-accent hover:underline">
                    {{ s.sourceName || s.sourceType || '下载' }}
                  </a>
                  <SourceTypeBadge :type="s.sourceType" />
                  <span v-if="s.region" class="text-xs text-admin-muted">{{ s.region }}</span>
                  <button class="ml-auto text-xs text-admin-muted hover:text-red-500" @click="delSource(s.id!)">删除</button>
                </div>
                <div v-if="!v.sources?.length" class="text-xs text-admin-muted">暂无下载源</div>
              </div>

              <!-- 新增下载源 -->
              <div v-if="activeVersionId === v.id" class="mt-3 rounded-lg border border-admin-border p-2">
                <div class="mb-1 text-xs font-medium text-admin-text">新增下载源 · v{{ v.versionName }}</div>
                <div class="grid grid-cols-2 gap-2 sm:grid-cols-3">
                  <input v-model="sourceForm.sourceName" :class="inputCls" placeholder="来源名称" />
                  <select v-model="sourceForm.sourceType" :class="inputCls">
                    <option v-for="t in SOURCE_TYPES" :key="t" :value="t">{{ t }}</option>
                  </select>
                  <input v-model="sourceForm.region" :class="inputCls" placeholder="地区" />
                </div>
                <input v-model="sourceForm.downloadUrl" :class="[inputCls, 'mt-2']" placeholder="下载地址 *" />
                <button
                  :class="[btnPrimary, 'mt-2']"
                  :disabled="!sourceForm.downloadUrl.trim()"
                  @click="saveSource"
                >
                  保存下载源
                </button>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </Teleport>
</template>

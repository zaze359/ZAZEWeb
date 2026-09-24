<script setup lang="ts">
import { ref, watch } from 'vue'
import type { AppVo, AppInput } from '@/types'

const props = defineProps<{ open: boolean; app: AppVo | null }>()
const emit = defineEmits<{ (e: 'cancel'): void; (e: 'save', v: AppInput): void }>()

const form = ref<AppInput>(blank())
function blank(): AppInput {
  return { name: '', packageName: '', category: '', developer: '', iconUrl: '', officialUrl: '', summary: '' }
}

watch(
  () => props.open,
  (o) => {
    if (!o) return
    const a = props.app
    form.value = a
      ? {
          name: a.name,
          packageName: a.packageName || '',
          category: a.category || '',
          developer: a.developer || '',
          iconUrl: a.iconUrl || '',
          officialUrl: a.officialUrl || '',
          summary: a.summary || ''
        }
      : blank()
  }
)

const inputCls =
  'w-full rounded-lg border border-admin-border bg-admin-surface px-3 py-2 text-sm text-admin-text outline-none focus:border-admin-accent'
const btnGhost =
  'rounded-lg border border-admin-border px-3 py-1.5 text-sm text-admin-muted transition hover:text-admin-text'
const btnPrimary =
  'rounded-lg bg-admin-accent px-3 py-1.5 text-sm text-white transition hover:opacity-90 disabled:opacity-50'
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 p-4"
      @click.self="emit('cancel')"
    >
      <div class="w-full max-w-md rounded-2xl border border-admin-border bg-admin-surface p-5 shadow-xl">
        <h3 class="mb-4 font-display text-lg font-semibold text-admin-text">
          {{ app ? '编辑应用' : '新增应用' }}
        </h3>
        <div class="space-y-3">
          <div>
            <label class="mb-1 block text-sm text-admin-muted">应用名称 *</label>
            <input v-model="form.name" :class="inputCls" placeholder="如 NewPipe" />
          </div>
          <div>
            <label class="mb-1 block text-sm text-admin-muted">包名</label>
            <input v-model="form.packageName" :class="inputCls" placeholder="如 org.schabi.newpipe" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="mb-1 block text-sm text-admin-muted">分类</label>
              <input v-model="form.category" :class="inputCls" placeholder="影音播放" />
            </div>
            <div>
              <label class="mb-1 block text-sm text-admin-muted">开发者</label>
              <input v-model="form.developer" :class="inputCls" />
            </div>
          </div>
          <div>
            <label class="mb-1 block text-sm text-admin-muted">图标地址</label>
            <input v-model="form.iconUrl" :class="inputCls" placeholder="http://" />
          </div>
          <div>
            <label class="mb-1 block text-sm text-admin-muted">官网地址</label>
            <input v-model="form.officialUrl" :class="inputCls" placeholder="http://" />
          </div>
          <div>
            <label class="mb-1 block text-sm text-admin-muted">简介</label>
            <textarea v-model="form.summary" rows="2" :class="inputCls"></textarea>
          </div>
        </div>
        <div class="mt-5 flex justify-end gap-2">
          <button :class="btnGhost" @click="emit('cancel')">取消</button>
          <button
            :class="btnPrimary"
            :disabled="!form.name.trim()"
            @click="emit('save', { ...form, name: form.name.trim() })"
          >
            保存
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

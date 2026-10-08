<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ApkAnalysis, ApkSignerCert } from '@/utils/apk'

const props = defineProps<{ analysis: ApkAnalysis; variant?: 'compact' | 'full' }>()
const variant = computed(() => props.variant || 'full')
const sig = computed(() => props.analysis.signature)
const copied = ref<string | null>(null)

const schemeList = computed(() => {
  const s = sig.value?.schemes
  if (!s) return []
  return [
    { key: 'v1', on: s.v1 },
    { key: 'v2', on: s.v2 },
    { key: 'v3', on: s.v3 },
    { key: 'v3.1', on: s.v3_1 }
  ]
})

const schemeSummary = computed(() => {
  const s = sig.value?.schemes
  if (!s) return ''
  const present = ['v1', 'v2', 'v3', 'v3.1'].filter((k) => (s as Record<string, boolean>)[k])
  if (!present.length) return ''
  if (present.length === 1 && present[0] === 'v1') {
    return '仅 v1：Android 7+ 要求 v2，部分场景可能装不上'
  }
  return present.join(' + ')
})

function shortSha(sha?: string): string {
  if (!sha) return ''
  const parts = sha.split(':')
  if (parts.length <= 8) return sha
  return parts.slice(0, 8).join(':') + ':…'
}

async function copy(text: string, id: string) {
  try {
    await navigator.clipboard.writeText(text)
    copied.value = id
    setTimeout(() => {
      if (copied.value === id) copied.value = null
    }, 2000)
  } catch {
    /* 剪贴板不可用时静默 */
  }
}
</script>

<template>
  <section class="rounded-xl border border-admin-border bg-admin-surface p-4">
    <div class="mb-3 flex items-center gap-2">
      <i class="fas fa-signature text-admin-accent"></i>
      <h3 class="text-sm font-semibold text-admin-text">签名与完整性</h3>
    </div>

    <!-- 无签名信息 -->
    <div
      v-if="!sig || (!sig.schemes.v1 && !sig.schemes.v2 && !sig.schemes.v3 && !sig.schemes.v3_1)"
      class="rounded-lg border border-dashed border-admin-border bg-admin-bg p-3 text-center text-xs text-admin-muted"
    >
      未检测到签名信息（可能是自定义打包或异常包，非错误）
    </div>

    <template v-else>
      <!-- 方案徽章 -->
      <div class="flex flex-wrap gap-1.5 text-[11px]">
        <span
          v-for="sc in schemeList"
          :key="sc.key"
          class="rounded-full px-2 py-0.5"
          :class="sc.on ? 'bg-admin-accent text-white' : 'border border-admin-border text-admin-muted'"
          >{{ sc.key }}</span
        >
      </div>
      <p v-if="schemeSummary" class="mt-1.5 text-[11px] text-admin-muted">{{ schemeSummary }}</p>

      <!-- 降级提示 -->
      <div
        v-if="sig.degraded.length"
        class="mt-2 rounded-lg border border-yellow-300 bg-yellow-50 p-2 text-[11px] text-yellow-700"
      >
        <span v-for="(d, i) in sig.degraded" :key="i">{{ d }}<br /></span>
      </div>

      <!-- 证书区 -->
      <div class="mt-3 space-y-2">
        <div
          v-for="(c, i) in sig.certs"
          :key="i"
          class="rounded-lg border border-admin-border p-2 text-[11px]"
        >
          <div class="flex flex-wrap items-center gap-1.5">
            <span class="font-semibold text-admin-text">{{ c.subjectCN || c.subject }}</span>
            <span v-if="c.debugCert" class="rounded bg-amber-50 px-1.5 py-0.5 text-amber-600">调试证书</span>
            <span v-if="c.selfSigned" class="rounded bg-gray-100 px-1.5 py-0.5 text-gray-500">自签名</span>
          </div>
          <div class="mt-0.5 text-admin-muted">签发者：{{ c.issuer }}</div>
          <div v-if="c.notBefore || c.notAfter" class="text-admin-muted">
            有效期：{{ c.notBefore || '?' }} → {{ c.notAfter || '?' }}
          </div>
          <div v-if="c.signatureAlgorithm" class="text-admin-muted">签名算法：{{ c.signatureAlgorithm }}</div>

          <!-- 指纹 -->
          <div v-if="c.sha256" class="mt-1">
            <div class="flex items-start gap-1.5">
              <span class="shrink-0 text-admin-muted">SHA-256</span>
              <code class="break-all font-mono text-admin-text">{{ variant === 'compact' ? shortSha(c.sha256) : c.sha256 }}</code>
              <button
                v-if="variant === 'full'"
                class="shrink-0 text-admin-accent"
                :title="copied === 'sha256-' + i ? '已复制' : '复制'"
                @click="copy(c.sha256!, 'sha256-' + i)"
              >
                <i class="fas" :class="copied === 'sha256-' + i ? 'fa-check' : 'fa-copy'"></i>
              </button>
            </div>
            <div v-if="variant === 'full' && c.sha1" class="mt-0.5 flex items-start gap-1.5">
              <span class="shrink-0 text-admin-muted">SHA-1</span>
              <code class="break-all font-mono text-admin-text">{{ c.sha1 }}</code>
              <button
                class="shrink-0 text-admin-accent"
                :title="copied === 'sha1-' + i ? '已复制' : '复制'"
                @click="copy(c.sha1!, 'sha1-' + i)"
              >
                <i class="fas" :class="copied === 'sha1-' + i ? 'fa-check' : 'fa-copy'"></i>
              </button>
            </div>
          </div>
          <div v-else-if="c.fingerprintError" class="mt-1 text-admin-muted">
            <i class="fas fa-info-circle"></i> {{ c.fingerprintError }}
          </div>
        </div>
      </div>

      <!-- 固定脚注（R5：诚实标注） -->
      <p class="mt-3 text-[11px] text-admin-muted">
        <i class="fas fa-exclamation-circle text-red-500"></i>
        仅检测签名方案与证书，<span class="text-red-600">未校验签名有效性</span>。
      </p>
      <p v-if="variant === 'full'" class="mt-1 text-[11px] text-admin-muted">
        不对 APK 内容做 digest 比对，也不校验证书链与信任锚；请勿据此判定「签名通过」。
      </p>
    </template>
  </section>
</template>

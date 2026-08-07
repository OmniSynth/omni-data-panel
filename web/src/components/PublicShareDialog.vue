<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTime } from '@/display'
import type { PublicLink, PublicLinkExpireDays } from '@/types'

const visible = defineModel<boolean>({ required: true })
defineProps<{
  resourceName?: string
  hint: string
  emptyText: string
  links: PublicLink[]
  loading?: boolean
  creating?: boolean
  urlFor: (link: PublicLink) => string
}>()
const emit = defineEmits<{
  create: [expiresInDays: PublicLinkExpireDays]
  copy: [link: PublicLink]
  revoke: [link: PublicLink]
}>()

const { t } = useI18n()
/** 0 表示永不过期（避免 el-select 对 null 的兼容问题） */
const expiresInDays = ref<0 | 1 | 7 | 30 | 90>(0)

const expireOptions: { value: 0 | 1 | 7 | 30 | 90; labelKey: string }[] = [
  { value: 0, labelKey: 'dashboard.expireNever' },
  { value: 1, labelKey: 'dashboard.expireDays' },
  { value: 7, labelKey: 'dashboard.expireDays' },
  { value: 30, labelKey: 'dashboard.expireDays' },
  { value: 90, labelKey: 'dashboard.expireDays' },
]

watch(visible, (open) => {
  if (open) expiresInDays.value = 0
})

function emitCreate() {
  emit('create', expiresInDays.value === 0 ? null : expiresInDays.value)
}

function isExpired(link: PublicLink) {
  if (!link.expiresAt) return false
  const ts = Date.parse(link.expiresAt)
  return Number.isFinite(ts) && ts <= Date.now()
}

function expireLabel(link: PublicLink) {
  if (!link.expiresAt) return t('dashboard.expireNever')
  if (isExpired(link)) return t('dashboard.expired')
  return t('dashboard.expiresAt', { time: formatDateTime(link.expiresAt) })
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="t('dashboard.publicShare')"
    width="520px"
    destroy-on-close
    append-to-body
    class="public-share-dialog"
  >
    <p v-if="resourceName" class="resource-name">{{ resourceName }}</p>
    <p class="hint">{{ hint }}</p>

    <div class="expire-row">
      <span class="expire-label">{{ t('dashboard.linkExpire') }}</span>
      <el-select v-model="expiresInDays" style="width: 180px">
        <el-option
          v-for="opt in expireOptions"
          :key="opt.value"
          :label="opt.value === 0 ? t(opt.labelKey) : t(opt.labelKey, { n: opt.value })"
          :value="opt.value"
        />
      </el-select>
    </div>

    <div v-loading="loading" class="body">
      <div v-if="!loading && !links.length" class="empty">
        <p class="empty-text">{{ emptyText }}</p>
        <el-button type="primary" :loading="creating" @click="emitCreate">
          {{ t('dashboard.createPublicLink') }}
        </el-button>
      </div>

      <div v-else class="link-list">
        <div v-for="link in links" :key="link.token" class="link-card">
          <el-input :model-value="urlFor(link)" readonly>
            <template #append>
              <el-button @click="emit('copy', link)">{{ t('dashboard.copyLink') }}</el-button>
            </template>
          </el-input>
          <div class="link-meta">
            <span class="expire-meta" :class="{ expired: isExpired(link) }">{{ expireLabel(link) }}</span>
            <div class="link-actions">
              <a class="open-link" :href="urlFor(link)" target="_blank" rel="noreferrer">
                {{ t('dashboard.openInNewTab') }}
              </a>
              <el-button link type="danger" @click="emit('revoke', link)">
                {{ t('dashboard.revoke') }}
              </el-button>
            </div>
          </div>
        </div>
        <el-button class="add-another" plain :loading="creating" @click="emitCreate">
          {{ t('dashboard.createAnotherLink') }}
        </el-button>
      </div>
    </div>

    <template #footer>
      <el-button type="primary" @click="visible = false">{{ t('common.done') }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.resource-name {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--omni-text);
}
.hint {
  margin: 0 0 16px;
  color: var(--omni-muted);
  font-size: 13px;
  line-height: 1.5;
}
.expire-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.expire-label {
  font-size: 13px;
  color: var(--omni-text);
  white-space: nowrap;
}
.body { min-height: 96px; }
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 28px 16px;
  border: 1px dashed var(--omni-border);
  border-radius: var(--omni-radius);
  background: var(--omni-surface);
}
.empty-text {
  margin: 0;
  color: var(--omni-muted);
  font-size: 13px;
}
.link-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.link-card {
  padding: 12px;
  border: 1px solid var(--omni-border);
  border-radius: var(--omni-radius);
  background: var(--omni-surface);
}
.link-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
  flex-wrap: wrap;
}
.expire-meta {
  font-size: 12px;
  color: var(--omni-muted);
}
.expire-meta.expired {
  color: var(--el-color-danger);
}
.link-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.open-link {
  font-size: 13px;
  color: var(--omni-accent);
  text-decoration: none;
}
.open-link:hover { text-decoration: underline; }
.add-another {
  align-self: flex-start;
}
</style>

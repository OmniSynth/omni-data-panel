<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { PublicLink } from '@/types'

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
  create: []
  copy: [link: PublicLink]
  revoke: [link: PublicLink]
}>()

const { t } = useI18n()
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

    <div v-loading="loading" class="body">
      <div v-if="!loading && !links.length" class="empty">
        <p class="empty-text">{{ emptyText }}</p>
        <el-button type="primary" :loading="creating" @click="emit('create')">
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
            <a class="open-link" :href="urlFor(link)" target="_blank" rel="noreferrer">
              {{ t('dashboard.openInNewTab') }}
            </a>
            <el-button link type="danger" @click="emit('revoke', link)">
              {{ t('dashboard.revoke') }}
            </el-button>
          </div>
        </div>
        <el-button class="add-another" plain :loading="creating" @click="emit('create')">
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

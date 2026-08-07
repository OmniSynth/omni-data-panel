<script setup lang="ts">
import { useI18n } from 'vue-i18n'

withDefaults(defineProps<{
  title?: string
  error?: string
  loading?: boolean
  showRefresh?: boolean
}>(), {
  title: '',
  error: '',
  loading: false,
  showRefresh: false,
})

defineEmits<{
  refresh: []
}>()

const { t } = useI18n()
</script>

<template>
  <article class="dashboard-card-shell">
    <header class="card-chrome">
      <h3 class="card-title" :title="title">{{ title }}</h3>
      <el-button
        v-if="showRefresh"
        class="card-refresh no-export"
        link
        :disabled="loading"
        @click.stop="$emit('refresh')"
      >
        {{ t('dashboard.refresh') }}
      </el-button>
    </header>
    <div class="card-body">
      <el-skeleton v-if="loading" class="card-skeleton" animated>
        <template #template>
          <el-skeleton-item variant="text" class="skel-line" />
          <el-skeleton-item variant="rect" class="skel-chart" />
        </template>
      </el-skeleton>
      <el-alert
        v-else-if="error"
        class="card-error"
        :title="error"
        type="error"
        :closable="false"
        show-icon
      />
      <div v-else class="card-content">
        <slot />
      </div>
    </div>
  </article>
</template>

<style scoped>
.dashboard-card-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: var(--omni-card);
  border: 1px solid var(--omni-border);
  border-radius: var(--omni-radius);
  box-shadow: var(--omni-shadow);
}
.card-chrome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-shrink: 0;
  min-height: 40px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--omni-border);
  background: var(--omni-surface);
}
.card-title {
  margin: 0;
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
  color: var(--omni-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-refresh {
  flex-shrink: 0;
  margin: 0;
}
.card-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 10px 12px 12px;
  overflow: hidden;
}
.card-content,
.card-skeleton,
.card-error {
  flex: 1;
  min-height: 0;
}
.card-content {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.card-content > :deep(*) {
  flex: 1;
  min-height: 0;
}
.card-skeleton {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}
.skel-line {
  width: 42%;
  height: 14px;
}
.skel-chart {
  flex: 1;
  width: 100%;
  min-height: 72px;
  border-radius: var(--omni-radius-sm);
}
</style>

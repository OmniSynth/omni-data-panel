<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { dataSourceHealthApi } from '@/api'
import { formatDateTime } from '@/display'
import type { DataSourceHealthItem, DataSourceHealthOverview, DataSourceHealthStatus } from '@/types'

const REFRESH_MS = 10_000

const { t } = useI18n()
const loading = ref(false)
const autoRefresh = ref(true)
const overview = ref<DataSourceHealthOverview>()
const lastError = ref('')
let timer: ReturnType<typeof setInterval> | undefined

const checkedLabel = computed(() => {
  const raw = overview.value?.checkedAt
  return raw ? formatDateTime(raw) : t('common.emptyDash')
})

const cards = computed(() => {
  const data = overview.value
  if (!data) return []
  return [
    { key: 'up', label: t('health.ok'), value: data.up, tone: 'ok' },
    { key: 'degraded', label: t('health.degraded'), value: data.degraded, tone: 'warn' },
    { key: 'down', label: t('health.down'), value: data.down, tone: 'danger' },
    { key: 'cold', label: t('health.noPool'), value: data.cold, tone: 'cold' },
    { key: 'total', label: t('health.dataSource'), value: data.total, tone: 'neutral' },
  ]
})

function healthLabel(status: DataSourceHealthStatus) {
  switch (status) {
    case 'UP': return t('health.ok')
    case 'DEGRADED': return t('health.degraded')
    case 'DOWN': return t('health.down')
    case 'COLD': return t('health.noPool')
    case 'DISABLED': return t('health.disabled')
    default: return status
  }
}

function healthType(status: DataSourceHealthStatus) {
  switch (status) {
    case 'UP': return 'success'
    case 'DEGRADED': return 'warning'
    case 'DOWN': return 'danger'
    case 'COLD': return 'info'
    default: return undefined
  }
}

function endpoint(row: DataSourceHealthItem) {
  if (!row.host) return t('common.emptyDash')
  const base = `${row.host}:${row.port ?? 3306}`
  return row.defaultDatabase ? `${base}/${row.defaultDatabase}` : base
}

function poolUsage(row: DataSourceHealthItem) {
  if (row.activeConnections == null || row.maximumPoolSize == null) return t('common.emptyDash')
  return `${row.activeConnections} / ${row.maximumPoolSize}`
}

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    overview.value = await dataSourceHealthApi.overview()
    lastError.value = ''
  } catch (error) {
    lastError.value = error instanceof Error ? error.message : t('health.loadFailed')
    if (!silent) ElMessage.error(lastError.value)
  } finally {
    loading.value = false
  }
}

function restartTimer() {
  if (timer) clearInterval(timer)
  timer = undefined
  if (!autoRefresh.value) return
  timer = setInterval(() => {
    void load(true)
  }, REFRESH_MS)
}

function onAutoRefreshChange(value: boolean | string | number) {
  autoRefresh.value = Boolean(value)
  restartTimer()
}

onMounted(async () => {
  await load()
  restartTimer()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ t('health.title') }}</h1>
        <p class="subtitle">{{ t('health.subtitle') }}</p>
      </div>
      <div class="actions">
        <span class="meta">{{ t('health.lastRefresh') }}{{ checkedLabel }}</span>
        <el-switch
          :model-value="autoRefresh"
          inline-prompt
          :active-text="t('health.auto')"
          :inactive-text="t('health.manual')"
          @change="onAutoRefreshChange"
        />
        <el-button type="primary" :loading="loading" @click="load()">{{ t('health.refreshNow') }}</el-button>
      </div>
    </div>

    <el-alert
      v-if="lastError"
      class="error-banner"
      type="error"
      :title="lastError"
      show-icon
      :closable="false"
    />

    <div class="summary">
      <div v-for="card in cards" :key="card.key" class="stat" :class="card.tone">
        <div class="stat-value">{{ card.value }}</div>
        <div class="stat-label">{{ card.label }}</div>
      </div>
    </div>

    <el-table :data="overview?.items || []" stripe :empty-text="t('health.empty')">
      <el-table-column prop="name" :label="t('health.dataSource')" min-width="140" show-overflow-tooltip />
      <el-table-column :label="t('health.health')" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="healthType(row.health)">{{ healthLabel(row.health) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('health.latency')" width="100">
        <template #default="{ row }">
          <span :class="{ slow: (row.latencyMs ?? 0) >= 1000 }">
            {{ row.latencyMs == null ? t('common.emptyDash') : `${row.latencyMs} ms` }}
          </span>
        </template>
      </el-table-column>
      <el-table-column :label="t('health.pool')" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.poolReady ? 'success' : 'info'" effect="plain">
            {{ row.poolReady ? t('health.pooled') : t('health.noPool') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('health.activeMax')" width="110">
        <template #default="{ row }">{{ poolUsage(row) }}</template>
      </el-table-column>
      <el-table-column :label="t('health.idle')" width="80">
        <template #default="{ row }">{{ row.idleConnections ?? t('common.emptyDash') }}</template>
      </el-table-column>
      <el-table-column :label="t('health.total')" width="80">
        <template #default="{ row }">{{ row.totalConnections ?? t('common.emptyDash') }}</template>
      </el-table-column>
      <el-table-column :label="t('health.waiting')" width="80">
        <template #default="{ row }">
          <span :class="{ warn: (row.threadsAwaitingConnection ?? 0) > 0 }">
            {{ row.threadsAwaitingConnection ?? t('common.emptyDash') }}
          </span>
        </template>
      </el-table-column>
      <el-table-column :label="t('health.address')" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ endpoint(row) }}</template>
      </el-table-column>
      <el-table-column prop="message" :label="t('health.remark')" min-width="180" show-overflow-tooltip />
    </el-table>
  </div>
</template>

<style scoped>
.subtitle {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 13px;
}
.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.meta {
  color: #6b7280;
  font-size: 13px;
}
.error-banner { margin-bottom: 14px; }
.summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.stat {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 14px 16px;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.1;
}
.stat-label {
  margin-top: 6px;
  color: #6b7280;
  font-size: 13px;
}
.stat.ok .stat-value { color: #15803d; }
.stat.warn .stat-value { color: #b45309; }
.stat.danger .stat-value { color: #b91c1c; }
.stat.cold .stat-value { color: #0369a1; }
.stat.neutral .stat-value { color: #374151; }
.slow, .warn { color: #b45309; font-weight: 600; }

@media (max-width: 960px) {
  .summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>

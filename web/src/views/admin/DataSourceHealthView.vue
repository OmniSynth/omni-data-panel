<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { dataSourceHealthApi } from '@/api'
import { formatDateTime } from '@/display'
import type { DataSourceHealthItem, DataSourceHealthOverview, DataSourceHealthStatus } from '@/types'

const REFRESH_MS = 10_000

const loading = ref(false)
const autoRefresh = ref(true)
const overview = ref<DataSourceHealthOverview>()
const lastError = ref('')
let timer: ReturnType<typeof setInterval> | undefined

const checkedLabel = computed(() => {
  const raw = overview.value?.checkedAt
  return raw ? formatDateTime(raw) : '—'
})

const cards = computed(() => {
  const data = overview.value
  if (!data) return []
  return [
    { key: 'up', label: '正常', value: data.up, tone: 'ok' },
    { key: 'degraded', label: '亚健康', value: data.degraded, tone: 'warn' },
    { key: 'down', label: '不可用', value: data.down, tone: 'danger' },
    { key: 'cold', label: '未建池', value: data.cold, tone: 'cold' },
    { key: 'total', label: '数据源', value: data.total, tone: 'neutral' },
  ]
})

function healthLabel(status: DataSourceHealthStatus) {
  switch (status) {
    case 'UP': return '正常'
    case 'DEGRADED': return '亚健康'
    case 'DOWN': return '不可用'
    case 'COLD': return '未建池'
    case 'DISABLED': return '已停用'
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
  if (!row.host) return '—'
  const base = `${row.host}:${row.port ?? 3306}`
  return row.defaultDatabase ? `${base}/${row.defaultDatabase}` : base
}

function poolUsage(row: DataSourceHealthItem) {
  if (row.activeConnections == null || row.maximumPoolSize == null) return '—'
  return `${row.activeConnections} / ${row.maximumPoolSize}`
}

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    overview.value = await dataSourceHealthApi.overview()
    lastError.value = ''
  } catch (error) {
    lastError.value = error instanceof Error ? error.message : '健康状态加载失败'
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
        <h1 class="page-title">连接池监控</h1>
        <p class="subtitle">实时探测分析数据源可用性、延迟与 HikariCP 连接池占用。</p>
      </div>
      <div class="actions">
        <span class="meta">上次刷新：{{ checkedLabel }}</span>
        <el-switch
          :model-value="autoRefresh"
          inline-prompt
          active-text="自动"
          inactive-text="手动"
          @change="onAutoRefreshChange"
        />
        <el-button type="primary" :loading="loading" @click="load()">立即刷新</el-button>
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

    <el-table :data="overview?.items || []" stripe empty-text="暂无数据源">
      <el-table-column prop="name" label="数据源" min-width="140" show-overflow-tooltip />
      <el-table-column label="健康" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="healthType(row.health)">{{ healthLabel(row.health) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="延迟" width="100">
        <template #default="{ row }">
          <span :class="{ slow: (row.latencyMs ?? 0) >= 1000 }">
            {{ row.latencyMs == null ? '—' : `${row.latencyMs} ms` }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="连接池" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.poolReady ? 'success' : 'info'" effect="plain">
            {{ row.poolReady ? '已建池' : '未建池' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="活跃/最大" width="110">
        <template #default="{ row }">{{ poolUsage(row) }}</template>
      </el-table-column>
      <el-table-column label="空闲" width="80">
        <template #default="{ row }">{{ row.idleConnections ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="总数" width="80">
        <template #default="{ row }">{{ row.totalConnections ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="等待" width="80">
        <template #default="{ row }">
          <span :class="{ warn: (row.threadsAwaitingConnection ?? 0) > 0 }">
            {{ row.threadsAwaitingConnection ?? '—' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="地址" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ endpoint(row) }}</template>
      </el-table-column>
      <el-table-column prop="message" label="说明" min-width="180" show-overflow-tooltip />
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

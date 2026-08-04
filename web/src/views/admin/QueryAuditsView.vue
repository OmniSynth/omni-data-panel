<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import AuditCleanupActions from '@/components/AuditCleanupActions.vue'
import { dataSourceApi, queryAuditApi, userApi } from '@/api'
import { displayLabel, formatDateTime } from '@/display'
import { formatDuration } from '@/query/duration'
import type { AdminUser, DataSource, Id, QueryAudit, QueryAuditPreview } from '@/types'

const { t } = useI18n()
const loading = ref(false)
const rows = ref<QueryAudit[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const users = ref<AdminUser[]>([])
const sources = ref<DataSource[]>([])
const detailVisible = ref(false)
const detailLoading = ref(false)
const cleaning = ref(false)
const detail = ref<QueryAudit>()
const preview = ref<QueryAuditPreview | null>(null)

const filters = reactive({
  keyword: '',
  status: '',
  userId: undefined as Id | undefined,
  sourceId: undefined as Id | undefined,
})

const statusOptions = computed(() => [
  { label: t('queryAudit.allStatus'), value: '' },
  { label: displayLabel('RUNNING'), value: 'RUNNING' },
  { label: displayLabel('SUCCEEDED'), value: 'SUCCEEDED' },
  { label: displayLabel('FAILED'), value: 'FAILED' },
  { label: displayLabel('CANCELLED'), value: 'CANCELLED' },
])

const detailSql = computed(() => detail.value?.sqlText || '')
const browserText = computed(() => summarizeUserAgent(detail.value?.userAgent))

async function loadMeta() {
  try {
    const [userRows, sourceRows] = await Promise.all([userApi.list(), dataSourceApi.list()])
    users.value = userRows
    sources.value = sourceRows
  } catch {
    // 筛选下拉失败不阻断主列表
  }
}

async function load() {
  loading.value = true
  try {
    const result = await queryAuditApi.page({
      keyword: filters.keyword.trim() || undefined,
      status: filters.status || undefined,
      userId: filters.userId,
      sourceId: filters.sourceId,
      page: page.value,
      size: size.value,
    })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('queryAudit.loadFailed'))
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  filters.userId = undefined
  filters.sourceId = undefined
  search()
}

async function openDetail(row: QueryAudit) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = row
  preview.value = null
  try {
    const full = await queryAuditApi.detail(row.id)
    detail.value = full
    preview.value = parsePreview(full.resultPreview)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('queryAudit.detailFailed'))
  } finally {
    detailLoading.value = false
  }
}

function parsePreview(raw?: string | null): QueryAuditPreview | null {
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as QueryAuditPreview
    if (!parsed || !Array.isArray(parsed.columns) || !Array.isArray(parsed.rows)) return null
    return parsed
  } catch {
    return null
  }
}

function summarizeUserAgent(ua?: string | null) {
  if (!ua) return t('common.emptyDash')
  if (/Edg\//i.test(ua)) return 'Microsoft Edge'
  if (/Chrome\//i.test(ua) && !/Edg\//i.test(ua)) return 'Google Chrome'
  if (/Firefox\//i.test(ua)) return 'Mozilla Firefox'
  if (/Safari\//i.test(ua) && !/Chrome\//i.test(ua)) return 'Safari'
  return ua.length > 80 ? `${ua.slice(0, 80)}…` : ua
}

function statusType(status?: string) {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'CANCELLED') return 'info'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

async function onCleanup(payload: { mode: 'ALL' | 'BEFORE_DAYS' | 'BEFORE_DATE'; days?: number; before?: string }) {
  cleaning.value = true
  try {
    const result = await queryAuditApi.cleanup(payload)
    ElMessage.success(t('queryAudit.deleted', { n: result.deleted }))
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('queryAudit.cleanupFailed'))
  } finally {
    cleaning.value = false
  }
}

onMounted(async () => {
  await loadMeta()
  await load()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">{{ t('queryAudit.title') }}</h1>
      <AuditCleanupActions :cleaning="cleaning" @cleanup="onCleanup" />
    </div>

    <div class="toolbar filters">
      <el-input
        v-model="filters.keyword"
        clearable
        :placeholder="t('queryAudit.searchPlaceholder')"
        style="width: 260px"
        @keyup.enter="search"
      />
      <el-select v-model="filters.status" clearable :placeholder="t('common.status')" style="width: 140px">
        <el-option v-for="item in statusOptions" :key="item.value || 'all'" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="filters.userId" clearable filterable :placeholder="t('queryAudit.user')" style="width: 180px">
        <el-option
          v-for="item in users"
          :key="item.id"
          :label="item.displayName || item.username"
          :value="item.id"
        />
      </el-select>
      <el-select v-model="filters.sourceId" clearable filterable :placeholder="t('queryAudit.dataSource')" style="width: 200px">
        <el-option v-for="item in sources" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
      <el-button type="primary" @click="search">{{ t('common.search') }}</el-button>
      <el-button @click="resetFilters">{{ t('common.reset') }}</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" :empty-text="t('queryAudit.empty')" @row-click="openDetail">
      <el-table-column :label="t('queryAudit.time')" width="170">
        <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('queryAudit.user')" min-width="120">
        <template #default="{ row }">{{ row.displayName || row.username }}</template>
      </el-table-column>
      <el-table-column prop="dataSourceName" :label="t('queryAudit.dataSource')" min-width="120" show-overflow-tooltip />
      <el-table-column label="SQL" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">
          <code class="sql-cell">{{ row.sqlText }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="clientIp" label="IP" width="130" show-overflow-tooltip />
      <el-table-column :label="t('queryAudit.userAgent')" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ summarizeUserAgent(row.userAgent) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.status')" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.status)">{{ displayLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('queryAudit.rows')" width="80">
        <template #default="{ row }">{{ row.rowCount ?? t('common.emptyDash') }}</template>
      </el-table-column>
      <el-table-column :label="t('queryAudit.duration')" width="90">
        <template #default="{ row }">{{ formatDuration(row.durationMs) || t('common.emptyDash') }}</template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="openDetail(row)">{{ t('queryAudit.detail') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="load"
        @size-change="search"
      />
    </div>

    <el-drawer v-model="detailVisible" :title="t('queryAudit.detailTitle')" size="720px">
      <div v-loading="detailLoading" class="detail">
        <template v-if="detail">
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="t('queryAudit.user')">{{ detail.displayName || detail.username }}（{{ detail.username }}）</el-descriptions-item>
            <el-descriptions-item :label="t('common.status')">
              <el-tag size="small" :type="statusType(detail.status)">{{ displayLabel(detail.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="t('queryAudit.dataSource')">{{ detail.dataSourceName }}</el-descriptions-item>
            <el-descriptions-item :label="t('queryAudit.queryId')">{{ detail.queryId }}</el-descriptions-item>
            <el-descriptions-item :label="t('queryAudit.startedAt')">{{ formatDateTime(detail.startedAt) }}</el-descriptions-item>
            <el-descriptions-item :label="t('queryAudit.finishedAt')">{{ formatDateTime(detail.finishedAt) }}</el-descriptions-item>
            <el-descriptions-item :label="t('queryAudit.duration')">{{ formatDuration(detail.durationMs) || t('common.emptyDash') }}</el-descriptions-item>
            <el-descriptions-item :label="t('queryAudit.rowCount')">{{ detail.rowCount ?? t('common.emptyDash') }}</el-descriptions-item>
            <el-descriptions-item label="IP">{{ detail.clientIp || t('common.emptyDash') }}</el-descriptions-item>
            <el-descriptions-item :label="t('queryAudit.userAgent')">{{ browserText }}</el-descriptions-item>
            <el-descriptions-item label="User-Agent" :span="2">
              <span class="ua">{{ detail.userAgent || t('common.emptyDash') }}</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.errorMessage" :label="t('queryAudit.error')" :span="2">
              <span class="error">{{ detail.errorMessage }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <h3 class="block-title">{{ t('queryAudit.sql') }}</h3>
          <pre class="sql-block">{{ detailSql }}</pre>

          <h3 class="block-title">
            {{ t('queryAudit.preview') }}
            <span v-if="preview" class="muted">
              {{ t('queryAudit.previewMeta', { shown: preview.previewRowCount, total: preview.totalRowCount }) }}
            </span>
          </h3>
          <el-empty v-if="!preview?.rows?.length" :description="t('queryAudit.noPreview')" :image-size="64" />
          <el-table v-else :data="preview.rows" max-height="360" stripe :empty-text="t('queryAudit.noPreview')" size="small">
            <el-table-column
              v-for="column in preview.columns"
              :key="column"
              :prop="column"
              :label="column"
              min-width="120"
              show-overflow-tooltip
            />
          </el-table>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.filters { margin-bottom: 14px; }
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
.sql-cell {
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
  color: #374151;
}
.detail { padding-right: 6px; }
.block-title {
  margin: 22px 0 10px;
  font-size: 14px;
  color: #111827;
}
.muted { color: #9ca3af; font-weight: 400; }
.sql-block {
  margin: 0;
  padding: 12px 14px;
  border-radius: 8px;
  background: #0f172a;
  color: #e2e8f0;
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 260px;
  overflow: auto;
}
.ua {
  word-break: break-all;
  color: #6b7280;
  font-size: 12px;
}
.error { color: #dc2626; }
:deep(.el-table__row) { cursor: pointer; }
</style>

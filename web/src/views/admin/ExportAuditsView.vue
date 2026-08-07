<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import AuditCleanupActions from '@/components/AuditCleanupActions.vue'
import { exportAuditApi, settingsApi } from '@/api'
import { formatDateTime } from '@/display'
import type { ExportAudit } from '@/types'

const { t } = useI18n()
const loading = ref(false)
const cleaning = ref(false)
const logsClearEnabled = ref(true)
const rows = ref<ExportAudit[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const filters = reactive({
  keyword: '',
  status: '',
  format: '',
})

const statusOptions = [
  { value: 'SUCCEEDED', labelKey: 'exportAudit.statusSucceeded' },
  { value: 'FAILED', labelKey: 'exportAudit.statusFailed' },
]

const formatOptions = [
  { value: 'CSV', label: 'CSV' },
  { value: 'XLSX', label: 'XLSX' },
]

async function load() {
  loading.value = true
  try {
    const result = await exportAuditApi.page({
      keyword: filters.keyword.trim() || undefined,
      status: filters.status || undefined,
      format: filters.format || undefined,
      page: page.value,
      size: size.value,
    })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('exportAudit.loadFailed'))
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
  filters.format = ''
  search()
}

function userText(row: ExportAudit) {
  return row.displayName || row.username || t('common.emptyDash')
}

function modeLabel(mode: string) {
  if (mode === 'SYNC') return t('exportAudit.modeSync')
  if (mode === 'ASYNC') return t('exportAudit.modeAsync')
  return mode
}

function formatBytes(value?: number | null) {
  if (value == null || !Number.isFinite(value)) return t('common.emptyDash')
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}

async function onCleanup(payload: { mode: 'ALL' | 'BEFORE_DAYS' | 'BEFORE_DATE'; days?: number; before?: string }) {
  cleaning.value = true
  try {
    const result = await exportAuditApi.cleanup(payload)
    ElMessage.success(t('exportAudit.deleted', { n: result.deleted }))
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('exportAudit.cleanupFailed'))
  } finally {
    cleaning.value = false
  }
}

onMounted(async () => {
  logsClearEnabled.value = await settingsApi.logsClearEnabled()
  await load()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">{{ t('exportAudit.title') }}</h1>
      <AuditCleanupActions v-if="logsClearEnabled" :cleaning="cleaning" @cleanup="onCleanup" />
    </div>

    <div class="toolbar filters">
      <el-input
        v-model="filters.keyword"
        clearable
        :placeholder="t('exportAudit.searchPlaceholder')"
        style="width: 260px"
        @keyup.enter="search"
      />
      <el-select v-model="filters.status" clearable :placeholder="t('exportAudit.status')" style="width: 140px">
        <el-option :label="t('exportAudit.allStatus')" value="" />
        <el-option
          v-for="item in statusOptions"
          :key="item.value"
          :label="t(item.labelKey)"
          :value="item.value"
        />
      </el-select>
      <el-select v-model="filters.format" clearable :placeholder="t('exportAudit.format')" style="width: 120px">
        <el-option :label="t('exportAudit.allFormats')" value="" />
        <el-option
          v-for="item in formatOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <el-button type="primary" @click="search">{{ t('common.search') }}</el-button>
      <el-button @click="resetFilters">{{ t('common.reset') }}</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" :empty-text="t('exportAudit.empty')">
      <el-table-column :label="t('exportAudit.time')" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('exportAudit.user')" width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ userText(row) }}</template>
      </el-table-column>
      <el-table-column prop="dataSourceName" :label="t('exportAudit.dataSource')" min-width="120" show-overflow-tooltip />
      <el-table-column prop="format" :label="t('exportAudit.format')" width="80" />
      <el-table-column :label="t('exportAudit.mode')" width="80">
        <template #default="{ row }">{{ modeLabel(row.mode) }}</template>
      </el-table-column>
      <el-table-column :label="t('exportAudit.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'SUCCEEDED' ? 'success' : 'danger'" size="small" effect="light">
            {{ row.status === 'SUCCEEDED' ? t('exportAudit.statusSucceeded') : t('exportAudit.statusFailed') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('exportAudit.rows')" width="90">
        <template #default="{ row }">{{ row.rowCount ?? t('common.emptyDash') }}</template>
      </el-table-column>
      <el-table-column :label="t('exportAudit.size')" width="100">
        <template #default="{ row }">{{ formatBytes(row.byteSize) }}</template>
      </el-table-column>
      <el-table-column prop="queryId" :label="t('exportAudit.queryId')" min-width="160" show-overflow-tooltip />
      <el-table-column prop="clientIp" :label="t('exportAudit.clientIp')" width="130" show-overflow-tooltip />
      <el-table-column prop="errorMessage" :label="t('exportAudit.error')" min-width="180" show-overflow-tooltip />
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
  </div>
</template>

<style scoped>
.filters { margin-bottom: 14px; }
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import AuditCleanupActions from '@/components/AuditCleanupActions.vue'
import { datasetAuditApi, settingsApi } from '@/api'
import { formatDateTime } from '@/display'
import type { DatasetAudit } from '@/types'

const { t } = useI18n()
const loading = ref(false)
const cleaning = ref(false)
const logsClearEnabled = ref(true)
const rows = ref<DatasetAudit[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const filters = reactive({
  keyword: '',
  action: '',
})

const actionOptions = [
  { value: 'CREATE', labelKey: 'datasetAudit.actionCreate' },
  { value: 'UPDATE', labelKey: 'datasetAudit.actionUpdate' },
  { value: 'SOFT_DELETE', labelKey: 'datasetAudit.actionSoftDelete' },
  { value: 'RESTORE', labelKey: 'datasetAudit.actionRestore' },
  { value: 'PURGE', labelKey: 'datasetAudit.actionPurge' },
]

async function load() {
  loading.value = true
  try {
    const result = await datasetAuditApi.page({
      keyword: filters.keyword.trim() || undefined,
      action: filters.action || undefined,
      page: page.value,
      size: size.value,
    })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('datasetAudit.loadFailed'))
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
  filters.action = ''
  search()
}

function actionLabel(action: string) {
  const hit = actionOptions.find((item) => item.value === action)
  return hit ? t(hit.labelKey) : action
}

function operatorText(row: DatasetAudit) {
  return row.operatorDisplayName || row.operatorUsername || t('common.emptyDash')
}

async function onCleanup(payload: { mode: 'ALL' | 'BEFORE_DAYS' | 'BEFORE_DATE'; days?: number; before?: string }) {
  cleaning.value = true
  try {
    const result = await datasetAuditApi.cleanup(payload)
    ElMessage.success(t('datasetAudit.deleted', { n: result.deleted }))
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('datasetAudit.cleanupFailed'))
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
      <h1 class="page-title">{{ t('datasetAudit.title') }}</h1>
      <AuditCleanupActions v-if="logsClearEnabled" :cleaning="cleaning" @cleanup="onCleanup" />
    </div>

    <div class="toolbar filters">
      <el-input
        v-model="filters.keyword"
        clearable
        :placeholder="t('datasetAudit.searchPlaceholder')"
        style="width: 260px"
        @keyup.enter="search"
      />
      <el-select v-model="filters.action" clearable :placeholder="t('datasetAudit.action')" style="width: 160px">
        <el-option :label="t('datasetAudit.allActions')" value="" />
        <el-option
          v-for="item in actionOptions"
          :key="item.value"
          :label="t(item.labelKey)"
          :value="item.value"
        />
      </el-select>
      <el-button type="primary" @click="search">{{ t('common.search') }}</el-button>
      <el-button @click="resetFilters">{{ t('common.reset') }}</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" :empty-text="t('datasetAudit.empty')">
      <el-table-column :label="t('datasetAudit.time')" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="datasetName" :label="t('datasetAudit.datasetName')" min-width="160" show-overflow-tooltip />
      <el-table-column :label="t('datasetAudit.action')" width="120">
        <template #default="{ row }">{{ actionLabel(row.action) }}</template>
      </el-table-column>
      <el-table-column :label="t('datasetAudit.operator')" width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ operatorText(row) }}</template>
      </el-table-column>
      <el-table-column prop="detail" :label="t('datasetAudit.detail')" min-width="220" show-overflow-tooltip />
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

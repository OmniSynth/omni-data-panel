<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AuditCleanupActions from '@/components/AuditCleanupActions.vue'
import { loginAuditApi } from '@/api'
import { formatDateTime } from '@/display'
import type { LoginAudit } from '@/types'

const loading = ref(false)
const cleaning = ref(false)
const rows = ref<LoginAudit[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const filters = reactive({
  keyword: '',
  success: '' as '' | 'true' | 'false',
})

async function load() {
  loading.value = true
  try {
    const result = await loginAuditApi.page({
      keyword: filters.keyword.trim() || undefined,
      success: filters.success === '' ? undefined : filters.success === 'true',
      page: page.value,
      size: size.value,
    })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录日志加载失败')
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
  filters.success = ''
  search()
}

function summarizeUserAgent(ua?: string | null) {
  if (!ua) return '—'
  if (/Edg\//i.test(ua)) return 'Microsoft Edge'
  if (/Chrome\//i.test(ua) && !/Edg\//i.test(ua)) return 'Google Chrome'
  if (/Firefox\//i.test(ua)) return 'Mozilla Firefox'
  if (/Safari\//i.test(ua) && !/Chrome\//i.test(ua)) return 'Safari'
  return ua.length > 80 ? `${ua.slice(0, 80)}…` : ua
}

async function onCleanup(payload: { mode: 'ALL' | 'BEFORE_DAYS' | 'BEFORE_DATE'; days?: number; before?: string }) {
  cleaning.value = true
  try {
    const result = await loginAuditApi.cleanup(payload)
    ElMessage.success(`已删除 ${result.deleted} 条登录日志`)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '清理失败')
  } finally {
    cleaning.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">登录日志</h1>
      <AuditCleanupActions :cleaning="cleaning" @cleanup="onCleanup" />
    </div>

    <div class="toolbar filters">
      <el-input
        v-model="filters.keyword"
        clearable
        placeholder="搜索用户名 / IP / 消息"
        style="width: 260px"
        @keyup.enter="search"
      />
      <el-select v-model="filters.success" clearable placeholder="结果" style="width: 140px">
        <el-option label="全部结果" value="" />
        <el-option label="成功" value="true" />
        <el-option label="失败" value="false" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" empty-text="暂无登录日志">
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.loggedAt) }}</template>
      </el-table-column>
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.success ? 'success' : 'danger'">
            {{ row.success ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="message" label="说明" min-width="140" show-overflow-tooltip />
      <el-table-column prop="clientIp" label="IP" width="140" show-overflow-tooltip />
      <el-table-column label="浏览器" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ summarizeUserAgent(row.userAgent) }}</template>
      </el-table-column>
      <el-table-column label="User-Agent" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ row.userAgent || '—' }}</template>
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

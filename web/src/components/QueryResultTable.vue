<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { formatDateTime } from '@/display'
import type { QueryResult } from '@/types'
import { QUERY_RESULT_DISPLAY_LIMIT, QUERY_RESULT_PAGE_SIZES } from '@/query/limits'

const ISO_LIKE = /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}/

function formatCell(value: unknown) {
  if (value == null) return ''
  if (typeof value === 'string' && ISO_LIKE.test(value)) return formatDateTime(value)
  return value
}

const props = withDefaults(defineProps<{
  result?: QueryResult | null
  maxHeight?: number | string
  stripe?: boolean
}>(), {
  maxHeight: 420,
  stripe: true,
})

const page = ref(1)
const pageSize = ref<number>(QUERY_RESULT_PAGE_SIZES[1])

const totalFetched = computed(() => props.result?.rows?.length || 0)
const cappedRows = computed(() => (props.result?.rows || []).slice(0, QUERY_RESULT_DISPLAY_LIMIT))
const displayTotal = computed(() => cappedRows.value.length)
const truncated = computed(() => totalFetched.value > QUERY_RESULT_DISPLAY_LIMIT)
const pagedRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return cappedRows.value.slice(start, start + pageSize.value)
})
const columns = computed(() => props.result?.columns || [])

watch(() => props.result, () => { page.value = 1 })
watch(pageSize, () => { page.value = 1 })
</script>

<template>
  <div class="result-table">
    <el-table :data="pagedRows" :max-height="maxHeight" :stripe="stripe" empty-text="暂无数据">
      <el-table-column
        v-for="column in columns"
        :key="column"
        :prop="column"
        :label="column"
        min-width="140"
        show-overflow-tooltip
      >
        <template #default="{ row }">{{ formatCell(row[column]) }}</template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <span class="meta">
        共 {{ displayTotal }} 行
        <template v-if="truncated">（后端返回 {{ totalFetched }} 行，页面最多展示 {{ QUERY_RESULT_DISPLAY_LIMIT }} 行）</template>
      </span>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[...QUERY_RESULT_PAGE_SIZES]"
        :total="displayTotal"
        layout="sizes, prev, pager, next, jumper"
        background
        small
      />
    </div>
  </div>
</template>

<style scoped>
.result-table { width: 100%; }
.pager {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}
.meta { color: #6b7280; font-size: 13px; }
</style>

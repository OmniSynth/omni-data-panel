<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TableStyle } from '@/dashboard/config'
import type { QueryResult, RecordData } from '@/types'
import { QUERY_RESULT_PAGE_SIZES } from '@/query/limits'
import { columnStyleOf, formatTableCell, resolveLinkHref, resolveRowBackground } from '@/table/formatCell'

const { t } = useI18n()

const props = withDefaults(defineProps<{
  result?: QueryResult | null
  /** 列格式与条件行色 */
  tableStyle?: TableStyle | null
  /** 非铺满模式下的表格最大高度 */
  maxHeight?: number | string
  /** 铺满父容器：表格最高不超过容器，行少时不撑出留白 */
  fill?: boolean
  stripe?: boolean
}>(), {
  maxHeight: 420,
  fill: false,
  stripe: true,
})

const rootRef = ref<HTMLElement | null>(null)
const pagerRef = ref<HTMLElement | null>(null)
const fillMaxHeight = ref<number | undefined>(undefined)

const page = ref(1)
const pageSize = ref<number>(QUERY_RESULT_PAGE_SIZES[1])

const allRows = computed(() => props.result?.rows || [])
const fetchedCount = computed(() => allRows.value.length)
const displayTotal = computed(() => {
  const total = props.result?.total
  return typeof total === 'number' && Number.isFinite(total) ? total : fetchedCount.value
})
const truncated = computed(() => Boolean(props.result?.truncated))
const pagedRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return allRows.value.slice(start, start + pageSize.value)
})
const columns = computed(() => props.result?.columns || [])

const tableMaxHeight = computed(() => {
  if (!props.fill) return props.maxHeight
  return fillMaxHeight.value
})

let resizeObserver: ResizeObserver | null = null

/** 按根容器与分页条高度计算表格可用 max-height（像素） */
function measureFillHeight() {
  if (!props.fill || !rootRef.value) {
    fillMaxHeight.value = undefined
    return
  }
  const rootHeight = rootRef.value.clientHeight
  const pagerHeight = pagerRef.value?.offsetHeight ?? 0
  const gap = 12
  fillMaxHeight.value = Math.max(120, rootHeight - pagerHeight - gap)
}

/** 单元格展示文本 */
function cellText(column: string, value: unknown) {
  const format = columnStyleOf(props.tableStyle, column).format || 'auto'
  return formatTableCell(value, format)
}

/** 列是否按链接展示 */
function isLinkColumn(column: string) {
  return columnStyleOf(props.tableStyle, column).format === 'link'
}

/** 安全链接地址；非法时回退为普通文本 */
function linkHref(value: unknown) {
  return resolveLinkHref(value)
}

/** 单元格内文本样式（颜色/对齐） */
function cellTextStyle(column: string) {
  const style = columnStyleOf(props.tableStyle, column)
  return {
    color: style.color || undefined,
    textAlign: style.align || undefined,
  }
}

/** 单元格背景：写在 td 上，才能盖过斑马纹 */
function tableCellStyle({ row }: { row: RecordData }) {
  const background = resolveRowBackground(row, props.tableStyle)
  return background ? { background } : undefined
}

watch(() => props.result, () => { page.value = 1 })
watch(pageSize, () => { page.value = 1 })
watch(() => props.fill, async () => {
  await nextTick()
  measureFillHeight()
})

onMounted(() => {
  if (!props.fill || !rootRef.value) return
  measureFillHeight()
  resizeObserver = new ResizeObserver(() => measureFillHeight())
  resizeObserver.observe(rootRef.value)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
})
</script>

<template>
  <div ref="rootRef" class="result-table" :class="{ fill }">
    <el-table
      :data="pagedRows"
      :max-height="tableMaxHeight"
      :stripe="stripe"
      :empty-text="t('resultTable.empty')"
      :cell-style="tableCellStyle"
    >
      <el-table-column
        v-for="column in columns"
        :key="column"
        :prop="column"
        :label="column"
        :align="columnStyleOf(tableStyle, column).align || 'left'"
        min-width="140"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <a
            v-if="isLinkColumn(column) && linkHref(row[column])"
            class="cell-text cell-link"
            :href="linkHref(row[column])!"
            :style="cellTextStyle(column)"
            target="_blank"
            rel="noopener noreferrer"
            @click.stop
          >{{ cellText(column, row[column]) }}</a>
          <span v-else class="cell-text" :style="cellTextStyle(column)">{{ cellText(column, row[column]) }}</span>
        </template>
      </el-table-column>
    </el-table>
    <div ref="pagerRef" class="pager">
      <span class="meta">
        {{ t('resultTable.total', { n: displayTotal }) }}
        <template v-if="truncated">
          {{ t('resultTable.truncatedHint', { rows: fetchedCount }) }}
        </template>
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
.result-table.fill {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.pager {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
  flex-shrink: 0;
}
.meta { color: #6b7280; font-size: calc(13px * var(--omni-font-scale)); }
.result-table :deep(.el-table .cell) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cell-text {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cell-link {
  color: var(--el-color-primary);
  text-decoration: none;
}
.cell-link:hover { text-decoration: underline; }
</style>

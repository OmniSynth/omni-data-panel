<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { QueryResult } from '@/types'
import { QUERY_RESULT_DISPLAY_LIMIT } from '@/query/limits'
import QueryResultTable from '@/components/QueryResultTable.vue'

const props = defineProps<{ result?: QueryResult; type: string; option?: Record<string, unknown> }>()
const host = ref<HTMLElement>()
let chart: echarts.ECharts | undefined

const cappedResult = computed<QueryResult | undefined>(() => {
  if (!props.result) return undefined
  return {
    columns: props.result.columns,
    rows: props.result.rows.slice(0, QUERY_RESULT_DISPLAY_LIMIT),
  }
})

function ensureChart() {
  if (props.type === 'table') {
    chart?.dispose()
    chart = undefined
    return
  }
  if (!host.value) return
  if (!chart) chart = echarts.init(host.value)
}

function render() {
  ensureChart()
  if (!chart || props.type === 'table') return
  const columns = cappedResult.value?.columns || []
  const rows = cappedResult.value?.rows || []
  const labels = columns.length >= 2
    ? rows.map((row) => String(row[columns[0]] ?? ''))
    : rows.map((_, index) => String(index + 1))
  const valueColumns = columns.length >= 2 ? columns.slice(1) : columns
  const numericSeries = valueColumns.flatMap((column) => {
    const values = rows.map((row) => Number(row[column]))
    return rows.length && values.every(Number.isFinite) ? [{ name: column, values }] : []
  })
  const type = props.type === 'pie' ? 'pie' : props.type
  const series = type === 'pie'
    ? numericSeries.slice(0, 1).map((item) => ({
      name: item.name,
      type: 'pie' as const,
      data: labels.map((name, index) => ({ name, value: item.values[index] })),
    }))
    : numericSeries.map((item) => ({
      name: item.name,
      type: type as 'bar' | 'line',
      data: item.values,
    }))
  const dataOption = {
    tooltip: { trigger: type === 'pie' ? 'item' : 'axis' },
    legend: type === 'pie' ? {} : { data: numericSeries.map((item) => item.name) },
    xAxis: type === 'pie' ? undefined : { type: 'category', data: labels },
    yAxis: type === 'pie' ? undefined : { type: 'value' },
    series,
  }
  const base = props.option || {}
  const baseSeries = Array.isArray(base.series) ? base.series : []
  const mergedSeries = series.map((item, index) => ({
    ...(typeof baseSeries[index] === 'object' ? baseSeries[index] : {}),
    ...item,
  }))
  chart.setOption({
    ...base,
    ...dataOption,
    xAxis: dataOption.xAxis && typeof base.xAxis === 'object'
      ? { ...base.xAxis, ...dataOption.xAxis } : dataOption.xAxis,
    yAxis: dataOption.yAxis && typeof base.yAxis === 'object'
      ? { ...base.yAxis, ...dataOption.yAxis } : dataOption.yAxis,
    series: mergedSeries,
  }, true)
}

function resize() { chart?.resize() }

onMounted(async () => {
  await nextTick()
  render()
  window.addEventListener('resize', resize)
})
watch(() => [props.result, props.type, props.option], async () => {
  await nextTick()
  render()
}, { deep: true })
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
})
</script>

<template>
  <div v-if="type === 'table'" class="table-box">
    <QueryResultTable :result="cappedResult" :max-height="320" />
  </div>
  <div v-else ref="host" class="chart-box" />
</template>

<style scoped>
.table-box { width: 100%; height: 100%; min-height: 220px; }
</style>

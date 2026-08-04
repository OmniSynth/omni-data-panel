<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { useI18n } from 'vue-i18n'
import type { QueryResult } from '@/types'
import { QUERY_RESULT_DISPLAY_LIMIT } from '@/query/limits'
import QueryResultTable from '@/components/QueryResultTable.vue'
import type { ChartEncoding } from '@/dashboard/config'
import { resolveValueColumns } from '@/dashboard/config'
import {
  aggregateRows,
  canDrill,
  filterRowsByDrillStack,
  type DrillStackItem,
} from '@/dashboard/drill'
import { chartThemeName, ensureChartThemes, withChartPolish } from '@/chart/theme'
import { useThemeStore } from '@/stores/theme'
import worldGeoJson from '@/assets/geo/world.json'

const props = defineProps<{
  result?: QueryResult
  type: string
  option?: Record<string, unknown>
  interactive?: boolean
}>()

const emit = defineEmits<{
  click: [{ label: string; seriesName?: string; value?: number | string }]
}>()

const { t } = useI18n()
const themeStore = useThemeStore()
const host = ref<HTMLElement>()
let chart: echarts.ECharts | undefined
let worldRegistered = false
let activeTheme = ''

ensureChartThemes()

const drillStack = ref<DrillStackItem[]>([])

const encoding = computed(() => {
  const raw = props.option?.encoding
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return undefined
  return raw as ChartEncoding
})

const drillPath = computed(() => {
  const raw = props.option?.drillPath
  if (!Array.isArray(raw)) return undefined
  return raw.filter((item): item is string => typeof item === 'string' && !!item)
})

const drillEnabled = computed(() => canDrill(drillPath.value))

const cappedSource = computed<QueryResult | undefined>(() => {
  if (!props.result) return undefined
  return {
    columns: props.result.columns,
    rows: props.result.rows.slice(0, QUERY_RESULT_DISPLAY_LIMIT),
  }
})

/**
 * 应用下钻过滤与聚合后的展示结果；无 drillPath 时原样返回。
 * 地图保留点级行（只过滤不聚合），其它图按当前层级类目 SUM。
 */
const displayResult = computed<QueryResult | undefined>(() => {
  const source = cappedSource.value
  if (!source) return undefined
  if (!drillEnabled.value || !drillPath.value) return source

  const path = drillPath.value
  const level = Math.min(drillStack.value.length, path.length - 1)
  const category = path[level]
  const filtered = filterRowsByDrillStack(source.rows, drillStack.value)
  if (props.type === 'map') {
    return { columns: source.columns, rows: filtered }
  }
  const encodedValues = resolveValueColumns(encoding.value, source.columns)
    .filter((col) => col !== category)
  const aggValues = encodedValues.length
    ? encodedValues
    : source.columns.filter((col) => col !== category && !path.includes(col))
  const valueCols = aggValues.length
    ? aggValues
    : source.columns.filter((col) => col !== category)
  return aggregateRows(filtered, category, valueCols)
})

const canDrillDeeper = computed(() => {
  if (!drillEnabled.value || !drillPath.value) return false
  return drillStack.value.length < drillPath.value.length - 1
})

const chartInteractive = computed(() =>
  !!props.interactive || (drillEnabled.value && canDrillDeeper.value))

const kpiValue = computed(() => {
  const rows = displayResult.value?.rows || []
  const columns = displayResult.value?.columns || []
  if (!rows.length || !columns.length) return '—'
  const encoded = resolveValueColumns(encoding.value, columns)
  const col = encoded[0] || columns[columns.length - 1]
  const raw = rows[0][col]
  const num = Number(raw)
  return Number.isFinite(num) ? num.toLocaleString() : String(raw ?? '—')
})

/**
 * 解析类目列与数值列：下钻时强制使用当前层级类目；否则优先 encoding。
 */
function pickColumns(columns: string[]) {
  const enc = encoding.value
  if (drillEnabled.value && drillPath.value) {
    const level = Math.min(drillStack.value.length, drillPath.value.length - 1)
    const category = drillPath.value[level]
    const values = resolveValueColumns(enc, columns).filter((col) => col !== category)
    return {
      category: columns.includes(category) ? category : columns[0],
      values: values.length ? values : columns.filter((column) => column !== category),
    }
  }
  if (enc?.category && columns.includes(enc.category)) {
    const values = resolveValueColumns(enc, columns)
    return {
      category: enc.category,
      values: values.length ? values : columns.filter((column) => column !== enc.category),
    }
  }
  return {
    category: columns.length >= 2 ? columns[0] : undefined,
    values: columns.length >= 2 ? columns.slice(1) : columns,
  }
}

function ensureWorldMap() {
  if (worldRegistered) return
  echarts.registerMap('world', worldGeoJson as Parameters<typeof echarts.registerMap>[1])
  worldRegistered = true
}

/** 初始化或销毁 ECharts 实例；表格/KPI 不创建实例 */
function ensureChart() {
  if (props.type === 'table' || props.type === 'kpi') {
    chart?.dispose()
    chart = undefined
    activeTheme = ''
    return
  }
  if (!host.value) return
  const theme = chartThemeName(themeStore.isDark)
  if (chart && activeTheme !== theme) {
    chart.dispose()
    chart = undefined
  }
  if (!chart) {
    chart = echarts.init(host.value, theme)
    activeTheme = theme
    chart.on('click', (params) => {
      if (!chartInteractive.value) return
      const label = String(params.name ?? '')
      if (!label && props.type !== 'map') return
      const mapLabel = props.type === 'map'
        ? String((params.data as { name?: string } | undefined)?.name
          ?? params.name
          ?? '')
        : label
      const clickLabel = mapLabel || label
      if (!clickLabel) return

      if (drillEnabled.value && drillPath.value && canDrillDeeper.value) {
        const level = drillStack.value.length
        const field = drillPath.value[level]
        drillStack.value = [...drillStack.value, { field, value: clickLabel }]
        return
      }
      if (!props.interactive) return
      emit('click', {
        label: clickLabel,
        seriesName: params.seriesName ? String(params.seriesName) : undefined,
        value: typeof params.value === 'number' || typeof params.value === 'string'
          ? params.value
          : Array.isArray(params.value) ? params.value[params.value.length - 1] as number | string : undefined,
      })
    })
  }
}

function buildMapOption(rows: Array<Record<string, unknown>>, columns: string[]) {
  ensureWorldMap()
  const enc = encoding.value
  const lngCol = enc?.lng && columns.includes(enc.lng)
    ? enc.lng
    : columns.find((c) => /lng|lon|longitude/i.test(c)) || columns[0]
  const latCol = enc?.lat && columns.includes(enc.lat)
    ? enc.lat
    : columns.find((c) => /lat|latitude/i.test(c)) || columns[1] || columns[0]
  const valueCols = resolveValueColumns(enc, columns)
  const valueCol = valueCols[0]
  const drillCategory = drillEnabled.value && drillPath.value
    ? drillPath.value[Math.min(drillStack.value.length, drillPath.value.length - 1)]
    : undefined
  const nameCol = (drillCategory && columns.includes(drillCategory))
    ? drillCategory
    : (enc?.category && columns.includes(enc.category) ? enc.category : undefined)

  const data: Array<{ name: string; value: [number, number, number] }> = []
  let maxAbs = 1
  for (const row of rows) {
    const lng = Number(row[lngCol])
    const lat = Number(row[latCol])
    if (!Number.isFinite(lng) || !Number.isFinite(lat)) continue
    if (lng < -180 || lng > 180 || lat < -90 || lat > 90) continue
    const metric = valueCol != null ? Number(row[valueCol]) : 1
    const size = Number.isFinite(metric) ? Math.abs(metric) : 1
    if (size > maxAbs) maxAbs = size
    data.push({
      name: nameCol ? String(row[nameCol] ?? '') : `${lng},${lat}`,
      value: [lng, lat, Number.isFinite(metric) ? metric : 1],
    })
  }

  return {
    tooltip: {
      trigger: 'item',
      formatter: (params: { name?: string; value?: number[] }) => {
        const v = params.value
        if (!Array.isArray(v)) return params.name || ''
        const metric = v[2]
        return `${params.name || ''}<br/>${lngCol}: ${v[0]}<br/>${latCol}: ${v[1]}`
          + (valueCol ? `<br/>${valueCol}: ${metric}` : '')
      },
    },
    geo: {
      map: 'world',
      roam: true,
      itemStyle: {
        areaColor: themeStore.isDark ? '#1e293b' : '#e8eef5',
        borderColor: themeStore.isDark ? '#475569' : '#9aa8b8',
      },
      emphasis: {
        itemStyle: { areaColor: themeStore.isDark ? '#334155' : '#d5e0ec' },
      },
    },
    series: [{
      type: 'scatter',
      coordinateSystem: 'geo',
      data,
      symbolSize: (val: number[]) => {
        const raw = Math.abs(Number(val?.[2]) || 1)
        return 8 + (raw / maxAbs) * 24
      },
      itemStyle: { color: '#509ee3' },
    }],
  }
}

/** 按图表类型与查询结果组装并 setOption */
function render() {
  ensureChart()
  if (!chart || props.type === 'table' || props.type === 'kpi') return
  const columns = displayResult.value?.columns || []
  const rows = displayResult.value?.rows || []

  let dataOption: Record<string, unknown>
  if (props.type === 'map') {
    // 地图用过滤后的点级行；列名仍取原始结果
    const mapColumns = cappedSource.value?.columns || columns
    const mapRows = displayResult.value?.rows || rows
    dataOption = buildMapOption(mapRows, mapColumns)
  } else {
    const picked = pickColumns(columns)
    const labels = picked.category
      ? rows.map((row) => String(row[picked.category!] ?? ''))
      : rows.map((_, index) => String(index + 1))
    const numericSeries = picked.values.flatMap((column) => {
      const values = rows.map((row) => Number(row[column]))
      return rows.length && values.every(Number.isFinite) ? [{ name: column, values }] : []
    })

    if (props.type === 'pie' || props.type === 'funnel') {
      dataOption = {
        tooltip: { trigger: 'item' },
        legend: {},
        series: numericSeries.slice(0, 1).map((item) => ({
          name: item.name,
          type: props.type === 'funnel' ? 'funnel' : 'pie',
          data: labels.map((name, index) => ({ name, value: item.values[index] })),
          ...(props.type === 'funnel' ? { sort: 'descending', gap: 2 } : {}),
        })),
      }
    } else if (props.type === 'scatter') {
      const xCol = picked.category || columns[0]
      const yCol = picked.values[0] || columns[1] || columns[0]
      dataOption = {
        tooltip: { trigger: 'item' },
        xAxis: { type: 'value', name: xCol },
        yAxis: { type: 'value', name: yCol },
        series: [{
          type: 'scatter',
          data: rows.map((row) => [Number(row[xCol]), Number(row[yCol])]),
        }],
      }
    } else if (props.type === 'hbar') {
      dataOption = {
        tooltip: { trigger: 'axis' },
        legend: { data: numericSeries.map((item) => item.name) },
        xAxis: { type: 'value' },
        yAxis: { type: 'category', data: labels },
        series: numericSeries.map((item) => ({
          name: item.name,
          type: 'bar',
          data: item.values,
        })),
      }
    } else if (props.type === 'combo') {
      const seriesTypes = encoding.value?.seriesTypes || {}
      dataOption = {
        tooltip: { trigger: 'axis' },
        legend: { data: numericSeries.map((item) => item.name) },
        xAxis: { type: 'category', data: labels },
        yAxis: { type: 'value' },
        series: numericSeries.map((item, index) => ({
          name: item.name,
          type: seriesTypes[item.name] || (index === 0 ? 'bar' : 'line'),
          data: item.values,
        })),
      }
    } else {
      const seriesType = props.type === 'area' ? 'line' : props.type === 'line' ? 'line' : 'bar'
      dataOption = {
        tooltip: { trigger: 'axis' },
        legend: { data: numericSeries.map((item) => item.name) },
        xAxis: { type: 'category', data: labels },
        yAxis: { type: 'value' },
        series: numericSeries.map((item) => ({
          name: item.name,
          type: seriesType,
          areaStyle: props.type === 'area' ? {} : undefined,
          data: item.values,
        })),
      }
    }
  }

  const base = { ...(props.option || {}) }
  delete base.encoding
  delete base.drillPath
  const baseSeries = Array.isArray(base.series) ? base.series : []
  const series = Array.isArray(dataOption.series) ? dataOption.series as Record<string, unknown>[] : []
  const mergedSeries = series.map((item, index) => ({
    ...(typeof baseSeries[index] === 'object' ? baseSeries[index] as object : {}),
    ...item,
  }))
  chart.setOption(withChartPolish({
    ...base,
    ...dataOption,
    geo: dataOption.geo,
    xAxis: dataOption.xAxis && typeof base.xAxis === 'object'
      ? { ...(base.xAxis as object), ...(dataOption.xAxis as object) }
      : dataOption.xAxis,
    yAxis: dataOption.yAxis && typeof base.yAxis === 'object'
      ? { ...(base.yAxis as object), ...(dataOption.yAxis as object) }
      : dataOption.yAxis,
    series: mergedSeries,
  }, themeStore.isDark), true)
}

function drillTo(index: number) {
  if (index < 0) {
    drillStack.value = []
  } else {
    drillStack.value = drillStack.value.slice(0, index + 1)
  }
}

/** 窗口尺寸变化时调整图表 */
function resize() { chart?.resize() }

onMounted(async () => {
  await nextTick()
  render()
  window.addEventListener('resize', resize)
})
watch(() => [props.result, props.type, props.option, props.interactive], async () => {
  drillStack.value = []
  await nextTick()
  render()
}, { deep: true })
watch(() => themeStore.isDark, async () => {
  await nextTick()
  render()
})
watch(drillStack, async () => {
  await nextTick()
  render()
}, { deep: true })
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
})
</script>

<template>
  <div class="chart-preview">
    <div v-if="drillEnabled && drillStack.length" class="drill-bar">
      <button type="button" class="drill-crumb" @click="drillTo(-1)">{{ t('drill.all') }}</button>
      <template v-for="(item, index) in drillStack" :key="`${item.field}-${item.value}-${index}`">
        <span class="drill-sep">/</span>
        <button type="button" class="drill-crumb" @click="drillTo(index)">
          {{ item.field }}={{ item.value }}
        </button>
      </template>
      <button type="button" class="drill-back" @click="drillTo(drillStack.length - 2)">
        {{ t('drill.back') }}
      </button>
    </div>
    <div v-if="type === 'table'" class="table-box">
      <QueryResultTable :result="displayResult" :max-height="320" />
    </div>
    <div v-else-if="type === 'kpi'" class="kpi-box">
      <div class="kpi-value">{{ kpiValue }}</div>
    </div>
    <div v-else ref="host" class="chart-box" :class="{ interactive: chartInteractive }" />
  </div>
</template>

<style scoped>
.chart-preview { width: 100%; height: 100%; display: flex; flex-direction: column; min-height: 0; }
.drill-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-bottom: 6px;
  font-size: 12px;
}
.drill-crumb {
  border: none;
  background: transparent;
  color: var(--el-color-primary);
  cursor: pointer;
  padding: 0 2px;
}
.drill-sep { color: var(--el-text-color-secondary); }
.drill-back {
  margin-left: auto;
  border: 1px solid var(--el-border-color);
  background: var(--el-fill-color-blank);
  border-radius: 4px;
  padding: 0 8px;
  cursor: pointer;
  font-size: 12px;
}
.table-box { width: 100%; flex: 1; min-height: 220px; }
.chart-box { width: 100%; flex: 1; min-height: 220px; }
.chart-box.interactive { cursor: pointer; }
.kpi-box {
  width: 100%;
  flex: 1;
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.kpi-value {
  font-size: 42px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  line-height: 1.1;
}
</style>

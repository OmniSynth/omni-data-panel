<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { GridStack } from 'gridstack'
import { chartApi, dashboardApi } from '@/api'
import type { Chart, Dashboard, DashboardCard, DashboardLayout, DashboardRenderCard, Id, QueryResult } from '@/types'
import ChartPreview from '@/components/ChartPreview.vue'

const route = useRoute()
const router = useRouter()
const dashboard = ref<Dashboard>()
const charts = ref<Chart[]>([])
const cards = ref<DashboardCard[]>([])
const results = ref<Record<string, QueryResult>>({})
const renderedCards = ref<Record<string, DashboardRenderCard>>({})
const selectedChartId = ref<Id>()
let grid: GridStack | undefined
let loadVersion = 0
let mounted = true

async function load() {
  const version = ++loadVersion
  try {
    const id = String(route.params.id)
    const dashboardData = await dashboardApi.get(id)
    if (!['ADMIN', 'OWNER', 'WRITE'].includes(dashboardData.accessLevel)) {
      ElMessage.warning('当前访问级别不可编辑仪表盘')
      await router.replace(`/dashboards/${id}/view`)
      return
    }
    const [chartList, cardList] = await Promise.all([chartApi.list(), dashboardApi.cards(id)])
    if (!mounted || version !== loadVersion) return
    dashboard.value = dashboardData
    charts.value = chartList
    cards.value = cardList
    await nextTick()
    initializeGrid()
    await refresh(version)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '仪表盘加载失败') }
}
function initializeGrid() {
  grid?.destroy(false)
  grid = GridStack.init({ column: 12, cellHeight: 90, margin: 8, float: true })
}
function chartOf(id: Id) { return charts.value.find((item) => String(item.id) === String(id)) }
function layoutOf(card: DashboardCard): DashboardLayout {
  const fallback = { x: 0, y: 0, w: 6, h: 4 }
  try {
    const parsed = JSON.parse(card.layoutJson) as unknown
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return fallback
    const layout = parsed as DashboardLayout
    if (![layout.x, layout.y, layout.w, layout.h].every(Number.isFinite)
      || layout.x < 0 || layout.y < 0 || layout.w <= 0 || layout.h <= 0) {
      return fallback
    }
    return {
      x: Math.floor(layout.x),
      y: Math.floor(layout.y),
      w: Math.floor(layout.w),
      h: Math.floor(layout.h),
    }
  } catch {
    return fallback
  }
}
function chartOption(configJson?: string) {
  try {
    const parsed = JSON.parse(configJson || '{}') as unknown
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : {}
  } catch {
    return {}
  }
}
async function addChart() {
  if (!dashboard.value || selectedChartId.value === undefined) return ElMessage.warning('请选择图表')
  if (cards.value.some((item) => String(item.chartId) === String(selectedChartId.value))) return ElMessage.warning('该图表已在仪表盘中')
  const chart = chartOf(selectedChartId.value)
  if (!chart) return
  try {
    const card = await dashboardApi.createCard(dashboard.value.id, {
      chartId: chart.id, title: chart.name, layoutJson: JSON.stringify({ x: 0, y: 0, w: 6, h: 4 }),
    })
    cards.value.push(card)
    await nextTick()
    const element = document.querySelector(`[gs-id="${CSS.escape(String(card.id))}"]`) as HTMLElement
    grid?.makeWidget(element)
    await refresh()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '添加图表失败') }
}
async function removeChart(card: DashboardCard) {
  if (!dashboard.value) return
  try {
    await dashboardApi.removeCard(dashboard.value.id, card.id)
    const element = document.querySelector(`[gs-id="${CSS.escape(String(card.id))}"]`) as HTMLElement
    if (element) grid?.removeWidget(element, false)
    cards.value = cards.value.filter((item) => String(item.id) !== String(card.id))
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '移除图表失败') }
}
async function refresh(version = loadVersion) {
  if (!dashboard.value) return
  try {
    const rendered = await dashboardApi.render(dashboard.value.id)
    if (!mounted || version !== loadVersion) return
    renderedCards.value = Object.fromEntries(rendered.cards.map((card) => [String(card.cardId), card]))
    results.value = Object.fromEntries(rendered.cards
      .filter((card) => card.result)
      .map((card) => [String(card.cardId), card.result!]))
  }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '仪表盘数据刷新失败') }
}
async function save() {
  if (!dashboard.value || !grid) return
  try {
    await Promise.all(grid.engine.nodes.map((node) => {
      const card = cards.value.find((item) => String(item.id) === String(node.id))
      if (!card) throw new Error(`卡片 ${node.id} 不存在`)
      const layoutJson = JSON.stringify({ x: node.x || 0, y: node.y || 0, w: node.w || 6, h: node.h || 4 })
      return dashboardApi.updateCard(dashboard.value!.id, card.id, {
        chartId: card.chartId, title: card.title, layoutJson,
      }).then((updated) => Object.assign(card, updated))
    }))
    ElMessage.success('布局已保存')
  }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '布局保存失败') }
}
onMounted(load)
watch(() => route.params.id, () => {
  results.value = {}
  renderedCards.value = {}
  selectedChartId.value = undefined
  load()
})
onBeforeUnmount(() => {
  mounted = false
  loadVersion++
  grid?.destroy(false)
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">编辑仪表盘：{{ dashboard?.name }}</h1>
      <div>
        <el-button @click="$router.push(`/dashboards/${$route.params.id}/view`)">查看</el-button>
        <el-button type="primary" @click="save">保存布局</el-button>
      </div>
    </div>
    <el-card class="toolbar">
      <el-select v-model="selectedChartId" placeholder="选择问题" style="width:240px">
        <el-option v-for="chart in charts" :key="chart.id" :label="chart.name" :value="chart.id" />
      </el-select>
      <el-button @click="addChart">添加问题</el-button>
      <el-button type="primary" @click="refresh">刷新卡片</el-button>
    </el-card>
    <div v-if="dashboard" class="grid-stack">
      <div v-for="card in cards" :key="card.id" class="grid-stack-item" :gs-id="String(card.id)" :gs-x="layoutOf(card).x" :gs-y="layoutOf(card).y" :gs-w="layoutOf(card).w" :gs-h="layoutOf(card).h">
        <div class="grid-stack-item-content">
          <div class="card-head"><strong>{{ card.title }}</strong><el-button link type="danger" @click="removeChart(card)">移除</el-button></div>
          <el-alert v-if="renderedCards[String(card.id)]?.error" :title="renderedCards[String(card.id)].error" type="error" :closable="false" />
          <ChartPreview
            v-else
            :type="renderedCards[String(card.id)]?.chartType || chartOf(card.chartId)?.chartType || 'bar'"
            :result="results[String(card.id)]"
            :option="chartOption(renderedCards[String(card.id)]?.configJson)"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.grid-stack { min-height: 500px; background: #e9edf3; }
.grid-stack-item-content { background: white; border-radius: 5px; padding: 10px; overflow: hidden; }
.card-head { height: 32px; display: flex; align-items: center; justify-content: space-between; }
.chart-box { height: calc(100% - 32px); }
</style>

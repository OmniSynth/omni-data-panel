<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { dashboardApi } from '@/api'
import type { DashboardLayout, DashboardRender } from '@/types'
import ChartPreview from '@/components/ChartPreview.vue'

const route = useRoute()
const dashboard = ref<DashboardRender>()
const loading = ref(false)
let loadVersion = 0
let mounted = true

function layoutStyle(layoutJson: string) {
  const fallback = { x: 0, y: 0, w: 6, h: 4 }
  let layout = fallback
  try {
    const parsed = JSON.parse(layoutJson) as unknown
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      const candidate = parsed as DashboardLayout
      if ([candidate.x, candidate.y, candidate.w, candidate.h].every(Number.isFinite)
        && candidate.x >= 0 && candidate.y >= 0 && candidate.w > 0 && candidate.h > 0) {
        layout = candidate
      }
    }
  } catch {
    layout = fallback
  }
  const x = Math.floor(layout.x)
  const y = Math.floor(layout.y)
  const w = Math.min(12, Math.max(1, Math.floor(layout.w)))
  const h = Math.max(1, Math.floor(layout.h))
  return {
    gridColumn: `${x + 1} / span ${w}`,
    gridRow: `${y + 1} / span ${h}`,
  }
}

function chartOption(configJson: string) {
  try {
    return JSON.parse(configJson) as Record<string, unknown>
  } catch {
    return {}
  }
}

async function load() {
  const version = ++loadVersion
  loading.value = true
  dashboard.value = undefined
  try {
    const data = await dashboardApi.render(String(route.params.id))
    if (!mounted || version !== loadVersion) return
    dashboard.value = data
  } catch (error) {
    if (!mounted || version !== loadVersion) return
    ElMessage.error(error instanceof Error ? error.message : '仪表盘加载失败')
  } finally {
    if (mounted && version === loadVersion) loading.value = false
  }
}

onMounted(load)
watch(() => route.params.id, load)
onBeforeUnmount(() => {
  mounted = false
  loadVersion++
})
</script>

<template>
  <div v-loading="loading" class="page dashboard-view">
    <div class="page-header">
      <h1 class="page-title">{{ dashboard?.name || '仪表盘' }}</h1>
      <div class="toolbar" style="margin:0">
        <el-button @click="$router.push(`/dashboards/${$route.params.id}/edit`)">编辑</el-button>
        <el-button @click="$router.push('/')">返回首页</el-button>
      </div>
    </div>
    <el-empty v-if="!loading && dashboard && !dashboard.cards.length" description="该仪表盘暂无卡片" />
    <div v-if="dashboard?.cards.length" class="dashboard-grid">
      <el-card
        v-for="card in dashboard.cards"
        :key="card.cardId"
        class="dashboard-card"
        :style="layoutStyle(card.layoutJson)"
      >
        <template #header><strong>{{ card.title }}</strong></template>
        <el-alert v-if="card.error" :title="card.error" type="error" :closable="false" show-icon />
        <ChartPreview
          v-else
          :type="card.chartType"
          :result="card.result"
          :option="chartOption(card.configJson)"
        />
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.dashboard-view { min-height: 100%; }
.dashboard-grid { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); grid-auto-rows: 90px; gap: 16px; }
.dashboard-card { display: flex; flex-direction: column; overflow: hidden; }
.dashboard-card :deep(.el-card__body) { flex: 1; min-height: 0; }
.chart-box { height: 100%; min-height: 220px; }
@media (max-width: 900px) {
  .dashboard-card { grid-column: 1 / -1 !important; }
}
</style>

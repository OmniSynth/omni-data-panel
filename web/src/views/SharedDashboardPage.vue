<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { embedApi, publicApi } from '@/api'
import type { DashboardLayout, DashboardRender } from '@/types'
import ChartPreview from '@/components/ChartPreview.vue'

const props = defineProps<{ mode: 'public' | 'embed' }>()
const route = useRoute()
const loading = ref(false)
const dashboard = ref<DashboardRender>()

const token = computed(() => String(route.params.token || ''))

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
  loading.value = true
  try {
    dashboard.value = props.mode === 'public'
      ? await publicApi.dashboard(token.value)
      : await embedApi.dashboard(token.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '仪表盘加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page standalone">
    <div class="page-header">
      <h1 class="page-title">{{ dashboard?.name || '仪表盘' }}</h1>
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
.standalone { min-height: 100vh; background: #f5f6f8; }
.dashboard-grid { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); grid-auto-rows: 90px; gap: 16px; }
.dashboard-card { display: flex; flex-direction: column; overflow: hidden; }
.dashboard-card :deep(.el-card__body) { flex: 1; min-height: 0; }
@media (max-width: 900px) {
  .dashboard-card { grid-column: 1 / -1 !important; }
}
</style>

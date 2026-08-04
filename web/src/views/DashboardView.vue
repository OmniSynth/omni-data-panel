<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { dashboardApi } from '@/api'
import type { DashboardRender, DashboardLayout } from '@/types'
import ChartPreview from '@/components/ChartPreview.vue'
import DashboardParameterBar from '@/components/DashboardParameterBar.vue'
import {
  applyClickToParameterValues,
  defaultParameterValues,
  parseClickAction,
  parseDashboardConfig,
} from '@/dashboard/config'

const { t } = useI18n()
const route = useRoute()
const dashboard = ref<DashboardRender>()
const parameterValues = ref<Record<string, unknown>>({})
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

async function load(forceRefresh = false) {
  const version = ++loadVersion
  loading.value = true
  try {
    const data = await dashboardApi.render(String(route.params.id), {
      forceRefresh,
      parameterValues: parameterValues.value,
    })
    if (!mounted || version !== loadVersion) return
    dashboard.value = data
    const config = parseDashboardConfig(data.configJson)
    if (!Object.keys(parameterValues.value).length) {
      parameterValues.value = defaultParameterValues(config.parameters || [])
    }
  } catch (error) {
    if (!mounted || version !== loadVersion) return
    ElMessage.error(error instanceof Error ? error.message : t('dashboard.loadFailed'))
  } finally {
    if (mounted && version === loadVersion) loading.value = false
  }
}

async function applyParameters() {
  await load(true)
}

async function onCardClick(cardId: string, label: string) {
  const card = dashboard.value?.cards.find((item) => String(item.cardId) === cardId)
  if (!card) return
  const action = parseClickAction(card.clickActionJson)
  if (!action?.enabled) return
  const config = parseDashboardConfig(dashboard.value?.configJson)
  const parameter = config.parameters?.find((item) => item.id === action.setParameterId)
  parameterValues.value = applyClickToParameterValues(
    parameterValues.value,
    action,
    label,
    parameter?.type,
  )
  await load(true)
}

onMounted(() => load(false))
watch(() => route.params.id, () => {
  parameterValues.value = {}
  dashboard.value = undefined
  load(false)
})
onBeforeUnmount(() => {
  mounted = false
  loadVersion++
})
</script>

<template>
  <div v-loading="loading" class="page dashboard-view">
    <div class="page-header">
      <h1 class="page-title">{{ dashboard?.name || t('dashboard.title') }}</h1>
      <div class="toolbar" style="margin:0">
        <el-button @click="load(true)">{{ t('dashboard.refresh') }}</el-button>
        <el-button @click="$router.push(`/dashboards/${$route.params.id}/edit`)">{{ t('common.edit') }}</el-button>
        <el-button @click="$router.push('/')">{{ t('dashboard.backHome') }}</el-button>
      </div>
    </div>
    <DashboardParameterBar
      v-if="dashboard"
      v-model="parameterValues"
      :parameters="parseDashboardConfig(dashboard.configJson).parameters || []"
      @apply="applyParameters"
    />
    <el-empty v-if="!loading && dashboard && !dashboard.cards.length" :description="t('dashboard.noCards')" />
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
          :interactive="!!parseClickAction(card.clickActionJson)?.enabled"
          @click="onCardClick(String(card.cardId), $event.label)"
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
@media (max-width: 900px) {
  .dashboard-card { grid-column: 1 / -1 !important; }
}
</style>

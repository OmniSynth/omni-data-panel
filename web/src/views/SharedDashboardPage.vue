<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { embedApi, publicApi } from '@/api'
import type { DashboardRender, DashboardRenderCard } from '@/types'
import ChartPreview from '@/components/ChartPreview.vue'
import DashboardParameterBar from '@/components/DashboardParameterBar.vue'
import { useFullscreen } from '@/composables/useFullscreen'
import {
  defaultParameterValues,
  filterCardsByTab,
  parseDashboardConfig,
  parseLayoutJson,
} from '@/dashboard/config'
import { exportDashboardPdf, exportDashboardPng } from '@/dashboard/exportDashboard'

const props = defineProps<{ mode: 'public' | 'embed' | 'print' }>()
const { t } = useI18n()
const route = useRoute()
const root = ref<HTMLElement>()
const { isFullscreen, toggle: toggleFullscreen } = useFullscreen(root)
const loading = ref(false)
const exporting = ref(false)
const dashboard = ref<DashboardRender>()
const parameterValues = ref<Record<string, unknown>>({})
const activeTabId = ref<string>()

const token = computed(() => String(route.params.token || ''))
const dashboardConfig = computed(() => parseDashboardConfig(dashboard.value?.configJson))
const parameters = computed(() => dashboardConfig.value.parameters || [])
const tabs = computed(() => dashboardConfig.value.tabs || [])
const isPrint = computed(() => props.mode === 'print')
const flattenTabs = computed(() => isPrint.value || exporting.value)
const visibleCards = computed(() => {
  if (!dashboard.value) return [] as DashboardRenderCard[]
  if (flattenTabs.value || !tabs.value.length) return dashboard.value.cards
  return filterCardsByTab(dashboard.value.cards, activeTabId.value, tabs.value)
})
const tabSections = computed(() => {
  if (!dashboard.value || !tabs.value.length) return []
  return tabs.value.map((tab) => ({
    tab,
    cards: filterCardsByTab(dashboard.value!.cards, tab.id, tabs.value),
  }))
})

function markPrintReady() {
  if (!isPrint.value) return
  document.documentElement.setAttribute('data-print-ready', 'true')
}

function clearPrintReady() {
  document.documentElement.removeAttribute('data-print-ready')
}

function syncActiveTab() {
  if (!tabs.value.length) {
    activeTabId.value = undefined
    return
  }
  if (!activeTabId.value || !tabs.value.some((item) => item.id === activeTabId.value)) {
    activeTabId.value = tabs.value[0]?.id
  }
}

function layoutStyle(layoutJson: string) {
  const layout = parseLayoutJson(layoutJson)
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
  clearPrintReady()
  try {
    if (props.mode === 'public') {
      dashboard.value = await publicApi.dashboard(token.value)
    } else if (props.mode === 'print') {
      dashboard.value = await publicApi.printDashboard(token.value)
    } else {
      dashboard.value = await embedApi.dashboard(token.value)
    }
    syncActiveTab()
    parameterValues.value = defaultParameterValues(parameters.value)
    if (isPrint.value) {
      await nextTick()
      // 等待图表首屏绘制完成后再通知无头浏览器截取
      window.setTimeout(markPrintReady, 2200)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('dashboard.loadFailed'))
    if (isPrint.value) markPrintReady()
  } finally {
    loading.value = false
  }
}

async function onExport(command: string) {
  if (exporting.value || loading.value) return
  if (!root.value || !dashboard.value?.cards.length) {
    ElMessage.warning(t('dashboard.exportEmpty'))
    return
  }
  exporting.value = true
  try {
    await nextTick()
    const name = dashboard.value.name || t('dashboard.title')
    if (command === 'pdf') await exportDashboardPdf(root.value, name)
    else await exportDashboardPng(root.value, name)
  } catch {
    ElMessage.error(t('dashboard.exportFailed'))
  } finally {
    exporting.value = false
  }
}

onMounted(load)
onBeforeUnmount(clearPrintReady)
</script>

<template>
  <div
    ref="root"
    v-loading="loading || exporting"
    class="page standalone"
    :class="{ print: isPrint }"
    :element-loading-text="exporting ? t('dashboard.exporting') : undefined"
  >
    <div class="page-header" :class="{ 'no-export': true }">
      <h1 class="page-title">{{ dashboard?.name || t('dashboard.title') }}</h1>
      <div v-if="!isPrint" class="header-actions">
        <el-button text @click="toggleFullscreen">
          {{ isFullscreen ? t('dashboard.exitFullscreen') : t('dashboard.fullscreen') }}
        </el-button>
        <el-dropdown trigger="click" :disabled="exporting || loading" @command="onExport">
          <el-button text :loading="exporting">{{ t('dashboard.export') }}</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="png">{{ t('dashboard.exportPng') }}</el-dropdown-item>
              <el-dropdown-item command="pdf">{{ t('dashboard.exportPdf') }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
    <DashboardParameterBar
      v-if="parameters.length && !isPrint"
      v-model="parameterValues"
      :parameters="parameters"
      readonly
    />
    <el-empty v-if="!loading && dashboard && !dashboard.cards.length" :description="t('dashboard.noCards')" />

    <template v-if="flattenTabs && tabs.length && dashboard?.cards.length">
      <section v-for="section in tabSections" :key="section.tab.id" class="tab-section">
        <h2 class="tab-section-title">{{ section.tab.name }}</h2>
        <div v-if="section.cards.length" class="dashboard-grid">
          <el-card
            v-for="card in section.cards"
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
      </section>
    </template>

    <template v-else-if="dashboard?.cards.length">
      <el-tabs v-if="tabs.length" v-model="activeTabId" class="dashboard-tabs">
        <el-tab-pane
          v-for="tab in tabs"
          :key="tab.id"
          :label="tab.name"
          :name="tab.id"
        />
      </el-tabs>
      <el-empty
        v-if="tabs.length && !visibleCards.length"
        :description="t('dashboard.noCardsInTab')"
      />
      <div v-if="visibleCards.length" class="dashboard-grid">
        <el-card
          v-for="card in visibleCards"
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
    </template>
  </div>
</template>

<style scoped>
.standalone { min-height: 100vh; background: var(--omni-bg, #f5f6f8); }
.standalone.print {
  min-height: auto;
  background: #fff;
  padding: 16px 20px;
}
.standalone:fullscreen,
.standalone:-webkit-full-screen {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  overflow: auto;
  padding: 16px 24px;
  background: var(--omni-bg, #f5f6f8);
}
.standalone.exporting .no-export { display: none !important; }
.page-header {
  align-items: flex-start;
  margin-bottom: 12px;
}
.page-title {
  flex: 1;
  min-width: 0;
  line-height: 32px;
}
.header-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
  flex-shrink: 0;
}
.header-actions :deep(.el-button) {
  margin: 0;
}
.dashboard-tabs { margin-bottom: 8px; }
.tab-section { margin-bottom: 24px; }
.tab-section-title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
}
.dashboard-grid { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); grid-auto-rows: 90px; gap: 16px; }
.dashboard-card { display: flex; flex-direction: column; overflow: hidden; }
.dashboard-card :deep(.el-card__body) { flex: 1; min-height: 0; }
@media (max-width: 900px) {
  .dashboard-card { grid-column: 1 / -1 !important; }
}
</style>

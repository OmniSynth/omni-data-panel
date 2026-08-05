<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { dashboardApi, publicLinkApi } from '@/api'
import type { DashboardRender, DashboardRenderCard, PublicLink } from '@/types'
import ChartPreview from '@/components/ChartPreview.vue'
import DashboardParameterBar from '@/components/DashboardParameterBar.vue'
import PublicShareDialog from '@/components/PublicShareDialog.vue'
import { useFullscreen } from '@/composables/useFullscreen'
import {
  applyClickToParameterValues,
  defaultParameterValues,
  filterCardsByTab,
  parseClickAction,
  parseDashboardConfig,
  parseLayoutJson,
} from '@/dashboard/config'
import { exportDashboardPdf, exportDashboardPng } from '@/dashboard/exportDashboard'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const root = ref<HTMLElement>()
const { isFullscreen, toggle: toggleFullscreen } = useFullscreen(root)
const dashboard = ref<DashboardRender>()
const parameterValues = ref<Record<string, unknown>>({})
const activeTabId = ref<string>()
const links = ref<PublicLink[]>([])
const loading = ref(false)
const exporting = ref(false)
const linksVisible = ref(false)
const linksLoading = ref(false)
const creatingLink = ref(false)
const canEdit = computed(() =>
  !!dashboard.value && ['ADMIN', 'OWNER', 'WRITE'].includes(dashboard.value.accessLevel))
const dashboardConfig = computed(() => parseDashboardConfig(dashboard.value?.configJson))
const tabs = computed(() => dashboardConfig.value.tabs || [])
const parameters = computed(() => dashboardConfig.value.parameters || [])
const visibleCards = computed(() => {
  if (!dashboard.value) return [] as DashboardRenderCard[]
  if (exporting.value || !tabs.value.length) return dashboard.value.cards
  return filterCardsByTab(dashboard.value.cards, activeTabId.value, tabs.value)
})
const tabSections = computed(() => {
  if (!dashboard.value || !tabs.value.length) return []
  return tabs.value.map((tab) => ({
    tab,
    cards: filterCardsByTab(dashboard.value!.cards, tab.id, tabs.value),
  }))
})
let loadVersion = 0
let mounted = true

function dashboardId() {
  return String(route.params.id)
}

function publicUrl(token: string) {
  return `${location.origin}/public/dashboard/${token}`
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

function syncActiveTab() {
  if (!tabs.value.length) {
    activeTabId.value = undefined
    return
  }
  if (!activeTabId.value || !tabs.value.some((item) => item.id === activeTabId.value)) {
    activeTabId.value = tabs.value[0]?.id
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
    const data = await dashboardApi.render(dashboardId(), {
      forceRefresh,
      parameterValues: parameterValues.value,
    })
    if (!mounted || version !== loadVersion) return
    dashboard.value = data
    syncActiveTab()
    if (!Object.keys(parameterValues.value).length) {
      parameterValues.value = defaultParameterValues(parameters.value)
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
  const parameter = parameters.value.find((item) => item.id === action.setParameterId)
  parameterValues.value = applyClickToParameterValues(
    parameterValues.value,
    action,
    label,
    parameter?.type,
  )
  await load(true)
}

async function loadLinks() {
  linksLoading.value = true
  try {
    const all = await publicLinkApi.list({ resourceType: 'DASHBOARD', resourceId: dashboardId() })
    const id = dashboardId()
    links.value = all.filter((link) =>
      link.enabled !== false
      && link.resourceType === 'DASHBOARD'
      && String(link.resourceId) === id)
  } catch {
    links.value = []
  } finally {
    linksLoading.value = false
  }
}

async function openShareDialog() {
  linksVisible.value = true
  await loadLinks()
}

async function createPublicLink() {
  if (creatingLink.value) return
  creatingLink.value = true
  try {
    const link = await publicLinkApi.create({
      resourceType: 'DASHBOARD',
      resourceId: dashboardId(),
    })
    links.value = [link, ...links.value.filter((item) => item.token !== link.token)]
    await navigator.clipboard.writeText(publicUrl(link.token))
    ElMessage.success(t('dashboard.linkCopied'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('dashboard.linkCreateFailed'))
  } finally {
    creatingLink.value = false
  }
}

async function copyLink(link: PublicLink) {
  try {
    await navigator.clipboard.writeText(publicUrl(link.token))
    ElMessage.success(t('dashboard.linkCopied'))
  } catch {
    ElMessage.error(t('dashboard.linkCreateFailed'))
  }
}

async function revokeLink(link: PublicLink) {
  const previous = links.value
  links.value = links.value.filter((item) => item.token !== link.token)
  try {
    await publicLinkApi.revoke(link.id)
    ElMessage.success(t('dashboard.revoked'))
  } catch (error) {
    links.value = previous
    ElMessage.error(error instanceof Error ? error.message : t('dashboard.revokeFailed'))
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

onMounted(() => load(false))
watch(() => route.params.id, () => {
  parameterValues.value = {}
  activeTabId.value = undefined
  dashboard.value = undefined
  links.value = []
  linksVisible.value = false
  load(false)
})
onBeforeUnmount(() => {
  mounted = false
  loadVersion++
})
</script>

<template>
  <div
    ref="root"
    v-loading="loading || exporting"
    class="page dashboard-view"
    :element-loading-text="exporting ? t('dashboard.exporting') : undefined"
  >
    <div class="page-header no-export">
      <h1 class="page-title">{{ dashboard?.name || t('dashboard.title') }}</h1>
      <div class="header-actions">
        <el-button text @click="load(true)">{{ t('dashboard.refresh') }}</el-button>
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
        <el-button
          v-if="!isFullscreen && canEdit"
          text
          @click="openShareDialog"
        >
          {{ t('dashboard.publicShare') }}
        </el-button>
        <el-button
          v-if="!isFullscreen && canEdit"
          type="primary"
          @click="router.push(`/dashboards/${route.params.id}/edit`)"
        >
          {{ t('common.edit') }}
        </el-button>
      </div>
    </div>
    <DashboardParameterBar
      v-if="dashboard"
      v-model="parameterValues"
      :parameters="parameters"
      @apply="applyParameters"
    />
    <el-empty v-if="!loading && dashboard && !dashboard.cards.length" :description="t('dashboard.noCards')" />

    <!-- 导出时扁平展示全部页签 -->
    <template v-if="exporting && tabs.length && dashboard?.cards.length">
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
            :interactive="!!parseClickAction(card.clickActionJson)?.enabled"
            @click="onCardClick(String(card.cardId), $event.label)"
          />
        </el-card>
      </div>
    </template>

    <PublicShareDialog
      v-model="linksVisible"
      class="no-export"
      :resource-name="dashboard?.name"
      :hint="t('dashboard.publicLinksHint')"
      :empty-text="t('dashboard.publicLinksEmpty')"
      :links="links"
      :loading="linksLoading"
      :creating="creatingLink"
      :url-for="(link) => publicUrl(link.token)"
      @create="createPublicLink"
      @copy="copyLink"
      @revoke="revokeLink"
    />
  </div>
</template>

<style scoped>
.dashboard-view { min-height: 100%; }
.dashboard-view:fullscreen,
.dashboard-view:-webkit-full-screen {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  overflow: auto;
  padding: 16px 24px;
  background: var(--omni-bg, #f5f6f8);
}
.dashboard-view.exporting .no-export { display: none !important; }
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

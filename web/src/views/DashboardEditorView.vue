<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { displayLabel } from '@/display'
import { GridStack } from 'gridstack'
import { chartApi, dashboardApi, datasetApi } from '@/api'
import type {
  CardClickAction,
  CardParameterBinding,
  Chart,
  Dashboard,
  DashboardCard,
  DashboardLayout,
  DashboardParameter,
  DashboardParameterType,
  DashboardRenderCard,
  DashboardTab,
  Dataset,
  Id,
  QueryResult,
} from '@/types'
import ChartPreview from '@/components/ChartPreview.vue'
import DashboardParameterBar from '@/components/DashboardParameterBar.vue'
import {
  createDashboardTab,
  defaultParameterValues,
  filterCardsByTab,
  parseBindings,
  parseClickAction,
  parseDashboardConfig,
  parseLayoutJson,
  resolveCardTabId,
  serializeBindings,
  serializeClickAction,
  serializeDashboardConfig,
  stringifyLayout,
} from '@/dashboard/config'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const dashboard = ref<Dashboard>()
const charts = ref<Chart[]>([])
const cards = ref<DashboardCard[]>([])
const results = ref<Record<string, QueryResult>>({})
const renderedCards = ref<Record<string, DashboardRenderCard>>({})
const selectedChartId = ref<Id>()
const selectedCardId = ref<Id>()
const parameters = ref<DashboardParameter[]>([])
const tabs = ref<DashboardTab[]>([])
const activeTabId = ref<string>()
const parameterValues = ref<Record<string, unknown>>({})
const datasets = ref<Dataset[]>([])
const bindingDraft = ref<CardParameterBinding[]>([])
const clickDraft = ref<CardClickAction>({ enabled: false, setParameterId: '', valueMode: 'replace' })
const savingMeta = ref(false)
let grid: GridStack | undefined
let loadVersion = 0
let mounted = true

const selectedCard = computed(() =>
  cards.value.find((item) => String(item.id) === String(selectedCardId.value)))

const visibleCards = computed(() =>
  filterCardsByTab(cards.value, activeTabId.value, tabs.value))

const parameterTypeOptions = computed(() => [
  { value: 'text' as DashboardParameterType, label: t('dashboard.typeText') },
  { value: 'number' as DashboardParameterType, label: t('dashboard.typeNumber') },
  { value: 'date' as DashboardParameterType, label: t('dashboard.typeDate') },
  { value: 'date-range' as DashboardParameterType, label: t('dashboard.typeDateRange') },
  { value: 'select' as DashboardParameterType, label: t('dashboard.typeSelect') },
  { value: 'multi-select' as DashboardParameterType, label: t('dashboard.typeMultiSelect') },
])

async function load() {
  const version = ++loadVersion
  try {
    const id = String(route.params.id)
    const dashboardData = await dashboardApi.get(id)
    if (!['ADMIN', 'OWNER', 'WRITE'].includes(dashboardData.accessLevel)) {
      ElMessage.warning(t('dashboard.cannotEdit'))
      await router.replace(`/dashboards/${id}/view`)
      return
    }
    const [chartList, cardList, datasetList] = await Promise.all([
      chartApi.list(),
      dashboardApi.cards(id),
      datasetApi.list(),
    ])
    if (!mounted || version !== loadVersion) return
    dashboard.value = dashboardData
    charts.value = chartList
    cards.value = cardList
    datasets.value = datasetList
    const config = parseDashboardConfig(dashboardData.configJson)
    parameters.value = config.parameters || []
    tabs.value = config.tabs || []
    activeTabId.value = tabs.value[0]?.id
    parameterValues.value = defaultParameterValues(parameters.value)
    await nextTick()
    initializeGrid()
    await refresh(version)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('dashboard.loadFailed'))
  }
}

function initializeGrid() {
  grid?.destroy(false)
  grid = GridStack.init({ column: 12, cellHeight: 90, margin: 8, float: true })
}

async function reinitializeGrid() {
  await nextTick()
  initializeGrid()
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

async function onActiveTabChange(tabId: string | number) {
  activeTabId.value = String(tabId)
  selectedCardId.value = undefined
  await reinitializeGrid()
}

function chartOf(id: Id) {
  return charts.value.find((item) => String(item.id) === String(id))
}

function layoutOf(card: DashboardCard): DashboardLayout {
  return parseLayoutJson(card.layoutJson)
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

function selectCard(card: DashboardCard) {
  selectedCardId.value = card.id
  bindingDraft.value = parseBindings(card.bindingsJson).map((item) => ({ ...item }))
  clickDraft.value = parseClickAction(card.clickActionJson) || {
    enabled: false,
    setParameterId: parameters.value[0]?.id || '',
    valueMode: 'replace',
  }
}

function addTab() {
  const tab = createDashboardTab(t('dashboard.tabN', { n: tabs.value.length + 1 }), tabs.value)
  tabs.value.push(tab)
  if (!activeTabId.value) activeTabId.value = tab.id
}

async function removeTab(index: number) {
  const removed = tabs.value[index]
  if (!removed || !dashboard.value) return
  const remaining = tabs.value.filter((_, i) => i !== index)
  const fallbackId = remaining[0]?.id
  const affected = cards.value.filter((card) =>
    resolveCardTabId(card.layoutJson, tabs.value) === removed.id)
  try {
    await Promise.all(affected.map(async (card) => {
      const layout = parseLayoutJson(card.layoutJson)
      if (fallbackId) layout.tabId = fallbackId
      else delete layout.tabId
      const updated = await dashboardApi.updateCard(dashboard.value!.id, card.id, {
        chartId: card.chartId,
        title: card.title,
        layoutJson: stringifyLayout(layout),
        bindingsJson: card.bindingsJson ?? '[]',
        clickActionJson: card.clickActionJson,
      })
      Object.assign(card, updated)
    }))
    tabs.value = remaining
    const configJson = serializeDashboardConfig({
      parameters: parameters.value,
      tabs: tabs.value,
    })
    dashboard.value = await dashboardApi.update(dashboard.value.id, {
      name: dashboard.value.name,
      description: dashboard.value.description,
      configJson,
      collectionId: dashboard.value.collectionId,
    })
    syncActiveTab()
    selectedCardId.value = undefined
    await reinitializeGrid()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('dashboard.tabsSaveFailed'))
  }
}

async function moveSelectedCardToTab(tabId: string) {
  if (!dashboard.value || !selectedCard.value || !tabs.value.length) return
  const layout = parseLayoutJson(selectedCard.value.layoutJson)
  if (layout.tabId === tabId) return
  layout.tabId = tabId
  try {
    const updated = await dashboardApi.updateCard(dashboard.value.id, selectedCard.value.id, {
      chartId: selectedCard.value.chartId,
      title: selectedCard.value.title,
      layoutJson: stringifyLayout(layout),
      bindingsJson: selectedCard.value.bindingsJson ?? '[]',
      clickActionJson: selectedCard.value.clickActionJson,
    })
    Object.assign(selectedCard.value, updated)
    if (tabId !== activeTabId.value) {
      selectedCardId.value = undefined
      await reinitializeGrid()
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('dashboard.moveCardFailed'))
  }
}

function selectedCardTabId(): string | undefined {
  if (!selectedCard.value || !tabs.value.length) return undefined
  return resolveCardTabId(selectedCard.value.layoutJson, tabs.value)
}

function addParameter() {
  const id = `param_${parameters.value.length + 1}`
  parameters.value.push({
    id,
    label: t('dashboard.paramN', { n: parameters.value.length + 1 }),
    type: 'text',
    required: false,
  })
}

function removeParameter(index: number) {
  parameters.value.splice(index, 1)
}

function isSelectParameter(type: DashboardParameterType) {
  return type === 'select' || type === 'multi-select'
}

function optionSource(parameter: DashboardParameter): 'static' | 'dataset' {
  // 只要存在 optionsFrom 即视为模型字段模式（允许先选模型再选字段）
  return parameter.optionsFrom ? 'dataset' : 'static'
}

function setOptionSource(parameter: DashboardParameter, source: 'static' | 'dataset') {
  if (source === 'static') {
    delete parameter.optionsFrom
    return
  }
  if (parameter.optionsFrom) return
  parameter.optionsFrom = {
    datasetId: datasets.value[0]?.id || '',
    field: '',
    limit: 200,
  }
}

function staticOptionsText(parameter: DashboardParameter): string {
  return (parameter.options || []).map((item) =>
    item.label === String(item.value) ? String(item.value) : `${item.label}=${item.value}`).join('\n')
}

function setStaticOptionsText(parameter: DashboardParameter, text: string) {
  parameter.options = text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const index = line.indexOf('=')
      if (index > 0) {
        return { label: line.slice(0, index).trim(), value: line.slice(index + 1).trim() }
      }
      return { label: line, value: line }
    })
}

function datasetFields(datasetId: string | number | undefined) {
  if (datasetId == null || datasetId === '') return []
  return datasets.value.find((item) => String(item.id) === String(datasetId))?.fields || []
}

function onParameterTypeChange(parameter: DashboardParameter) {
  if (!isSelectParameter(parameter.type)) {
    delete parameter.optionsFrom
    delete parameter.options
  }
}

function rangeDefaultValue(parameter: DashboardParameter): [string, string] | undefined {
  const raw = parameter.defaultValue
  if (Array.isArray(raw) && raw.length >= 2) return [String(raw[0] ?? ''), String(raw[1] ?? '')]
  if (raw && typeof raw === 'object') {
    const map = raw as Record<string, unknown>
    const start = map.start ?? map.from
    const end = map.end ?? map.to
    if (start != null || end != null) return [String(start ?? ''), String(end ?? '')]
  }
  return undefined
}

function setRangeDefaultValue(parameter: DashboardParameter, value: [string, string] | null) {
  if (!value) {
    parameter.defaultValue = undefined
    return
  }
  parameter.defaultValue = { start: value[0], end: value[1] }
}

function addBinding() {
  bindingDraft.value.push({
    parameterId: parameters.value[0]?.id || '',
    mode: 'semantic',
    field: '',
    operator: 'EQ',
  })
}

function removeBinding(index: number) {
  bindingDraft.value.splice(index, 1)
}

async function saveParameters() {
  if (!dashboard.value) return
  savingMeta.value = true
  try {
    const configJson = serializeDashboardConfig({
      parameters: parameters.value,
      tabs: tabs.value,
    })
    dashboard.value = await dashboardApi.update(dashboard.value.id, {
      name: dashboard.value.name,
      description: dashboard.value.description,
      configJson,
      collectionId: dashboard.value.collectionId,
    })
    syncActiveTab()
    parameterValues.value = {
      ...defaultParameterValues(parameters.value),
      ...parameterValues.value,
    }
    ElMessage.success(t('dashboard.paramsSaved'))
    await reinitializeGrid()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('dashboard.paramsSaveFailed'))
  } finally {
    savingMeta.value = false
  }
}

async function saveCardMeta() {
  if (!dashboard.value || !selectedCard.value) return
  try {
    const updated = await dashboardApi.updateCard(dashboard.value.id, selectedCard.value.id, {
      chartId: selectedCard.value.chartId,
      title: selectedCard.value.title,
      layoutJson: selectedCard.value.layoutJson,
      bindingsJson: serializeBindings(bindingDraft.value),
      clickActionJson: serializeClickAction(clickDraft.value.enabled ? clickDraft.value : null),
    })
    Object.assign(selectedCard.value, updated)
    ElMessage.success(t('dashboard.bindingsSaved'))
    await refresh()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('dashboard.bindingsSaveFailed'))
  }
}

async function addChart() {
  if (!dashboard.value || selectedChartId.value === undefined) return ElMessage.warning(t('dashboard.needChart'))
  if (cards.value.some((item) => String(item.chartId) === String(selectedChartId.value))) {
    return ElMessage.warning(t('dashboard.chartAlreadyAdded'))
  }
  const chart = chartOf(selectedChartId.value)
  if (!chart) return
  try {
    const defaultH = chart.chartType === 'kpi' ? 2 : 4
    const layout: DashboardLayout = {
      x: 0,
      y: 0,
      w: chart.chartType === 'kpi' ? 3 : 6,
      h: defaultH,
    }
    if (activeTabId.value) layout.tabId = activeTabId.value
    const card = await dashboardApi.createCard(dashboard.value.id, {
      chartId: chart.id,
      title: chart.name,
      layoutJson: stringifyLayout(layout),
      bindingsJson: '[]',
    })
    cards.value.push(card)
    await nextTick()
    const element = document.querySelector(`[gs-id="${CSS.escape(String(card.id))}"]`) as HTMLElement
    grid?.makeWidget(element)
    selectCard(card)
    await refresh()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('dashboard.addChartFailed'))
  }
}

async function removeChart(card: DashboardCard) {
  if (!dashboard.value) return
  try {
    await dashboardApi.removeCard(dashboard.value.id, card.id)
    const element = document.querySelector(`[gs-id="${CSS.escape(String(card.id))}"]`) as HTMLElement
    if (element) grid?.removeWidget(element, false)
    cards.value = cards.value.filter((item) => String(item.id) !== String(card.id))
    if (String(selectedCardId.value) === String(card.id)) selectedCardId.value = undefined
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('dashboard.removeChartFailed'))
  }
}

async function refresh(version = loadVersion) {
  if (!dashboard.value) return
  try {
    const rendered = await dashboardApi.render(dashboard.value.id, {
      forceRefresh: true,
      parameterValues: parameterValues.value,
    })
    if (!mounted || version !== loadVersion) return
    renderedCards.value = Object.fromEntries(rendered.cards.map((card) => [String(card.cardId), card]))
    results.value = Object.fromEntries(rendered.cards
      .filter((card) => card.result)
      .map((card) => [String(card.cardId), card.result!]))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('dashboard.refreshFailed'))
  }
}

async function save() {
  if (!dashboard.value || !grid) return
  try {
    await Promise.all(grid.engine.nodes.map((node) => {
      const card = cards.value.find((item) => String(item.id) === String(node.id))
      if (!card) throw new Error(t('dashboard.cardMissing', { id: node.id }))
      const existing = parseLayoutJson(card.layoutJson)
      const layout: DashboardLayout = {
        x: node.x || 0,
        y: node.y || 0,
        w: node.w || 6,
        h: node.h || 4,
      }
      const tabId = existing.tabId || activeTabId.value
      if (tabId) layout.tabId = tabId
      const layoutJson = stringifyLayout(layout)
      return dashboardApi.updateCard(dashboard.value!.id, card.id, {
        chartId: card.chartId,
        title: card.title,
        layoutJson,
        bindingsJson: card.bindingsJson ?? '[]',
        clickActionJson: card.clickActionJson,
      }).then((updated) => Object.assign(card, updated))
    }))
    ElMessage.success(t('dashboard.layoutSaved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('dashboard.layoutSaveFailed'))
  }
}

onMounted(load)
watch(() => route.params.id, () => {
  results.value = {}
  renderedCards.value = {}
  selectedChartId.value = undefined
  selectedCardId.value = undefined
  tabs.value = []
  activeTabId.value = undefined
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
      <h1 class="page-title">{{ t('dashboard.editTitle') }}{{ dashboard?.name }}</h1>
      <div>
        <el-button @click="$router.push(`/dashboards/${$route.params.id}/view`)">{{ t('common.view') }}</el-button>
        <el-button type="primary" @click="save">{{ t('dashboard.saveLayout') }}</el-button>
      </div>
    </div>

    <el-card class="section">
      <template #header>
        <div class="section-head">
          <strong>{{ t('dashboard.tabs') }}</strong>
          <div>
            <el-button @click="addTab">{{ t('dashboard.addTab') }}</el-button>
            <el-button type="primary" :loading="savingMeta" @click="saveParameters">{{ t('dashboard.saveTabs') }}</el-button>
          </div>
        </div>
      </template>
      <p class="tabs-hint">{{ t('dashboard.tabsHint') }}</p>
      <el-empty v-if="!tabs.length" :description="t('dashboard.noTabs')" />
      <div v-for="(tab, index) in tabs" :key="tab.id" class="param-row">
        <el-input v-model="tab.name" :placeholder="t('dashboard.tabName')" style="width:220px" />
        <el-button link type="danger" @click="removeTab(index)">{{ t('common.remove') }}</el-button>
      </div>
    </el-card>

    <el-card class="section">
      <template #header>
        <div class="section-head">
          <strong>{{ t('dashboard.parameters') }}</strong>
          <div>
            <el-button @click="addParameter">{{ t('dashboard.addParameter') }}</el-button>
            <el-button type="primary" :loading="savingMeta" @click="saveParameters">{{ t('dashboard.saveParameters') }}</el-button>
          </div>
        </div>
      </template>
      <el-empty v-if="!parameters.length" :description="t('dashboard.noParameters')" />
      <div v-for="(parameter, index) in parameters" :key="parameter.id + index" class="param-block">
        <div class="param-row">
          <el-input v-model="parameter.id" placeholder="ID" style="width:140px" />
          <el-input v-model="parameter.label" :placeholder="t('dashboard.label')" style="width:140px" />
          <el-select v-model="parameter.type" style="width:140px" @change="onParameterTypeChange(parameter)">
            <el-option
              v-for="option in parameterTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-input
            v-if="parameter.type !== 'date-range'"
            v-model="parameter.defaultValue as string"
            :placeholder="t('dashboard.defaultValue')"
            style="width:160px"
          />
          <el-date-picker
            v-else
            :model-value="rangeDefaultValue(parameter)"
            type="daterange"
            value-format="YYYY-MM-DD"
            :start-placeholder="t('common.start')"
            :end-placeholder="t('common.end')"
            style="width:260px"
            @update:model-value="setRangeDefaultValue(parameter, $event as [string, string] | null)"
          />
          <el-switch v-model="parameter.required" inline-prompt :active-text="t('common.required')" :inactive-text="t('common.optional')" />
          <el-button link type="danger" @click="removeParameter(index)">{{ t('common.delete') }}</el-button>
        </div>
        <div v-if="isSelectParameter(parameter.type)" class="param-options">
          <el-radio-group
            :model-value="optionSource(parameter)"
            size="small"
            @update:model-value="setOptionSource(parameter, $event as 'static' | 'dataset')"
          >
            <el-radio-button value="static">{{ t('dashboard.staticOptions') }}</el-radio-button>
            <el-radio-button value="dataset">{{ t('dashboard.datasetField') }}</el-radio-button>
          </el-radio-group>
          <el-input
            v-if="optionSource(parameter) === 'static'"
            :model-value="staticOptionsText(parameter)"
            type="textarea"
            :rows="3"
            :placeholder="t('dashboard.optionsHint')"
            style="margin-top:8px;max-width:480px"
            @update:model-value="setStaticOptionsText(parameter, $event)"
          />
          <div v-else class="param-row" style="margin-top:8px">
            <el-select
              :model-value="parameter.optionsFrom?.datasetId"
              :placeholder="t('dashboard.selectModel')"
              filterable
              style="width:220px"
              @update:model-value="parameter.optionsFrom = {
                datasetId: $event,
                field: '',
                limit: parameter.optionsFrom?.limit || 200,
              }"
            >
              <el-option
                v-for="dataset in datasets"
                :key="dataset.id"
                :label="dataset.name"
                :value="dataset.id"
              />
            </el-select>
            <el-select
              :model-value="parameter.optionsFrom?.field"
              :placeholder="t('dashboard.selectField')"
              filterable
              style="width:180px"
              @update:model-value="parameter.optionsFrom = {
                datasetId: parameter.optionsFrom?.datasetId || '',
                field: $event,
                limit: parameter.optionsFrom?.limit || 200,
              }"
            >
              <el-option
                v-for="field in datasetFields(parameter.optionsFrom?.datasetId)"
                :key="field.name"
                :label="field.name"
                :value="field.name"
              />
            </el-select>
            <el-input-number
              :model-value="parameter.optionsFrom?.limit || 200"
              :min="1"
              :max="2000"
              controls-position="right"
              style="width:140px"
              @update:model-value="parameter.optionsFrom = {
                datasetId: parameter.optionsFrom?.datasetId || '',
                field: parameter.optionsFrom?.field || '',
                limit: $event || 200,
              }"
            />
          </div>
        </div>
      </div>
      <DashboardParameterBar
        v-if="parameters.length"
        v-model="parameterValues"
        :parameters="parameters"
        @apply="refresh()"
      />
    </el-card>

    <el-card class="toolbar">
      <el-tabs
        v-if="tabs.length"
        :model-value="activeTabId"
        class="editor-tabs"
        @tab-change="onActiveTabChange"
      >
        <el-tab-pane
          v-for="tab in tabs"
          :key="tab.id"
          :label="tab.name"
          :name="tab.id"
        />
      </el-tabs>
      <div class="toolbar-actions">
        <el-select v-model="selectedChartId" :placeholder="t('dashboard.selectChart')" style="width:240px">
          <el-option v-for="chart in charts" :key="chart.id" :label="chart.name" :value="chart.id" />
        </el-select>
        <el-button @click="addChart">{{ t('dashboard.addChart') }}</el-button>
        <el-button type="primary" @click="refresh()">{{ t('dashboard.refreshCards') }}</el-button>
      </div>
    </el-card>

    <div class="editor-body">
      <div v-if="dashboard" class="grid-stack">
        <div
          v-for="card in visibleCards"
          :key="card.id"
          class="grid-stack-item"
          :gs-id="String(card.id)"
          :gs-x="layoutOf(card).x"
          :gs-y="layoutOf(card).y"
          :gs-w="layoutOf(card).w"
          :gs-h="layoutOf(card).h"
          @click="selectCard(card)"
        >
          <div class="grid-stack-item-content" :class="{ selected: String(selectedCardId) === String(card.id) }">
            <div class="card-head">
              <strong>{{ card.title }}</strong>
              <el-button link type="danger" @click.stop="removeChart(card)">{{ t('common.remove') }}</el-button>
            </div>
            <el-alert
              v-if="renderedCards[String(card.id)]?.error"
              :title="renderedCards[String(card.id)].error"
              type="error"
              :closable="false"
            />
            <ChartPreview
              v-else
              :type="renderedCards[String(card.id)]?.chartType || chartOf(card.chartId)?.chartType || 'bar'"
              :result="results[String(card.id)]"
              :option="chartOption(renderedCards[String(card.id)]?.configJson)"
            />
          </div>
        </div>
      </div>

      <el-card v-if="selectedCard" class="side-panel">
        <template #header>
          <div class="section-head">
            <strong>{{ t('dashboard.cardConfig') }}{{ selectedCard.title }}</strong>
            <el-button
              link
              type="primary"
              @click="router.push({ path: '/query', query: { questionId: String(selectedCard.chartId) } })"
            >{{ t('chart.edit') }}</el-button>
          </div>
        </template>
        <el-form v-if="tabs.length" label-width="90px" class="card-tab-form">
          <el-form-item :label="t('dashboard.moveToTab')">
            <el-select
              :model-value="selectedCardTabId()"
              style="width:100%"
              @change="moveSelectedCardToTab(String($event))"
            >
              <el-option v-for="tab in tabs" :key="tab.id" :label="tab.name" :value="tab.id" />
            </el-select>
          </el-form-item>
        </el-form>
        <h4>{{ t('dashboard.bindings') }}</h4>
        <div v-for="(binding, index) in bindingDraft" :key="index" class="binding-row">
          <el-select v-model="binding.parameterId" :placeholder="t('dashboard.parameter')" style="width:120px">
            <el-option v-for="item in parameters" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
          <el-select v-model="binding.mode" style="width:110px">
            <el-option :label="t('dashboard.semantic')" value="semantic" />
            <el-option :label="displayLabel('SQL')" value="sql" />
          </el-select>
          <el-input
            v-if="binding.mode === 'semantic'"
            v-model="binding.field"
            :placeholder="t('dashboard.field')"
            style="width:120px"
          />
          <el-select
            v-if="binding.mode === 'semantic'"
            v-model="binding.operator"
            style="width:100px"
          >
            <el-option
              v-for="op in ['EQ','NE','GT','GTE','LT','LTE','LIKE','IN']"
              :key="op"
              :label="op"
              :value="op"
            />
          </el-select>
          <el-input-number
            v-if="binding.mode === 'sql'"
            v-model="binding.parameterIndex"
            :min="0"
            controls-position="right"
          />
          <el-button link type="danger" @click="removeBinding(index)">{{ t('dashboard.del') }}</el-button>
        </div>
        <el-button @click="addBinding">{{ t('dashboard.addBinding') }}</el-button>

        <h4 class="mt">{{ t('dashboard.clickAction') }}</h4>
        <el-form label-width="90px">
          <el-form-item :label="t('common.enabled')">
            <el-switch v-model="clickDraft.enabled" />
          </el-form-item>
          <el-form-item :label="t('dashboard.writeParameter')">
            <el-select v-model="clickDraft.setParameterId" style="width:100%">
              <el-option v-for="item in parameters" :key="item.id" :label="item.label" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('dashboard.writeMode')">
            <el-select v-model="clickDraft.valueMode" style="width:100%">
              <el-option :label="t('dashboard.replace')" value="replace" />
              <el-option :label="t('dashboard.toggleMulti')" value="toggle" />
            </el-select>
          </el-form-item>
        </el-form>
        <el-button type="primary" @click="saveCardMeta">{{ t('dashboard.saveCardConfig') }}</el-button>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.section { margin-bottom: 12px; }
.section-head { display: flex; justify-content: space-between; align-items: center; }
.tabs-hint {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}
.param-row, .binding-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
.param-block { margin-bottom: 12px; padding-bottom: 8px; border-bottom: 1px solid var(--el-border-color-lighter); }
.param-options { margin: 0 0 8px 4px; }
.toolbar { margin-bottom: 12px; }
.toolbar-actions { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.editor-tabs { margin-bottom: 8px; }
.card-tab-form { margin-bottom: 8px; }
.editor-body { display: grid; grid-template-columns: 1fr 320px; gap: 12px; }
.grid-stack { min-height: 500px; background: #e9edf3; }
.grid-stack-item-content {
  background: white;
  border-radius: 5px;
  padding: 10px;
  overflow: hidden;
  border: 2px solid transparent;
}
.grid-stack-item-content.selected { border-color: var(--el-color-primary); }
.card-head { height: 32px; display: flex; align-items: center; justify-content: space-between; }
.side-panel h4 { margin: 0 0 8px; }
.side-panel .mt { margin-top: 16px; }
@media (max-width: 1100px) {
  .editor-body { grid-template-columns: 1fr; }
}
</style>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { promptBox } from '@/i18n/dialog'
import { useRoute, useRouter } from 'vue-router'
import { chartApi, collectionApi, dataSourceApi, exportApi, queryApi, settingsApi } from '@/api'
import { displayLabel } from '@/display'
import { useUserStore } from '@/stores/user'
import type { Chart, Collection, DataSource, Id, QueryResult, QuerySnapshot, SiteSettings } from '@/types'
import SqlEditor from '@/components/SqlEditor.vue'
import MetadataBrowsePanel from '@/components/MetadataBrowsePanel.vue'
import { useFullscreen } from '@/composables/useFullscreen'
import ChartPreview from '@/components/ChartPreview.vue'
import QueryResultTable from '@/components/QueryResultTable.vue'
import {
  countCompletionTables,
  inferDefaultSchema,
  toEditorSchema,
  type CompletionSchemaPayload,
  type EditorSqlSchema,
} from '@/sql/schema'
import { resolveSqlDialect } from '@/sql/dialects'
import { QUERY_RESULT_DISPLAY_LIMIT } from '@/query/limits'
import { formatDuration } from '@/query/duration'
import { alignNamedParameters, alignSqlParameters, extractNamedPlaceholders } from '@/sql/parameters'
import { chartTypeOptions, mergeChartConfig, type ChartEncoding, type TableStyle } from '@/dashboard/config'
import ChartEncodingForm from '@/components/ChartEncodingForm.vue'
import TableStyleForm from '@/components/TableStyleForm.vue'

const { t } = useI18n()
const userStore = useUserStore()
const chartTypeOptionList = computed(() => chartTypeOptions())
const route = useRoute()
const router = useRouter()

const sources = ref<DataSource[]>([])
const collections = ref<Collection[]>([])
const sourceId = ref<Id | undefined>(
  typeof route.query.sourceId === 'string' ? route.query.sourceId : undefined,
)
const sql = ref('')
const sqlParameters = ref<string[]>([])
const namedSqlParameters = reactive<Record<string, string>>({})
const namedParamNames = computed(() => extractNamedPlaceholders(sql.value))
const sqlEditorRef = ref<{
  format: () => boolean
  insertText: (text: string) => boolean
  requestMeasure?: () => void
}>()
const editorPanelRef = ref<HTMLElement | null>(null)
const { isFullscreen: editorFullscreen, toggle: toggleEditorFullscreen } = useFullscreen(editorPanelRef)
const editorSchema = ref<EditorSqlSchema>({})
const completionPayload = ref<CompletionSchemaPayload | null>(null)
const schemaLoading = ref(false)
const collectionId = ref<Id | undefined>(
  typeof route.query.collectionId === 'string' ? route.query.collectionId : undefined,
)
const questionName = ref(typeof route.query.name === 'string' ? route.query.name : '')
const chartType = ref('table')
const chartEncoding = ref<ChartEncoding>({})
const chartDrillPath = ref<string[]>([])
const chartTableStyle = ref<TableStyle>({})
const saving = ref(false)
const task = ref<QuerySnapshot>()
const result = ref<QueryResult>()
const tipsCollapsed = ref(false)
const nowMs = ref(Date.now())
const running = computed(() => task.value?.status === 'QUEUED' || task.value?.status === 'RUNNING')
const currentSource = computed(() => sources.value.find((item) => String(item.id) === String(sourceId.value)))
const currentDialect = computed(() => resolveSqlDialect(currentSource.value?.dialect, currentSource.value?.jdbcUrl).id)
const catalogHint = computed(() => {
  const database = currentSource.value?.defaultDatabase?.trim()
  if (database) return t('sql.defaultDbHint', { db: database })
  if (currentSource.value) return t('sql.noDefaultDb')
  return t('sql.selectSourceHint')
})
const editorPlaceholder = computed(() => t('sql.editorPlaceholder'))
const schemaTableCount = computed(() => countCompletionTables(completionPayload.value))
const editorDefaultSchema = computed(() =>
  inferDefaultSchema(completionPayload.value, currentSource.value?.defaultDatabase))
const resultCount = computed(() => result.value?.rows.length || 0)
const selectedCollectionName = computed(() => {
  if (collectionId.value === undefined) return ''
  return collections.value.find((item) => String(item.id) === String(collectionId.value))?.name || ''
})
const canSave = computed(() => userStore.hasPermission('chart:create'))
const canExport = computed(() => userStore.hasPermission('export:execute'))
const statusType = computed(() => {
  const status = task.value?.status
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'CANCELLED') return 'info'
  if (status === 'QUEUED' || status === 'RUNNING') return 'warning'
  return 'info'
})
const elapsedMs = computed(() => {
  if (!task.value) return undefined
  if (task.value.durationMs != null) return task.value.durationMs
  if (task.value.startedAtMs != null && running.value) {
    return Math.max(0, nowMs.value - task.value.startedAtMs)
  }
  return undefined
})
const elapsedText = computed(() => formatDuration(elapsedMs.value))
let timer: number | undefined
let clockTimer: number | undefined

function startClock() {
  window.clearInterval(clockTimer)
  nowMs.value = Date.now()
  clockTimer = window.setInterval(() => { nowMs.value = Date.now() }, 200)
}

function stopClock() {
  window.clearInterval(clockTimer)
  clockTimer = undefined
}

async function loadCompletionSchema(id: Id) {
  schemaLoading.value = true
  try {
    const payload = await dataSourceApi.completionSchema(id)
    completionPayload.value = payload
    editorSchema.value = toEditorSchema(payload)
    const source = sources.value.find((item) => String(item.id) === String(id))
    if (source && payload.dialect) source.dialect = payload.dialect
  } catch {
    completionPayload.value = null
    editorSchema.value = {}
  } finally {
    schemaLoading.value = false
  }
}

function flatten(nodes: Collection[], acc: Collection[] = []): Collection[] {
  for (const node of nodes) {
    acc.push(node)
    if (node.children?.length) flatten(node.children, acc)
  }
  return acc
}

async function load() {
  if (!userStore.hasPermission('query:raw')) return
  try {
    const [sourceList, tree, settings] = await Promise.all([
      dataSourceApi.list(),
      collectionApi.tree(),
      settingsApi.get().catch((): SiteSettings => ({})),
    ])
    sources.value = sourceList
    collections.value = flatten(tree)
    const tipsDefault = settings['ui.sql.tips-collapsed-default']
    tipsCollapsed.value = String(tipsDefault) === 'true' || tipsDefault === true
    if (collectionId.value === undefined) {
      collectionId.value = collections.value[0]?.id
    } else if (!collections.value.some((item) => String(item.id) === String(collectionId.value))) {
      collectionId.value = collections.value[0]?.id
    }
    if (sourceId.value === undefined && sources.value.length === 1) {
      sourceId.value = sources.value[0].id
    }
    if (sourceId.value !== undefined) await loadCompletionSchema(sourceId.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('sql.loadFailed'))
  }
}

function buildNamedPayload(): Record<string, unknown> | undefined {
  const names = extractNamedPlaceholders(sql.value)
  if (!names.length) return undefined
  const payload: Record<string, unknown> = {}
  for (const name of names) {
    payload[name] = namedSqlParameters[name] ?? ''
  }
  return payload
}

async function run() {
  if (!sourceId.value) return ElMessage.warning(t('sql.needSource'))
  if (!sql.value.trim()) return ElMessage.warning(t('sql.needSql'))
  result.value = undefined
  try {
    const submitted = await queryApi.submit({
      sourceId: sourceId.value,
      sql: sql.value,
      parameters: alignSqlParameters(sql.value, sqlParameters.value),
      namedParameters: buildNamedPayload(),
    })
    task.value = { queryId: submitted.queryId, status: 'QUEUED', startedAtMs: Date.now() }
    startClock()
    await poll()
  } catch (error) {
    stopClock()
    ElMessage.error(error instanceof Error ? error.message : t('sql.submitFailed'))
  }
}

async function poll() {
  if (!task.value) return
  try {
    task.value = await queryApi.status(task.value.queryId)
    if (task.value.status === 'SUCCEEDED') {
      result.value = task.value.result
      stopClock()
    } else if (task.value.status === 'FAILED') {
      stopClock()
      ElMessage.error(task.value.error || t('sql.execFailed'))
    } else if (task.value.status === 'CANCELLED') {
      stopClock()
    } else {
      timer = window.setTimeout(poll, 800)
    }
  } catch (error) {
    stopClock()
    ElMessage.error(error instanceof Error ? error.message : t('sql.statusFailed'))
  }
}

async function cancel() {
  if (!task.value) return
  try {
    await queryApi.cancel(task.value.queryId)
    task.value = { ...task.value, status: 'CANCELLED', durationMs: elapsedMs.value }
    stopClock()
    ElMessage.info(t('sql.cancelled'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('sql.cancelFailed'))
  }
}

async function createExport(format: 'CSV' | 'XLSX') {
  if (!canExport.value) return ElMessage.warning(t('common.noExportPermission'))
  if (!task.value || task.value.status !== 'SUCCEEDED') return
  try {
    const blob = await exportApi.download(task.value.queryId, format)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `query.${format.toLowerCase()}`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('sql.exportFailed'))
  }
}

async function saveQuestion() {
  if (!sourceId.value || !sql.value.trim()) return ElMessage.warning(t('sql.needConfig'))
  if (!canSave.value) return ElMessage.warning(t('chart.noCreatePermission'))
  if (collectionId.value === undefined) return ElMessage.warning(t('sql.needCollection'))
  let name = questionName.value.trim()
  if (!name) {
    const { value } = await promptBox(t('chart.namePrompt'), t('sql.saveToCollection'), {
      inputPattern: /\S+/,
      inputErrorMessage: t('common.nameRequired'),
    })
    name = value
    questionName.value = value
  }
  const data: Partial<Chart> = {
    name,
    collectionId: collectionId.value,
    queryJson: JSON.stringify({
      sourceId: sourceId.value,
      sql: sql.value,
      parameters: alignSqlParameters(sql.value, sqlParameters.value),
      namedParameters: buildNamedPayload(),
    }),
    chartType: chartType.value || 'table',
    configJson: mergeChartConfig({}, chartEncoding.value, chartDrillPath.value, chartTableStyle.value),
    dataSourceId: sourceId.value,
  }
  saving.value = true
  try {
    const created = await chartApi.create(data)
    ElMessage.success(t('sql.savedTo', { name: selectedCollectionName.value || collectionId.value }))
    await router.push(`/questions/${created.id}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('common.saveFailed'))
  } finally {
    saving.value = false
  }
}

function onEditorKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
    event.preventDefault()
    if (!running.value) void run()
  }
}

function formatSqlInEditor() {
  if (!sqlEditorRef.value?.format()) {
    ElMessage.warning(t('sqlEditor.formatFailed'))
  }
}

function insertSqlText(text: string) {
  sqlEditorRef.value?.insertText(text)
}

watch(sql, () => {
  const named = alignNamedParameters(sql.value, namedSqlParameters)
  for (const key of Object.keys(namedSqlParameters)) {
    if (!(key in named)) delete namedSqlParameters[key]
  }
  for (const [key, value] of Object.entries(named)) {
    if (!(key in namedSqlParameters)) namedSqlParameters[key] = String(value ?? '')
  }
  sqlParameters.value = alignSqlParameters(sql.value, sqlParameters.value).map((item) => String(item ?? ''))
})
watch(() => route.query.sourceId, (value) => {
  if (typeof value === 'string') sourceId.value = value
})

watch(() => route.query.collectionId, (value) => {
  if (typeof value === 'string') collectionId.value = value
})

watch(() => route.query.name, (value) => {
  if (typeof value === 'string') questionName.value = value
})

watch(editorFullscreen, async () => {
  await nextTick()
  sqlEditorRef.value?.requestMeasure?.()
})

watch(sourceId, async (value) => {
  completionPayload.value = null
  editorSchema.value = {}
  if (value !== undefined) await loadCompletionSchema(value)
})

onMounted(load)
onBeforeUnmount(() => {
  window.clearTimeout(timer)
  stopClock()
})
</script>

<template>
  <div class="page sql-page">
    <div class="page-header">
      <div class="title-block">
        <h1 class="page-title">{{ t('sql.title') }}</h1>
        <p class="page-subtitle">
          {{ t('sql.subtitle') }}
          <template v-if="selectedCollectionName">{{ t('sql.currentCollection', { name: selectedCollectionName }) }}</template>
        </p>
      </div>
      <div class="header-actions">
        <el-button @click="$router.push('/databases')">{{ t('sql.browse') }}</el-button>
        <el-button
          v-if="canSave"
          :loading="saving"
          :disabled="!sourceId || !sql.trim()"
          @click="saveQuestion"
        >{{ t('sql.saveToCollection') }}</el-button>
        <el-button
          v-if="userStore.hasPermission('query:raw')"
          type="primary"
          :loading="running"
          :disabled="!sourceId"
          @click="run"
        >{{ t('sql.run') }}</el-button>
        <el-button v-if="running" type="danger" @click="cancel">{{ t('common.cancel') }}</el-button>
      </div>
    </div>

    <el-alert
      v-if="!userStore.hasPermission('query:raw')"
      class="perm-alert"
      type="warning"
      :closable="false"
      show-icon
      :title="t('sql.noPermissionTitle')"
      :description="t('sql.noPermissionHint')"
    />

    <template v-else>
      <section class="tip-panel">
        <div class="tip-head">
          <div>
            <strong>{{ t('sql.tips') }}</strong>
            <span class="tip-head-meta">{{ t('sql.readonlyLimit', { n: QUERY_RESULT_DISPLAY_LIMIT }) }}</span>
          </div>
          <el-button link type="primary" @click="tipsCollapsed = !tipsCollapsed">
            {{ tipsCollapsed ? t('sql.expand') : t('sql.collapse') }}
          </el-button>
        </div>
        <div v-show="!tipsCollapsed" class="tip-grid">
          <div class="tip-card tip-info">
            <div class="tip-label">{{ t('sql.autocomplete') }}</div>
            <p>{{ t('sql.autocompleteHint') }}</p>
          </div>
          <div class="tip-card tip-ok">
            <div class="tip-label">{{ t('sql.shortcut') }}</div>
            <p>{{ t('sql.shortcutHint') }}</p>
          </div>
          <div class="tip-card tip-warn">
            <div class="tip-label">{{ t('sql.metadata') }}</div>
            <p>
              <template v-if="schemaLoading">{{ t('sql.loadingMeta') }}</template>
              <template v-else-if="!sourceId">{{ t('sql.selectSourceMeta') }}</template>
              <template v-else-if="schemaTableCount === 0">
                {{ t('sql.noMeta') }}
              </template>
              <template v-else>{{ t('sql.metaReady', { n: schemaTableCount }) }}</template>
            </p>
          </div>
          <div class="tip-card tip-info">
            <div class="tip-label">{{ t('sql.crossDb') }}</div>
            <p>{{ catalogHint }}</p>
          </div>
        </div>
      </section>

      <section
        ref="editorPanelRef"
        class="editor-panel"
        :class="{ 'is-fullscreen': editorFullscreen }"
      >
        <div class="panel-toolbar">
          <div class="source-row">
            <span class="field-label">{{ t('dataSource.title') }}</span>
            <el-select
              v-model="sourceId"
              filterable
              :placeholder="t('sql.selectSource')"
              class="source-select"
            >
              <el-option
                v-for="item in sources"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              >
                <span>{{ item.name }}</span>
                <span class="option-meta">{{ item.dialect || 'MYSQL' }}</span>
              </el-option>
            </el-select>
            <el-tag v-if="currentSource" size="small" effect="plain" type="info">
              {{ currentDialect }}
            </el-tag>
            <el-tag v-if="schemaLoading" size="small" type="warning">{{ t('sql.loadingSuggest') }}</el-tag>
            <el-tag v-else-if="sourceId && schemaTableCount > 0" size="small" type="success" effect="plain">
              {{ t('sql.tablesSuggest', { n: schemaTableCount }) }}
            </el-tag>
          </div>
          <div class="run-row">
            <el-tag v-if="task" :type="statusType" effect="light">{{ displayLabel(task.status) }}</el-tag>
            <span v-if="elapsedText" class="elapsed">{{ t('sql.duration') }} {{ elapsedText }}</span>
            <el-button
              plain
              :title="t('sql.fullscreenHint')"
              @click="toggleEditorFullscreen"
            >
              {{ editorFullscreen ? t('sql.exitFullscreen') : t('sql.fullscreen') }}
            </el-button>
            <el-button plain :disabled="!sql.trim()" :title="t('sql.formatHint')" @click="formatSqlInEditor">
              {{ t('sql.format') }}
            </el-button>
            <el-button type="primary" :loading="running" :disabled="!sourceId" @click="run">{{ t('sql.execute') }}</el-button>
            <el-button v-if="running" type="danger" plain @click="cancel">{{ t('common.cancel') }}</el-button>
          </div>
        </div>

        <div class="editor-row">
          <div class="editor-box" @keydown="onEditorKeydown">
            <SqlEditor
              ref="sqlEditorRef"
              v-model="sql"
              :dialect="currentDialect"
              :jdbc-url="currentSource?.jdbcUrl"
              :schema="editorSchema"
              :default-schema="editorDefaultSchema"
              :placeholder-text="editorPlaceholder"
              :show-fullscreen="false"
            />
          </div>
          <MetadataBrowsePanel
            :source-id="sourceId"
            :default-schema="editorDefaultSchema"
            @insert="insertSqlText"
          />
        </div>
        <div v-if="namedParamNames.length || sqlParameters.length" class="sql-params">
          <div class="sql-params-title">{{ t('sql.sqlParams') }}</div>
          <div v-for="name in namedParamNames" :key="name" class="sql-param-row">
            <el-input v-model="namedSqlParameters[name]" :placeholder="`:${name}`">
              <template #prepend>{{ name }}</template>
            </el-input>
          </div>
          <div v-for="(_, index) in sqlParameters" :key="`pos-${index}`" class="sql-param-row">
            <el-input v-model="sqlParameters[index]" :placeholder="t('sql.paramN', { n: index + 1 })" />
          </div>
        </div>
      </section>

      <section v-if="canSave" class="save-panel-block">
        <div class="save-head">
          <div>
            <h2 class="section-title">{{ t('sql.saveToCollection') }}</h2>
            <p class="section-meta">{{ t('sql.saveHint') }}</p>
          </div>
          <el-button type="primary" :loading="saving" :disabled="!sourceId || !sql.trim()" @click="saveQuestion">
            {{ t('common.save') }}
          </el-button>
        </div>
        <div class="save-row">
          <el-input v-model="questionName" :placeholder="t('sql.chartName')" class="save-name" />
          <el-select v-model="collectionId" filterable :placeholder="t('sql.selectCollection')" class="save-collection">
            <el-option v-for="item in collections" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <el-select v-model="chartType" class="save-chart">
            <el-option
              v-for="option in chartTypeOptionList"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </div>
      </section>

      <section v-if="result" class="result-panel">
        <div class="result-head">
          <div>
            <h2 class="section-title">{{ t('sql.result') }}</h2>
            <p class="section-meta">
              {{ t('sql.showRows', { n: resultCount }) }}
              <template v-if="elapsedText"> · {{ t('sql.duration') }} {{ elapsedText }}</template>
            </p>
          </div>
          <div v-if="canExport" class="result-actions">
            <el-button @click="createExport('CSV')">{{ t('sql.exportCsv') }}</el-button>
            <el-button @click="createExport('XLSX')">{{ t('sql.exportXlsx') }}</el-button>
          </div>
        </div>

        <QueryResultTable :result="result" :table-style="chartTableStyle" :max-height="420" />
        <div v-if="canSave" class="encoding-block">
          <el-select v-model="chartType" style="width:150px;margin-right:8px">
            <el-option
              v-for="option in chartTypeOptionList"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <TableStyleForm
            v-if="chartType === 'table'"
            v-model="chartTableStyle"
            :columns="result.columns"
          />
          <ChartEncodingForm
            v-model="chartEncoding"
            v-model:drill-path="chartDrillPath"
            :columns="result.columns"
            :chart-type="chartType"
          />
        </div>
        <ChartPreview
          v-if="chartType !== 'table'"
          :result="result"
          :type="chartType"
          :option="JSON.parse(mergeChartConfig({}, chartEncoding, chartDrillPath, chartTableStyle))"
        />
      </section>
    </template>
  </div>
</template>

<style scoped>
.sql-page { display: flex; flex-direction: column; gap: 16px; }
.title-block { display: flex; flex-direction: column; gap: 4px; }
.page-subtitle { margin: 0; color: var(--omni-muted); font-size: 13px; }
.header-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.perm-alert { margin: 0; }

.tip-panel,
.editor-panel,
.save-panel-block,
.result-panel {
  background: var(--omni-card);
  border: 1px solid var(--omni-border);
  border-radius: 10px;
  padding: 16px;
}
.encoding-block { margin: 12px 0; display: flex; flex-direction: column; gap: 8px; }

.tip-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 0;
}
.tip-head strong { font-size: 14px; color: #111827; }
.tip-head-meta {
  margin-left: 10px;
  color: var(--omni-muted);
  font-size: 12px;
}
.tip-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}
.tip-card {
  border-radius: 8px;
  padding: 12px 14px;
  border: 1px solid transparent;
}
.tip-label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
  margin-bottom: 6px;
}
.tip-card p {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: #374151;
}
.tip-info {
  background: #eff6ff;
  border-color: #bfdbfe;
}
.tip-info .tip-label { color: #1d4ed8; }
.tip-ok {
  background: #ecfdf5;
  border-color: #a7f3d0;
}
.tip-ok .tip-label { color: #047857; }
.tip-warn {
  background: #fffbeb;
  border-color: #fde68a;
}
.tip-warn .tip-label { color: #b45309; }

kbd {
  display: inline-block;
  padding: 1px 6px;
  border: 1px solid #d1d5db;
  border-bottom-width: 2px;
  border-radius: 4px;
  background: #fff;
  font-size: 11px;
  font-family: Consolas, "Courier New", monospace;
  color: #111827;
}
code {
  padding: 1px 5px;
  border-radius: 4px;
  background: rgba(17, 24, 39, 0.06);
  font-size: 12px;
  font-family: Consolas, "Courier New", monospace;
}

.panel-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.source-row,
.run-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.elapsed {
  color: var(--omni-muted);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
.field-label {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}
.source-select { width: 280px; }
.option-meta {
  float: right;
  color: var(--omni-muted);
  font-size: 12px;
  margin-left: 16px;
}
.editor-box {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--omni-border);
  border-radius: 8px;
  overflow: hidden;
  background: var(--omni-editor-bg);
}
.editor-row {
  display: flex;
  gap: 12px;
  align-items: stretch;
  min-width: 0;
  min-height: 360px;
  max-height: min(62vh, 560px);
}
.editor-row :deep(.meta-browse) {
  max-height: 100%;
  overflow: hidden;
}
.editor-panel.is-fullscreen,
.editor-panel:fullscreen,
.editor-panel:-webkit-full-screen {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  margin: 0;
  border-radius: 0;
  background: var(--omni-bg);
  overflow: hidden;
}
.editor-panel.is-fullscreen .panel-toolbar,
.editor-panel:fullscreen .panel-toolbar,
.editor-panel:-webkit-full-screen .panel-toolbar {
  flex-shrink: 0;
}
.editor-panel.is-fullscreen .editor-row,
.editor-panel:fullscreen .editor-row,
.editor-panel:-webkit-full-screen .editor-row {
  flex: 1;
  min-height: 0;
  max-height: none;
}
.editor-panel.is-fullscreen .editor-box,
.editor-panel:fullscreen .editor-box,
.editor-panel:-webkit-full-screen .editor-box {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
}
.editor-panel.is-fullscreen .editor-box :deep(.sql-editor-shell),
.editor-panel:fullscreen .editor-box :deep(.sql-editor-shell),
.editor-panel:-webkit-full-screen .editor-box :deep(.sql-editor-shell),
.editor-panel.is-fullscreen .editor-box :deep(.sql-editor),
.editor-panel:fullscreen .editor-box :deep(.sql-editor),
.editor-panel:-webkit-full-screen .editor-box :deep(.sql-editor),
.editor-panel.is-fullscreen .editor-box :deep(.cm-editor),
.editor-panel:fullscreen .editor-box :deep(.cm-editor),
.editor-panel:-webkit-full-screen .editor-box :deep(.cm-editor) {
  flex: 1;
  min-height: 0;
  height: 100%;
}
.editor-panel.is-fullscreen .sql-params,
.editor-panel:fullscreen .sql-params,
.editor-panel:-webkit-full-screen .sql-params {
  flex-shrink: 0;
  margin-top: 12px;
  max-height: 30%;
  overflow: auto;
}
@media (max-width: 900px) {
  .editor-row {
    flex-direction: column;
    max-height: none;
  }
  .editor-row :deep(.meta-browse) {
    width: 100%;
    max-width: none;
    flex-basis: auto;
    max-height: 360px;
  }
}

.result-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.section-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
}
.section-meta {
  margin: 4px 0 0;
  color: var(--omni-muted);
  font-size: 12px;
}
.result-actions { display: flex; gap: 8px; flex-wrap: wrap; }

.save-panel-block .save-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.save-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}
.save-name { width: 240px; }
.save-collection { width: 220px; }
.save-chart { width: 140px; }
.sql-params { margin-top: 12px; }
.sql-params-title { font-size: 13px; margin-bottom: 8px; color: var(--omni-muted); }
.sql-param-row { margin-bottom: 8px; max-width: 420px; }

@media (max-width: 1100px) {
  .tip-grid { grid-template-columns: 1fr; }
  .source-select { width: 220px; }
}
</style>

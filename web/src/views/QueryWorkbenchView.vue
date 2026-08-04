<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { promptBox } from '@/i18n/dialog'
import { useRoute, useRouter } from 'vue-router'
import { chartApi, collectionApi, dataSourceApi, datasetApi, exportApi, metricApi, queryApi } from '@/api'
import { displayLabel } from '@/display'
import { useUserStore } from '@/stores/user'
import type { Chart, Collection, DataSource, Dataset, Id, Metric, QueryResult, QuerySnapshot, QuerySubmission, SemanticQuery } from '@/types'
import SqlEditor from '@/components/SqlEditor.vue'
import ChartPreview from '@/components/ChartPreview.vue'
import QueryResultTable from '@/components/QueryResultTable.vue'
import {
  inferDefaultSchema,
  toEditorSchema,
  type CompletionSchemaPayload,
  type EditorSqlSchema,
} from '@/sql/schema'
import { resolveSqlDialect } from '@/sql/dialects'
import { QUERY_RESULT_DISPLAY_LIMIT } from '@/query/limits'
import { formatDuration } from '@/query/duration'
import { alignSqlParameters } from '@/sql/parameters'
import { chartTypeOptions, mergeChartConfig, parseChartConfig, type ChartEncoding } from '@/dashboard/config'
import ChartEncodingForm from '@/components/ChartEncodingForm.vue'

const { t } = useI18n()
const userStore = useUserStore()
const chartTypeOptionList = computed(() => chartTypeOptions())
const route = useRoute()
const router = useRouter()
const mode = ref<'visual' | 'sql'>('visual')
const datasets = ref<Dataset[]>([])
const sources = ref<DataSource[]>([])
const collections = ref<Collection[]>([])
const sourceId = ref<Id>()
const sql = ref('')
const sqlEditorRef = ref<{ format: () => boolean }>()
const sqlParameters = ref<string[]>([])
const editorSchema = ref<EditorSqlSchema>({})
const completionPayload = ref<CompletionSchemaPayload | null>(null)
const definition = reactive<SemanticQuery>({
  datasetId: '', dimensions: [], metrics: [], metricIds: [], sorts: [], limit: 100,
})
const filters = reactive<Array<{ field: string; operator: 'EQ' | 'NE' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'LIKE' | 'IN'; value: string }>>([])
const businessMetrics = ref<Metric[]>([])
const task = ref<QuerySnapshot>()
const result = ref<QueryResult>()
const chartType = ref('table')
const chartEncoding = ref<ChartEncoding>({})
const chartDrillPath = ref<string[]>([])
const chartConfigBase = ref<Record<string, unknown>>({})
const questionName = ref('')
const questionDescription = ref('')
const collectionId = ref<Id | undefined>(
  typeof route.query.collectionId === 'string' ? route.query.collectionId : undefined,
)
const questionId = computed(() =>
  typeof route.query.questionId === 'string' ? route.query.questionId : undefined)
const editingExisting = computed(() => !!questionId.value)
const nowMs = ref(Date.now())
const running = computed(() => task.value?.status === 'QUEUED' || task.value?.status === 'RUNNING')
const currentDataset = computed(() =>
  datasets.value.find((item) => String(item.id) === String(definition.datasetId)))
const dimensions = computed(() => currentDataset.value?.fields.filter((field) => field.fieldType === 'DIMENSION') || [])
const metrics = computed(() => currentDataset.value?.fields.filter((field) => field.fieldType === 'METRIC') || [])
const selectedMetricIds = computed({
  get: () => (definition.metricIds || []).map(String),
  set: (values: string[]) => {
    definition.metricIds = values
  },
})
const currentSource = computed(() => sources.value.find((item) => String(item.id) === String(sourceId.value)))
const currentDialect = computed(() => resolveSqlDialect(currentSource.value?.dialect, currentSource.value?.jdbcUrl).id)
const editorDefaultSchema = computed(() =>
  inferDefaultSchema(completionPayload.value, currentSource.value?.defaultDatabase))
const elapsedMs = computed(() => {
  if (!task.value) return undefined
  if (task.value.durationMs != null) return task.value.durationMs
  if (task.value.startedAtMs != null && running.value) return Math.max(0, nowMs.value - task.value.startedAtMs)
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
  try {
    const payload = await dataSourceApi.completionSchema(id)
    completionPayload.value = payload
    editorSchema.value = toEditorSchema(payload)
    const source = sources.value.find((item) => String(item.id) === String(id))
    if (source && payload.dialect) source.dialect = payload.dialect
  } catch {
    completionPayload.value = null
    editorSchema.value = {}
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
  try {
    const [datasetList, sourceList, tree] = await Promise.all([
      datasetApi.list(), dataSourceApi.list(), collectionApi.tree(),
    ])
    datasets.value = datasetList
    sources.value = sourceList
    collections.value = flatten(tree)
    if (!collectionId.value) collectionId.value = collections.value[0]?.id
    if (route.query.mode === 'sql' && userStore.hasPermission('query:raw')) {
      mode.value = 'sql'
    }
    if (typeof route.query.sourceId === 'string') {
      sourceId.value = route.query.sourceId
      mode.value = userStore.hasPermission('query:raw') ? 'sql' : mode.value
    }
    if (mode.value === 'sql' && sourceId.value !== undefined) await loadCompletionSchema(sourceId.value)
    if (questionId.value) await hydrateQuestion(questionId.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('workbench.loadFailed'))
  }
}

async function hydrateQuestion(id: string) {
  const chart = await chartApi.get(id)
  questionName.value = chart.name
  questionDescription.value = chart.description || ''
  chartType.value = chart.chartType || 'table'
  const parsedConfig = parseChartConfig(chart.configJson)
  chartEncoding.value = parsedConfig.encoding || {}
  chartDrillPath.value = parsedConfig.drillPath || []
  const { encoding: _ignored, drillPath: _drill, ...rest } = parsedConfig
  chartConfigBase.value = rest
  collectionId.value = chart.collectionId
  const submission = JSON.parse(chart.queryJson || '{}') as QuerySubmission
  if (submission.sql) {
    mode.value = 'sql'
    sourceId.value = submission.sourceId
    sql.value = submission.sql
    sqlParameters.value = alignSqlParameters(submission.sql, (submission.parameters || []).map(String)) as string[]
    if (submission.sourceId !== undefined) await loadCompletionSchema(submission.sourceId)
  } else if (submission.query) {
    mode.value = 'visual'
    Object.assign(definition, {
      ...submission.query,
      datasetId: String(submission.query.datasetId ?? ''),
      metricIds: (submission.query.metricIds || []).map(String),
    })
    filters.splice(0, filters.length, ...((submission.query.filter?.children || []) as typeof filters))
    await loadBusinessMetrics(definition.datasetId)
  }
}

async function loadBusinessMetrics(datasetId?: Id) {
  if (datasetId === undefined || datasetId === null || datasetId === '') {
    businessMetrics.value = []
    return
  }
  try {
    businessMetrics.value = await metricApi.list({ modelId: datasetId })
  } catch {
    businessMetrics.value = []
  }
}

watch(() => definition.datasetId, (id, previous) => {
  if (previous != null && String(previous) !== '' && String(previous) !== String(id || '')) {
    definition.metricIds = []
  }
  void loadBusinessMetrics(id)
})

function syncSqlParameters() {
  sqlParameters.value = alignSqlParameters(sql.value, sqlParameters.value).map((item) => String(item ?? ''))
}

watch(sql, syncSqlParameters)

function payload(): QuerySubmission {
  return mode.value === 'sql'
    ? {
      sourceId: sourceId.value,
      sql: sql.value,
      parameters: alignSqlParameters(sql.value, sqlParameters.value),
    }
    : {
      query: {
        ...JSON.parse(JSON.stringify(definition)),
        filter: filters.length ? { logic: 'AND', children: filters.map((item) => ({ ...item })) } : undefined,
      },
    }
}

async function run() {
  if (mode.value === 'sql' ? (!sourceId.value || !sql.value.trim()) : !definition.datasetId) {
    return ElMessage.warning(t('workbench.needConfig'))
  }
  result.value = undefined
  try {
    const submitted = await queryApi.submit(payload())
    task.value = { queryId: submitted.queryId, status: 'QUEUED', startedAtMs: Date.now() }
    startClock()
    await poll()
  } catch (error) {
    stopClock()
    ElMessage.error(error instanceof Error ? error.message : t('workbench.submitFailed'))
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
      ElMessage.error(task.value.error || t('workbench.execFailed'))
    } else if (task.value.status === 'CANCELLED') {
      stopClock()
    } else {
      timer = window.setTimeout(poll, 1000)
    }
  } catch (error) {
    stopClock()
    ElMessage.error(error instanceof Error ? error.message : t('workbench.statusFailed'))
  }
}

async function cancel() {
  if (!task.value) return
  try {
    await queryApi.cancel(task.value.queryId)
    task.value = { ...task.value, status: 'CANCELLED', durationMs: elapsedMs.value }
    stopClock()
    ElMessage.info(t('workbench.cancelled'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('workbench.cancelFailed'))
  }
}

function formatSqlInEditor() {
  if (!sqlEditorRef.value?.format()) {
    ElMessage.warning(t('sqlEditor.formatFailed'))
  }
}

async function saveQuestion() {
  if (!result.value) return ElMessage.warning(t('workbench.runFirst'))
  if (mode.value === 'visual' && !definition.datasetId) return ElMessage.warning(t('workbench.needModel'))
  if (mode.value === 'sql' && (!sourceId.value || !sql.value.trim())) return ElMessage.warning(t('workbench.needSqlConfig'))
  if (!userStore.hasPermission('chart:create')) return ElMessage.warning(t('chart.noCreatePermission'))

  let name = questionName.value.trim()
  if (!name) {
    const { value } = await promptBox(t('chart.namePrompt'), t('chart.saveAs'), {
      inputPattern: /\S+/,
      inputErrorMessage: t('common.nameRequired'),
    })
    name = value
  }

  const data: Partial<Chart> = {
    name,
    description: questionDescription.value,
    collectionId: collectionId.value,
    queryJson: JSON.stringify(payload()),
    chartType: chartType.value || 'table',
    configJson: mergeChartConfig(chartConfigBase.value, chartEncoding.value, chartDrillPath.value),
    datasetId: mode.value === 'visual' ? definition.datasetId : undefined,
    dataSourceId: mode.value === 'sql' ? sourceId.value : undefined,
  }

  try {
    const saved = questionId.value
      ? await chartApi.update(questionId.value, data)
      : await chartApi.create(data)
    ElMessage.success(t('chart.saved'))
    await router.push(`/questions/${saved.id}`)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('chart.saveFailed'))
  }
}

async function createExport(format: 'CSV' | 'XLSX') {
  if (!task.value || task.value.status !== 'SUCCEEDED') return
  try {
    const blob = await exportApi.download(task.value.queryId, format)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = t('workbench.resultFile', { format: format.toLowerCase() })
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('workbench.exportFailed'))
  }
}

onMounted(load)
onBeforeUnmount(() => {
  window.clearTimeout(timer)
  stopClock()
})

watch(sourceId, async (value) => {
  if (mode.value === 'sql' && value !== undefined) await loadCompletionSchema(value)
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">{{ editingExisting ? t('chart.edit') : t('workbench.title') }}</h1>
      <el-radio-group v-model="mode">
        <el-radio-button value="visual">{{ t('workbench.visual') }}</el-radio-button>
        <el-radio-button v-if="userStore.hasPermission('query:raw')" value="sql">{{ t('workbench.sqlMode') }}</el-radio-button>
      </el-radio-group>
    </div>
    <el-card>
      <el-form label-width="90px" class="meta-form">
        <el-form-item :label="t('chart.name')"><el-input v-model="questionName" :placeholder="t('workbench.namePlaceholder')" /></el-form-item>
        <el-form-item :label="t('common.description')"><el-input v-model="questionDescription" :placeholder="t('common.optional')" /></el-form-item>
        <el-form-item :label="t('common.collection')">
          <el-select v-model="collectionId" class="full-width" clearable>
            <el-option v-for="item in collections" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template v-if="mode === 'visual'">
        <el-form label-width="90px">
          <el-form-item :label="t('metric.model')">
            <el-select v-model="definition.datasetId" class="full-width">
              <el-option v-for="item in datasets" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="displayLabel('DIMENSION')">
            <el-select v-model="definition.dimensions" multiple class="full-width">
              <el-option v-for="item in dimensions" :key="item.name" :label="item.name" :value="item.name" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('workbench.modelFieldMetrics')">
            <el-select v-model="definition.metrics" multiple class="full-width" clearable>
              <el-option v-for="item in metrics" :key="item.name" :label="`${item.name}（${displayLabel(item.aggregation)}）`" :value="item.name" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('workbench.businessMetrics')">
            <el-select v-model="selectedMetricIds" multiple class="full-width" clearable filterable :placeholder="t('workbench.selectPublishedMetrics')">
              <el-option
                v-for="item in businessMetrics"
                :key="item.id"
                :label="`${item.name}（${displayLabel(item.aggregation)}）`"
                :value="String(item.id)"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('workbench.filters')">
            <div class="full-width">
              <div v-for="(item,index) in filters" :key="index" class="row">
                <el-input v-model="item.field" :placeholder="t('workbench.field')" />
                <el-select v-model="item.operator" :placeholder="t('workbench.operator')">
                  <el-option :label="displayLabel('EQ')" value="EQ" />
                  <el-option :label="displayLabel('NE')" value="NE" />
                  <el-option :label="displayLabel('GT')" value="GT" />
                  <el-option :label="displayLabel('GTE')" value="GTE" />
                  <el-option :label="displayLabel('LT')" value="LT" />
                  <el-option :label="displayLabel('LTE')" value="LTE" />
                  <el-option :label="displayLabel('LIKE')" value="LIKE" />
                  <el-option :label="displayLabel('IN')" value="IN" />
                </el-select>
                <el-input v-model="item.value" :placeholder="t('workbench.value')" />
                <el-button type="danger" link @click="filters.splice(index,1)">{{ t('common.delete') }}</el-button>
              </div>
              <el-button link type="primary" @click="filters.push({field:'',operator:'EQ',value:''})">{{ t('workbench.addFilter') }}</el-button>
            </div>
          </el-form-item>
          <el-form-item :label="t('workbench.sorts')">
            <div class="full-width">
              <div v-for="(item,index) in definition.sorts" :key="index" class="row">
                <el-input v-model="item.field" :placeholder="t('workbench.field')" />
                <el-select v-model="item.direction">
                  <el-option :label="displayLabel('ASC')" value="ASC" /><el-option :label="displayLabel('DESC')" value="DESC" />
                </el-select>
                <el-button type="danger" link @click="definition.sorts.splice(index,1)">{{ t('common.delete') }}</el-button>
              </div>
              <el-button link type="primary" @click="definition.sorts.push({field:'',direction:'ASC'})">{{ t('workbench.addSort') }}</el-button>
            </div>
          </el-form-item>
        </el-form>
      </template>
      <template v-else>
        <el-select v-model="sourceId" :placeholder="t('workbench.selectSource')" class="full-width sql-source">
          <el-option v-for="item in sources" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <p v-if="currentSource" class="catalog-hint">
          <template v-if="currentSource.defaultDatabase">
            {{ t('workbench.defaultDbHint', { db: currentSource.defaultDatabase }) }}
          </template>
          <template v-else>{{ t('workbench.noDefaultDbHint') }}</template>
        </p>
        <SqlEditor
          ref="sqlEditorRef"
          v-model="sql"
          :dialect="currentDialect"
          :jdbc-url="currentSource?.jdbcUrl"
          :schema="editorSchema"
          :default-schema="editorDefaultSchema"
        />
        <div v-if="sqlParameters.length" class="sql-params">
          <div class="sql-params-title">{{ t('workbench.sqlParams') }}</div>
          <div v-for="(_, index) in sqlParameters" :key="index" class="row">
            <el-input v-model="sqlParameters[index]" :placeholder="t('workbench.paramN', { n: index + 1 })" />
          </div>
        </div>
      </template>
      <div class="toolbar bottom">
        <span>{{ t('workbench.rowLimit') }}</span>
        <el-input-number v-model="definition.limit" :min="1" :max="QUERY_RESULT_DISPLAY_LIMIT" />
        <el-button v-if="mode === 'sql'" plain :disabled="!sql.trim()" :title="t('sql.formatHint')" @click="formatSqlInEditor">
          {{ t('sql.format') }}
        </el-button>
        <el-button type="primary" :loading="running" @click="run">{{ t('workbench.run') }}</el-button>
        <el-button v-if="running" type="danger" @click="cancel">{{ t('common.cancel') }}</el-button>
        <span v-if="task">{{ t('workbench.status') }}{{ displayLabel(task.status) }}</span>
        <span v-if="elapsedText" class="elapsed">{{ t('workbench.duration') }} {{ elapsedText }}</span>
      </div>
    </el-card>
    <el-card v-if="result" class="result-card">
      <div class="toolbar">
        <strong>
          {{ t('workbench.resultRows', { n: Math.min(result.rows.length, QUERY_RESULT_DISPLAY_LIMIT) }) }}
          <template v-if="elapsedText"> · {{ t('workbench.duration') }} {{ elapsedText }}</template>
          <template v-if="result.rows.length > QUERY_RESULT_DISPLAY_LIMIT">{{ t('workbench.truncated') }}</template>
          ）
        </strong>
        <div>
          <el-button @click="createExport('CSV')">{{ t('sql.exportCsv') }}</el-button>
          <el-button @click="createExport('XLSX')">{{ t('sql.exportXlsx') }}</el-button>
        </div>
      </div>
      <QueryResultTable :result="result" :max-height="380" />
      <el-divider />
      <div v-if="userStore.hasPermission('chart:create')" class="toolbar">
        <span>{{ t('workbench.displayType') }}</span>
        <el-select v-model="chartType" style="width:150px">
          <el-option
            v-for="option in chartTypeOptionList"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button type="primary" @click="saveQuestion">{{ editingExisting ? t('workbench.saveChanges') : t('chart.saveAs') }}</el-button>
      </div>
      <ChartEncodingForm
        v-if="result"
        v-model="chartEncoding"
        v-model:drill-path="chartDrillPath"
        :columns="result.columns"
        :chart-type="chartType"
      />
      <ChartPreview
        v-if="chartType !== 'table'"
        :result="result"
        :type="chartType"
        :option="JSON.parse(mergeChartConfig(chartConfigBase, chartEncoding, chartDrillPath))"
      />
    </el-card>
  </div>
</template>

<style scoped>
.meta-form { margin-bottom: 8px; }
.row { display: flex; gap: 10px; margin-bottom: 8px; }
.row > * { flex: 1; }
.row .el-button { flex: 0 0 auto; }
.bottom { margin: 16px 0 0; }
.elapsed { color: #6b7280; font-variant-numeric: tabular-nums; }
.result-card { margin-top: 18px; }
.result-card .toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.sql-source { margin-bottom: 8px; }
.catalog-hint {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.5;
  color: #6b7280;
}
.sql-params { margin-top: 12px; }
.sql-params-title { font-size: 13px; margin-bottom: 8px; color: #6b7280; }
</style>

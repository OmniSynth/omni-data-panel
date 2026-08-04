<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { chartApi, collectionApi, dataSourceApi, datasetApi, exportApi, queryApi } from '@/api'
import { displayLabel } from '@/display'
import { useUserStore } from '@/stores/user'
import type { Chart, Collection, DataSource, Dataset, Id, QueryResult, QuerySnapshot, QuerySubmission, SemanticQuery } from '@/types'
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

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const mode = ref<'visual' | 'sql'>('visual')
const datasets = ref<Dataset[]>([])
const sources = ref<DataSource[]>([])
const collections = ref<Collection[]>([])
const sourceId = ref<Id>()
const sql = ref('')
const editorSchema = ref<EditorSqlSchema>({})
const completionPayload = ref<CompletionSchemaPayload | null>(null)
const definition = reactive<SemanticQuery>({ datasetId: '', dimensions: [], metrics: [], sorts: [], limit: 100 })
const filters = reactive<Array<{ field: string; operator: 'EQ' | 'NE' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'LIKE' | 'IN'; value: string }>>([])
const task = ref<QuerySnapshot>()
const result = ref<QueryResult>()
const chartType = ref('table')
const questionName = ref('')
const questionDescription = ref('')
const collectionId = ref<Id | undefined>(
  typeof route.query.collectionId === 'string' ? route.query.collectionId : undefined,
)
const nowMs = ref(Date.now())
const running = computed(() => task.value?.status === 'QUEUED' || task.value?.status === 'RUNNING')
const currentDataset = computed(() => datasets.value.find((item) => item.id === definition.datasetId))
const dimensions = computed(() => currentDataset.value?.fields.filter((field) => field.fieldType === 'DIMENSION') || [])
const metrics = computed(() => currentDataset.value?.fields.filter((field) => field.fieldType === 'METRIC') || [])
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
    const questionId = typeof route.query.questionId === 'string' ? route.query.questionId : undefined
    if (questionId) await hydrateQuestion(questionId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '工作台加载失败')
  }
}

async function hydrateQuestion(id: string) {
  const chart = await chartApi.get(id)
  questionName.value = chart.name
  questionDescription.value = chart.description || ''
  chartType.value = chart.chartType || 'table'
  collectionId.value = chart.collectionId
  const submission = JSON.parse(chart.queryJson || '{}') as QuerySubmission
  if (submission.sql) {
    mode.value = 'sql'
    sourceId.value = submission.sourceId
    sql.value = submission.sql
    if (submission.sourceId !== undefined) await loadCompletionSchema(submission.sourceId)
  } else if (submission.query) {
    mode.value = 'visual'
    Object.assign(definition, submission.query)
    filters.splice(0, filters.length, ...((submission.query.filter?.children || []) as typeof filters))
  }
}

function payload(): QuerySubmission {
  return mode.value === 'sql'
    ? { sourceId: sourceId.value, sql: sql.value, parameters: [] }
    : {
      query: {
        ...JSON.parse(JSON.stringify(definition)),
        filter: filters.length ? { logic: 'AND', children: filters.map((item) => ({ ...item })) } : undefined,
      },
    }
}

async function run() {
  if (mode.value === 'sql' ? (!sourceId.value || !sql.value.trim()) : !definition.datasetId) {
    return ElMessage.warning('请完成查询配置')
  }
  result.value = undefined
  try {
    const submitted = await queryApi.submit(payload())
    task.value = { queryId: submitted.queryId, status: 'QUEUED', startedAtMs: Date.now() }
    startClock()
    await poll()
  } catch (error) {
    stopClock()
    ElMessage.error(error instanceof Error ? error.message : '查询提交失败')
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
      ElMessage.error(task.value.error || '查询执行失败')
    } else if (task.value.status === 'CANCELLED') {
      stopClock()
    } else {
      timer = window.setTimeout(poll, 1000)
    }
  } catch (error) {
    stopClock()
    ElMessage.error(error instanceof Error ? error.message : '查询状态获取失败')
  }
}

async function cancel() {
  if (!task.value) return
  try {
    await queryApi.cancel(task.value.queryId)
    task.value = { ...task.value, status: 'CANCELLED', durationMs: elapsedMs.value }
    stopClock()
    ElMessage.info('查询已取消')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '取消失败')
  }
}

async function saveQuestion() {
  if (!result.value) return ElMessage.warning('请先执行查询')
  if (mode.value === 'visual' && !definition.datasetId) return ElMessage.warning('请选择模型')
  if (mode.value === 'sql' && (!sourceId.value || !sql.value.trim())) return ElMessage.warning('请完成 SQL 配置')
  if (!userStore.hasPermission('chart:create')) return ElMessage.warning('无创建问题权限')

  let name = questionName.value.trim()
  if (!name) {
    const { value } = await ElMessageBox.prompt('请输入问题名称', '保存为问题', {
      inputPattern: /\S+/,
      inputErrorMessage: '名称不能为空',
    })
    name = value
  }

  const data: Partial<Chart> = {
    name,
    description: questionDescription.value,
    collectionId: collectionId.value,
    queryJson: JSON.stringify(payload()),
    chartType: chartType.value || 'table',
    configJson: '{}',
    datasetId: mode.value === 'visual' ? definition.datasetId : undefined,
    dataSourceId: mode.value === 'sql' ? sourceId.value : undefined,
  }

  try {
    const questionId = typeof route.query.questionId === 'string' ? route.query.questionId : undefined
    const saved = questionId
      ? await chartApi.update(questionId, data)
      : await chartApi.create(data)
    ElMessage.success('问题已保存')
    await router.push(`/questions/${saved.id}`)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '问题保存失败')
  }
}

async function createExport(format: 'CSV' | 'XLSX') {
  if (!task.value || task.value.status !== 'SUCCEEDED') return
  try {
    const blob = await exportApi.download(task.value.queryId, format)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `查询结果.${format.toLowerCase()}`
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败')
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
      <h1 class="page-title">查询工作台</h1>
      <el-radio-group v-model="mode">
        <el-radio-button value="visual">可视化查询</el-radio-button>
        <el-radio-button v-if="userStore.hasPermission('query:raw')" value="sql">SQL 模式</el-radio-button>
      </el-radio-group>
    </div>
    <el-card>
      <el-form label-width="90px" class="meta-form">
        <el-form-item label="问题名称"><el-input v-model="questionName" placeholder="保存时使用" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="questionDescription" placeholder="可选" /></el-form-item>
        <el-form-item label="集合">
          <el-select v-model="collectionId" class="full-width" clearable>
            <el-option v-for="item in collections" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template v-if="mode === 'visual'">
        <el-form label-width="90px">
          <el-form-item label="模型">
            <el-select v-model="definition.datasetId" class="full-width">
              <el-option v-for="item in datasets" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="维度">
            <el-select v-model="definition.dimensions" multiple class="full-width">
              <el-option v-for="item in dimensions" :key="item.name" :label="item.name" :value="item.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="指标">
            <el-select v-model="definition.metrics" multiple class="full-width">
              <el-option v-for="item in metrics" :key="item.name" :label="`${item.name}（${displayLabel(item.aggregation)}）`" :value="item.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="过滤条件">
            <div class="full-width">
              <div v-for="(item,index) in filters" :key="index" class="row">
                <el-input v-model="item.field" placeholder="字段" />
                <el-select v-model="item.operator" placeholder="运算符">
                  <el-option label="等于" value="EQ" />
                  <el-option label="不等于" value="NE" />
                  <el-option label="大于" value="GT" />
                  <el-option label="大于等于" value="GTE" />
                  <el-option label="小于" value="LT" />
                  <el-option label="小于等于" value="LTE" />
                  <el-option label="包含" value="LIKE" />
                  <el-option label="属于" value="IN" />
                </el-select>
                <el-input v-model="item.value" placeholder="值" />
                <el-button type="danger" link @click="filters.splice(index,1)">删除</el-button>
              </div>
              <el-button link type="primary" @click="filters.push({field:'',operator:'EQ',value:''})">添加过滤</el-button>
            </div>
          </el-form-item>
          <el-form-item label="排序">
            <div class="full-width">
              <div v-for="(item,index) in definition.sorts" :key="index" class="row">
                <el-input v-model="item.field" placeholder="字段" />
                <el-select v-model="item.direction">
                  <el-option label="升序" value="ASC" /><el-option label="降序" value="DESC" />
                </el-select>
                <el-button type="danger" link @click="definition.sorts.splice(index,1)">删除</el-button>
              </div>
              <el-button link type="primary" @click="definition.sorts.push({field:'',direction:'ASC'})">添加排序</el-button>
            </div>
          </el-form-item>
        </el-form>
      </template>
      <template v-else>
        <el-select v-model="sourceId" placeholder="选择数据源" class="full-width sql-source">
          <el-option v-for="item in sources" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <p v-if="currentSource" class="catalog-hint">
          <template v-if="currentSource.defaultDatabase">
            未限定表名使用默认库 {{ currentSource.defaultDatabase }}；可用 库名.表名 跨库查询。
          </template>
          <template v-else>未设置默认库，请使用 库名.表名 编写 SQL。</template>
        </p>
        <SqlEditor
          v-model="sql"
          :dialect="currentDialect"
          :jdbc-url="currentSource?.jdbcUrl"
          :schema="editorSchema"
          :default-schema="editorDefaultSchema"
        />
      </template>
      <div class="toolbar bottom">
        <span>返回行数</span>
        <el-input-number v-model="definition.limit" :min="1" :max="QUERY_RESULT_DISPLAY_LIMIT" />
        <el-button type="primary" :loading="running" @click="run">执行查询</el-button>
        <el-button v-if="running" type="danger" @click="cancel">取消</el-button>
        <span v-if="task">状态：{{ displayLabel(task.status) }}</span>
        <span v-if="elapsedText" class="elapsed">耗时 {{ elapsedText }}</span>
      </div>
    </el-card>
    <el-card v-if="result" class="result-card">
      <div class="toolbar">
        <strong>
          查询结果（{{ Math.min(result.rows.length, QUERY_RESULT_DISPLAY_LIMIT) }} 行
          <template v-if="elapsedText"> · 耗时 {{ elapsedText }}</template>
          <template v-if="result.rows.length > QUERY_RESULT_DISPLAY_LIMIT">，已截断</template>
          ）
        </strong>
        <div>
          <el-button @click="createExport('CSV')">导出 CSV</el-button>
          <el-button @click="createExport('XLSX')">导出 XLSX</el-button>
        </div>
      </div>
      <QueryResultTable :result="result" :max-height="380" />
      <el-divider />
      <div v-if="userStore.hasPermission('chart:create')" class="toolbar">
        <span>展示类型</span>
        <el-select v-model="chartType" style="width:150px">
          <el-option label="表格" value="table" />
          <el-option label="柱状图" value="bar" />
          <el-option label="折线图" value="line" />
          <el-option label="饼图" value="pie" />
        </el-select>
        <el-button type="primary" @click="saveQuestion">保存为问题</el-button>
      </div>
      <ChartPreview v-if="chartType !== 'table'" :result="result" :type="chartType" />
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
</style>

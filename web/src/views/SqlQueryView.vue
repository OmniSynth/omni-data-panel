<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { chartApi, collectionApi, dataSourceApi, exportApi, queryApi } from '@/api'
import { displayLabel } from '@/display'
import { useUserStore } from '@/stores/user'
import type { Chart, Collection, DataSource, Id, QueryResult, QuerySnapshot } from '@/types'
import SqlEditor from '@/components/SqlEditor.vue'
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

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()

const sources = ref<DataSource[]>([])
const collections = ref<Collection[]>([])
const sourceId = ref<Id | undefined>(
  typeof route.query.sourceId === 'string' ? route.query.sourceId : undefined,
)
const sql = ref('SELECT 1')
const editorSchema = ref<EditorSqlSchema>({})
const completionPayload = ref<CompletionSchemaPayload | null>(null)
const schemaLoading = ref(false)
const collectionId = ref<Id | undefined>(
  typeof route.query.collectionId === 'string' ? route.query.collectionId : undefined,
)
const questionName = ref(typeof route.query.name === 'string' ? route.query.name : '')
const chartType = ref('table')
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
  if (database) return `未限定表名使用默认库 \`${database}\`；也可用 库名.表名 跨库查询。`
  if (currentSource.value) return '当前数据源未设置默认库，请使用 `库名.表名` 编写 SQL。'
  return '选择数据源后，可按默认库或 库名.表名 编写跨库 SQL。'
})
const editorPlaceholder = computed(() => {
  const database = currentSource.value?.defaultDatabase?.trim()
  if (database) return `在此编写 SQL，例如 SELECT * FROM your_table LIMIT 100（默认库 ${database}）`
  return '在此编写 SQL，例如 SELECT * FROM db_name.your_table LIMIT 100'
})
const schemaTableCount = computed(() => countCompletionTables(completionPayload.value))
const editorDefaultSchema = computed(() =>
  inferDefaultSchema(completionPayload.value, currentSource.value?.defaultDatabase))
const resultCount = computed(() => Math.min(result.value?.rows.length || 0, QUERY_RESULT_DISPLAY_LIMIT))
const selectedCollectionName = computed(() => {
  if (collectionId.value === undefined) return ''
  return collections.value.find((item) => String(item.id) === String(collectionId.value))?.name || ''
})
const canSave = computed(() => userStore.hasPermission('chart:create'))
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
    const [sourceList, tree] = await Promise.all([dataSourceApi.list(), collectionApi.tree()])
    sources.value = sourceList
    collections.value = flatten(tree)
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
    ElMessage.error(error instanceof Error ? error.message : 'SQL 查询页加载失败')
  }
}

async function run() {
  if (!sourceId.value) return ElMessage.warning('请选择数据源')
  if (!sql.value.trim()) return ElMessage.warning('请输入 SQL')
  result.value = undefined
  try {
    const submitted = await queryApi.submit({
      sourceId: sourceId.value,
      sql: sql.value,
      parameters: [],
    })
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
      timer = window.setTimeout(poll, 800)
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

async function createExport(format: 'CSV' | 'XLSX') {
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
    ElMessage.error(error instanceof Error ? error.message : '导出失败')
  }
}

async function saveQuestion() {
  if (!sourceId.value || !sql.value.trim()) return ElMessage.warning('请完成数据源与 SQL 配置')
  if (!canSave.value) return ElMessage.warning('无创建问题权限')
  if (collectionId.value === undefined) return ElMessage.warning('请选择要保存到的集合')
  let name = questionName.value.trim()
  if (!name) {
    const { value } = await ElMessageBox.prompt('请输入问题名称', '保存到集合', {
      inputPattern: /\S+/,
      inputErrorMessage: '名称不能为空',
    })
    name = value
    questionName.value = value
  }
  const data: Partial<Chart> = {
    name,
    collectionId: collectionId.value,
    queryJson: JSON.stringify({ sourceId: sourceId.value, sql: sql.value, parameters: [] }),
    chartType: chartType.value || 'table',
    configJson: '{}',
    dataSourceId: sourceId.value,
  }
  saving.value = true
  try {
    const created = await chartApi.create(data)
    ElMessage.success(`已保存到集合「${selectedCollectionName.value || collectionId.value}」`)
    await router.push(`/questions/${created.id}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
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

watch(() => route.query.sourceId, (value) => {
  if (typeof value === 'string') sourceId.value = value
})

watch(() => route.query.collectionId, (value) => {
  if (typeof value === 'string') collectionId.value = value
})

watch(() => route.query.name, (value) => {
  if (typeof value === 'string') questionName.value = value
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
        <h1 class="page-title">SQL 查询</h1>
        <p class="page-subtitle">
          对已授权数据源执行只读 SQL；可将查询保存为问题并放入集合
          <template v-if="selectedCollectionName">（当前集合：{{ selectedCollectionName }}）</template>
        </p>
      </div>
      <div class="header-actions">
        <el-button @click="$router.push('/databases')">数据源浏览</el-button>
        <el-button
          v-if="canSave"
          :loading="saving"
          :disabled="!sourceId || !sql.trim()"
          @click="saveQuestion"
        >保存到集合</el-button>
        <el-button
          v-if="userStore.hasPermission('query:raw')"
          type="primary"
          :loading="running"
          :disabled="!sourceId"
          @click="run"
        >执行查询</el-button>
        <el-button v-if="running" type="danger" @click="cancel">取消</el-button>
      </div>
    </div>

    <el-alert
      v-if="!userStore.hasPermission('query:raw')"
      class="perm-alert"
      type="warning"
      :closable="false"
      show-icon
      title="当前角色缺少「执行原生 SQL」权限（query:raw）"
      description="请联系管理员在角色管理中授予该权限后再使用数据源 SQL 查询。"
    />

    <template v-else>
      <section class="tip-panel">
        <div class="tip-head">
          <div>
            <strong>使用提示</strong>
            <span class="tip-head-meta">只读查询 · 最多展示 {{ QUERY_RESULT_DISPLAY_LIMIT }} 行</span>
          </div>
          <el-button link type="primary" @click="tipsCollapsed = !tipsCollapsed">
            {{ tipsCollapsed ? '展开' : '收起' }}
          </el-button>
        </div>
        <div v-show="!tipsCollapsed" class="tip-grid">
          <div class="tip-card tip-info">
            <div class="tip-label">联想补全</div>
            <p>输入时自动提示 SQL 关键字、表名与字段；也可按 <kbd>Ctrl</kbd> + <kbd>Space</kbd> 手动触发。</p>
          </div>
          <div class="tip-card tip-ok">
            <div class="tip-label">快捷执行</div>
            <p>编辑器内按 <kbd>Ctrl</kbd> + <kbd>Enter</kbd> 提交查询。仅支持单条 <code>SELECT</code> / <code>WITH</code> 只读语句。</p>
          </div>
          <div class="tip-card tip-warn">
            <div class="tip-label">元数据</div>
            <p>
              <template v-if="schemaLoading">正在加载当前数据源的表字段目录…</template>
              <template v-else-if="!sourceId">选择数据源后会加载可联想的表与字段。</template>
              <template v-else-if="schemaTableCount === 0">
                当前没有可用表字段联想，请先到管理后台对该数据源执行「同步元数据」。
              </template>
              <template v-else>已加载约 {{ schemaTableCount }} 张表的字段目录，可直接输入表名开始联想。</template>
            </p>
          </div>
          <div class="tip-card tip-info">
            <div class="tip-label">跨库 SQL</div>
            <p>{{ catalogHint }}</p>
          </div>
        </div>
      </section>

      <section class="editor-panel">
        <div class="panel-toolbar">
          <div class="source-row">
            <span class="field-label">数据源</span>
            <el-select
              v-model="sourceId"
              filterable
              placeholder="选择有权限的数据源"
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
            <el-tag v-if="schemaLoading" size="small" type="warning">加载联想中</el-tag>
            <el-tag v-else-if="sourceId && schemaTableCount > 0" size="small" type="success" effect="plain">
              {{ schemaTableCount }} 表可联想
            </el-tag>
          </div>
          <div class="run-row">
            <el-tag v-if="task" :type="statusType" effect="light">{{ displayLabel(task.status) }}</el-tag>
            <span v-if="elapsedText" class="elapsed">耗时 {{ elapsedText }}</span>
            <el-button type="primary" :loading="running" :disabled="!sourceId" @click="run">执行</el-button>
            <el-button v-if="running" type="danger" plain @click="cancel">取消</el-button>
          </div>
        </div>

        <div class="editor-box" @keydown="onEditorKeydown">
          <SqlEditor
            v-model="sql"
            :dialect="currentDialect"
            :jdbc-url="currentSource?.jdbcUrl"
            :schema="editorSchema"
            :default-schema="editorDefaultSchema"
            :placeholder-text="editorPlaceholder"
          />
        </div>
      </section>

      <section v-if="canSave" class="save-panel-block">
        <div class="save-head">
          <div>
            <h2 class="section-title">保存到集合</h2>
            <p class="section-meta">将当前 SQL 保存为问题，可在集合、首页续看与仪表盘中使用；无需先执行查询。</p>
          </div>
          <el-button type="primary" :loading="saving" :disabled="!sourceId || !sql.trim()" @click="saveQuestion">
            保存
          </el-button>
        </div>
        <div class="save-row">
          <el-input v-model="questionName" placeholder="问题名称" class="save-name" />
          <el-select v-model="collectionId" filterable placeholder="选择集合" class="save-collection">
            <el-option v-for="item in collections" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <el-select v-model="chartType" class="save-chart">
            <el-option label="表格" value="table" />
            <el-option label="柱状图" value="bar" />
            <el-option label="折线图" value="line" />
            <el-option label="饼图" value="pie" />
          </el-select>
        </div>
      </section>

      <section v-if="result" class="result-panel">
        <div class="result-head">
          <div>
            <h2 class="section-title">查询结果</h2>
            <p class="section-meta">
              展示 {{ resultCount }} 行
              <template v-if="elapsedText"> · 耗时 {{ elapsedText }}</template>
              <template v-if="result.rows.length > QUERY_RESULT_DISPLAY_LIMIT">
                （已截断至 {{ QUERY_RESULT_DISPLAY_LIMIT }}）
              </template>
            </p>
          </div>
          <div class="result-actions">
            <el-button @click="createExport('CSV')">导出 CSV</el-button>
            <el-button @click="createExport('XLSX')">导出 XLSX</el-button>
          </div>
        </div>

        <QueryResultTable :result="result" :max-height="420" />
        <ChartPreview v-if="chartType !== 'table'" :result="result" :type="chartType" />
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
  border: 1px solid var(--omni-border);
  border-radius: 8px;
  overflow: hidden;
  background: #fafbfc;
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

@media (max-width: 1100px) {
  .tip-grid { grid-template-columns: 1fr; }
  .source-select { width: 220px; }
}
</style>

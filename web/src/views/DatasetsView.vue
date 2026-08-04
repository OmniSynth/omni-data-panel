<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { collectionApi, dataSourceApi, datasetApi } from '@/api'
import { useUserStore } from '@/stores/user'
import type { Collection, DataSource, Dataset, DatasetField, Id, MetadataColumn, MetadataTable, ModelType } from '@/types'
import SqlEditor from '@/components/SqlEditor.vue'
import {
  countCompletionTables,
  inferDefaultSchema,
  toEditorSchema,
  type CompletionSchemaPayload,
  type EditorSqlSchema,
} from '@/sql/schema'
import { resolveSqlDialect } from '@/sql/dialects'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const rows = ref<Dataset[]>([])
const sources = ref<DataSource[]>([])
const collections = ref<Collection[]>([])
const schemas = ref<string[]>([])
const tables = ref<MetadataTable[]>([])
const columns = ref<MetadataColumn[]>([])
const editorSchema = ref<EditorSqlSchema>({})
const completionPayload = ref<CompletionSchemaPayload | null>(null)
const schemaLoading = ref(false)
const visible = ref(false)
const editingId = ref<Id>()
const form = reactive<Dataset>({
  id: '', name: '', dataSourceId: '', schemaName: '', tableName: '', fields: [],
  description: '', collectionId: '', modelType: 'TABLE', definitionSql: '',
})
const currentSource = computed(() => sources.value.find((item) => String(item.id) === String(form.dataSourceId)))
const currentDialect = computed(() => resolveSqlDialect(currentSource.value?.dialect, currentSource.value?.jdbcUrl).id)
const schemaTableCount = computed(() => countCompletionTables(completionPayload.value))
const editorDefaultSchema = computed(() =>
  inferDefaultSchema(completionPayload.value, currentSource.value?.defaultDatabase || form.schemaName))
const modelType = computed({
  get: () => (form.modelType || 'TABLE') as ModelType,
  set: (value: ModelType) => { form.modelType = value },
})

async function loadCompletionSchema(id?: Id) {
  if (id === undefined || id === null || id === '') {
    completionPayload.value = null
    editorSchema.value = {}
    return
  }
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
  try {
    const [datasetPage, sourcePage, tree] = await Promise.all([
      datasetApi.list(), dataSourceApi.list(), collectionApi.tree(),
    ])
    rows.value = datasetPage
    sources.value = sourcePage
    collections.value = flatten(tree)
    const focusId = typeof route.params.id === 'string' ? route.params.id : undefined
    if (focusId) {
      const hit = datasetPage.find((item) => String(item.id) === focusId)
      if (hit) await open(hit)
    } else if (route.query.create === '1') {
      await open()
      if (typeof route.query.collectionId === 'string') form.collectionId = route.query.collectionId
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模型加载失败')
  }
}

async function sourceChanged() {
  try {
    await loadCompletionSchema(form.dataSourceId)
    if (modelType.value === 'TABLE') {
      schemas.value = await dataSourceApi.schemas(form.dataSourceId)
      tables.value = []; columns.value = []
      form.schemaName = ''; form.tableName = ''; form.fields = []
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '元数据加载失败')
  }
}

async function schemaChanged() {
  try {
    tables.value = await dataSourceApi.tables(form.dataSourceId, form.schemaName)
    columns.value = []; form.tableName = ''; form.fields = []
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '数据表加载失败')
  }
}

async function tableChanged() {
  try {
    columns.value = await dataSourceApi.columns(form.dataSourceId, form.schemaName, form.tableName)
    form.fields = columns.value.map((column): DatasetField => ({
      name: column.columnName,
      columnName: column.columnName,
      fieldType: 'DIMENSION',
    }))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '字段加载失败')
  }
}

function onModelTypeChange(value: ModelType) {
  if (value === 'SQL') {
    form.schemaName = ''
    form.tableName = ''
    if (!form.definitionSql?.trim()) {
      form.definitionSql = 'SELECT 1 AS id'
    }
    if (!form.fields.length) {
      form.fields = [{ name: 'id', columnName: 'id', fieldType: 'DIMENSION' }]
    }
  } else if (form.dataSourceId) {
    void sourceChanged()
  }
}

function addSqlField() {
  form.fields.push({ name: '', columnName: '', fieldType: 'DIMENSION' })
}

function removeField(index: number) {
  form.fields.splice(index, 1)
}

async function open(row?: Dataset) {
  Object.assign(form, row ? JSON.parse(JSON.stringify(row)) : {
    id: '', name: '', dataSourceId: '', schemaName: '', tableName: '', fields: [],
    description: '', collectionId: collections.value[0]?.id || '', modelType: 'TABLE', definitionSql: '',
  })
  if (!form.modelType) form.modelType = 'TABLE'
  editingId.value = row?.id
  visible.value = true
  if (form.dataSourceId) await loadCompletionSchema(form.dataSourceId)
  else {
    completionPayload.value = null
    editorSchema.value = {}
  }
  if (row && row.modelType !== 'SQL') {
    try {
      schemas.value = await dataSourceApi.schemas(row.dataSourceId)
      tables.value = await dataSourceApi.tables(row.dataSourceId, row.schemaName)
      columns.value = await dataSourceApi.columns(row.dataSourceId, row.schemaName, row.tableName)
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '元数据加载失败')
    }
  }
}

async function save() {
  if (!form.name || !form.dataSourceId) return ElMessage.warning('请填写名称与数据源')
  if (modelType.value === 'TABLE' && (!form.schemaName || !form.tableName || !form.fields.length)) {
    return ElMessage.warning('请完整配置表模型')
  }
  if (modelType.value === 'SQL') {
    if (!form.definitionSql?.trim()) return ElMessage.warning('请填写 SQL 定义')
    if (!form.fields.length) return ElMessage.warning('请至少配置一个输出字段')
    if (form.fields.some((field) => !field.name?.trim() || !field.columnName?.trim())) {
      return ElMessage.warning('请完整填写输出字段的列名与语义名称')
    }
  }
  const data = { ...form, modelType: modelType.value }
  delete (data as Partial<Dataset>).id
  try {
    if (editingId.value !== undefined) await datasetApi.update(editingId.value, data)
    else await datasetApi.create(data)
    visible.value = false
    ElMessage.success('模型已保存')
    if (route.query.create || route.params.id) await router.replace('/models')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  }
}

async function remove(id: Id) {
  try {
    await ElMessageBox.confirm('确认将该模型移入废纸篓？', '删除确认')
    await datasetApi.remove(id)
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

function collectionName(id?: Id) {
  if (id === undefined || id === null || id === '') return '-'
  return collections.value.find((item) => String(item.id) === String(id))?.name || String(id)
}

watch(() => route.params.id, load)
watch(() => route.query.create, load)
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">模型</h1>
      <el-button v-if="userStore.hasPermission('dataset:create')" type="primary" @click="open()">新建模型</el-button>
    </div>
    <el-table :data="rows" empty-text="暂无模型">
      <el-table-column prop="name" label="名称" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.modelType === 'SQL' ? 'warning' : 'info'" effect="plain">
            {{ row.modelType === 'SQL' ? 'SQL' : '表' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column label="集合" width="160">
        <template #default="{ row }">{{ collectionName(row.collectionId) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link @click="open(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="visible"
      :title="editingId === undefined ? '新建模型' : '编辑模型'"
      width="960px"
      class="model-dialog"
      destroy-on-close
    >
      <el-form label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" placeholder="模型名称" /></el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="可选说明" />
        </el-form-item>
        <el-form-item label="集合">
          <el-select v-model="form.collectionId" class="full-width" clearable>
            <el-option v-for="item in collections" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="modelType" @change="onModelTypeChange">
            <el-radio-button value="TABLE">表模型</el-radio-button>
            <el-radio-button value="SQL">SQL 模型</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="数据源">
          <el-select v-model="form.dataSourceId" class="full-width" filterable placeholder="选择数据源" @change="sourceChanged">
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
        </el-form-item>

        <template v-if="modelType === 'TABLE'">
          <el-form-item label="模式">
            <el-select v-model="form.schemaName" class="full-width" filterable @change="schemaChanged">
              <el-option v-for="item in schemas" :key="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="数据表">
            <el-select v-model="form.tableName" class="full-width" filterable @change="tableChanged">
              <el-option v-for="item in tables" :key="item.tableName" :label="item.tableName" :value="item.tableName" />
            </el-select>
          </el-form-item>
        </template>
      </el-form>

      <section v-if="modelType === 'SQL'" class="sql-block">
        <div class="sql-tips">
          <div class="tip tip-info">
            <strong>定义要求</strong>
            <p>填写单条只读 <code>SELECT</code> / <code>WITH</code>。系统会将其作为子查询封装后供问题与仪表盘引用。</p>
          </div>
          <div class="tip tip-ok">
            <strong>输出字段</strong>
            <p>下方字段的「列名」须与 SQL 结果列（别名）一致，并至少配置一个维度或指标。</p>
          </div>
          <div class="tip tip-warn">
            <strong>联想状态</strong>
            <p>
              <template v-if="schemaLoading">正在加载表字段联想…</template>
              <template v-else-if="!form.dataSourceId">请先选择数据源。</template>
              <template v-else-if="schemaTableCount === 0">暂无表字段联想，请先同步元数据。</template>
              <template v-else>已加载约 {{ schemaTableCount }} 张表，可按 <kbd>Ctrl</kbd>+<kbd>Space</kbd> 触发补全。</template>
            </p>
          </div>
          <div v-if="currentSource" class="tip tip-info">
            <strong>跨库 SQL</strong>
            <p>
              <template v-if="currentSource.defaultDatabase">
                未限定表名使用默认库 {{ currentSource.defaultDatabase }}；也可用 库名.表名。
              </template>
              <template v-else>未设置默认库时请使用 库名.表名。</template>
            </p>
          </div>
        </div>

        <div class="sql-toolbar">
          <span class="sql-label">定义 SQL</span>
          <div class="sql-tags">
            <el-tag v-if="currentSource" size="small" effect="plain" type="info">{{ currentDialect }}</el-tag>
            <el-tag v-if="schemaLoading" size="small" type="warning">加载联想中</el-tag>
            <el-tag v-else-if="form.dataSourceId && schemaTableCount > 0" size="small" type="success" effect="plain">
              {{ schemaTableCount }} 表可联想
            </el-tag>
          </div>
        </div>
        <div class="editor-box">
          <SqlEditor
            :model-value="form.definitionSql || ''"
            :dialect="currentDialect"
            :jdbc-url="currentSource?.jdbcUrl"
            :schema="editorSchema"
            :default-schema="editorDefaultSchema"
            placeholder-text="SELECT id, name FROM your_table"
            @update:model-value="form.definitionSql = $event"
          />
        </div>

        <div class="fields-head">
          <div>
            <strong>输出字段</strong>
            <span class="fields-meta">{{ form.fields.length }} 个</span>
          </div>
          <el-button type="primary" plain @click="addSqlField">添加字段</el-button>
        </div>
        <el-table :data="form.fields" max-height="280" empty-text="请添加 SQL 输出字段">
          <el-table-column label="列名（SQL 别名）" min-width="160">
            <template #default="{ row }">
              <el-input v-model="row.columnName" placeholder="如 order_id" @change="row.name = row.name || row.columnName" />
            </template>
          </el-table-column>
          <el-table-column label="语义名称" min-width="140">
            <template #default="{ row }"><el-input v-model="row.name" placeholder="展示名称" /></template>
          </el-table-column>
          <el-table-column label="类型" width="140">
            <template #default="{ row }">
              <el-select
                v-model="row.fieldType"
                @change="row.aggregation = row.fieldType === 'METRIC' ? (row.aggregation || 'SUM') : undefined"
              >
                <el-option label="维度" value="DIMENSION" />
                <el-option label="指标" value="METRIC" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="聚合" width="130">
            <template #default="{ row }">
              <el-select v-model="row.aggregation" clearable :disabled="row.fieldType !== 'METRIC'" placeholder="聚合">
                <el-option label="求和" value="SUM" />
                <el-option label="平均" value="AVG" />
                <el-option label="计数" value="COUNT" />
                <el-option label="最大" value="MAX" />
                <el-option label="最小" value="MIN" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="" width="70" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeField($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <el-table v-else-if="modelType === 'TABLE'" :data="form.fields" max-height="320" empty-text="选择数据表后自动加载字段">
        <el-table-column prop="columnName" label="物理字段" />
        <el-table-column label="语义名称">
          <template #default="{ row }"><el-input v-model="row.name" /></template>
        </el-table-column>
        <el-table-column label="类型">
          <template #default="{ row }">
            <el-select
              v-model="row.fieldType"
              @change="row.aggregation = row.fieldType === 'METRIC' ? (row.aggregation || 'SUM') : undefined"
            >
              <el-option label="维度" value="DIMENSION" />
              <el-option label="指标" value="METRIC" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="聚合">
          <template #default="{ row }">
            <el-select v-model="row.aggregation" clearable :disabled="row.fieldType !== 'METRIC'" placeholder="聚合">
              <el-option label="求和" value="SUM" />
              <el-option label="平均" value="AVG" />
              <el-option label="计数" value="COUNT" />
              <el-option label="最大" value="MAX" />
              <el-option label="最小" value="MIN" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.option-meta {
  float: right;
  color: var(--omni-muted);
  font-size: 12px;
  margin-left: 16px;
}
.sql-block {
  margin-top: 4px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sql-tips {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.tip {
  border-radius: 8px;
  padding: 10px 12px;
  border: 1px solid transparent;
}
.tip strong {
  display: block;
  font-size: 12px;
  margin-bottom: 4px;
}
.tip p {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: #374151;
}
.tip-info { background: #eff6ff; border-color: #bfdbfe; }
.tip-info strong { color: #1d4ed8; }
.tip-ok { background: #ecfdf5; border-color: #a7f3d0; }
.tip-ok strong { color: #047857; }
.tip-warn { background: #fffbeb; border-color: #fde68a; }
.tip-warn strong { color: #b45309; }
.sql-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.sql-label { font-size: 13px; font-weight: 600; color: #374151; }
.sql-tags { display: flex; gap: 6px; flex-wrap: wrap; }
.editor-box {
  border: 1px solid var(--omni-border);
  border-radius: 8px;
  overflow: hidden;
  background: #fafbfc;
}
.fields-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 4px;
}
.fields-head strong { font-size: 13px; }
.fields-meta {
  margin-left: 8px;
  color: var(--omni-muted);
  font-size: 12px;
}
kbd {
  display: inline-block;
  padding: 0 5px;
  border: 1px solid #d1d5db;
  border-bottom-width: 2px;
  border-radius: 4px;
  background: #fff;
  font-size: 11px;
  font-family: Consolas, "Courier New", monospace;
}
code {
  padding: 0 4px;
  border-radius: 4px;
  background: rgba(17, 24, 39, 0.06);
  font-size: 11px;
  font-family: Consolas, "Courier New", monospace;
}
@media (max-width: 960px) {
  .sql-tips { grid-template-columns: 1fr; }
}
</style>

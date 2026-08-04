<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { confirmBox } from '@/i18n/dialog'
import { useRoute, useRouter } from 'vue-router'
import { collectionApi, dataSourceApi, datasetApi } from '@/api'
import { displayLabel } from '@/display'
import { useUserStore } from '@/stores/user'
import type { Collection, DataSource, Dataset, DatasetField, Id, MetadataColumn, MetadataTable, ModelType } from '@/types'
import SqlEditor from '@/components/SqlEditor.vue'
import DatasetDataPolicyPanel from '@/components/DatasetDataPolicyPanel.vue'
import {
  countCompletionTables,
  inferDefaultSchema,
  toEditorSchema,
  type CompletionSchemaPayload,
  type EditorSqlSchema,
} from '@/sql/schema'
import { resolveSqlDialect } from '@/sql/dialects'

const { t } = useI18n()
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
const inferringFields = ref(false)
const sqlEditorRef = ref<{ format: () => boolean }>()
const visible = ref(false)
const editingId = ref<Id>()
const policyVisible = ref(false)
const policyDataset = ref<Dataset>()
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
    ElMessage.error(error instanceof Error ? error.message : t('dataset.loadFailed'))
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
    ElMessage.error(error instanceof Error ? error.message : t('dataset.metaFailed'))
  }
}

async function schemaChanged() {
  try {
    tables.value = await dataSourceApi.tables(form.dataSourceId, form.schemaName)
    columns.value = []; form.tableName = ''; form.fields = []
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('dataset.tablesFailed'))
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
    ElMessage.error(error instanceof Error ? error.message : t('dataset.fieldsFailed'))
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

function formatSqlInEditor() {
  if (!sqlEditorRef.value?.format()) {
    ElMessage.warning(t('sqlEditor.formatFailed'))
  }
}

async function inferFieldsFromSql() {
  if (!form.dataSourceId) return ElMessage.warning(t('dataset.needSource'))
  if (!form.definitionSql?.trim()) return ElMessage.warning(t('dataset.needSql'))
  inferringFields.value = true
  try {
    const inferred = await datasetApi.inferSqlFields(form.dataSourceId, form.definitionSql)
    if (!inferred.length) {
      ElMessage.warning(t('dataset.noFieldsDetected'))
      return
    }
    const previous = new Map(
      form.fields
        .filter((field) => field.columnName)
        .map((field) => [field.columnName, field] as const),
    )
    form.fields = inferred.map((item): DatasetField => {
      const existing = previous.get(item.columnName)
      if (existing) {
        return {
          ...existing,
          columnName: item.columnName,
          name: existing.name || item.name,
          fieldType: existing.fieldType || item.fieldType,
          aggregation: existing.fieldType === 'METRIC'
            ? (existing.aggregation || item.aggregation || 'SUM')
            : (item.fieldType === 'METRIC' ? (item.aggregation || 'SUM') : undefined),
        }
      }
      return {
        name: item.name,
        columnName: item.columnName,
        fieldType: item.fieldType,
        aggregation: item.fieldType === 'METRIC' ? (item.aggregation || 'SUM') : undefined,
      }
    })
    ElMessage.success(t('dataset.fieldsGenerated', { n: form.fields.length }))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('dataset.inferFailed'))
  } finally {
    inferringFields.value = false
  }
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
      ElMessage.error(error instanceof Error ? error.message : t('dataset.metaFailed'))
    }
  }
}

async function save() {
  if (!form.name || !form.dataSourceId) return ElMessage.warning(t('dataset.needNameSource'))
  if (modelType.value === 'TABLE' && (!form.schemaName || !form.tableName || !form.fields.length)) {
    return ElMessage.warning(t('dataset.needTableConfig'))
  }
  if (modelType.value === 'SQL') {
    if (!form.definitionSql?.trim()) return ElMessage.warning(t('dataset.needSqlDef'))
    if (!form.fields.length) return ElMessage.warning(t('dataset.needOutputField'))
    if (form.fields.some((field) => !field.name?.trim() || !field.columnName?.trim())) {
      return ElMessage.warning(t('dataset.needFieldNames'))
    }
  }
  const data = { ...form, modelType: modelType.value }
  delete (data as Partial<Dataset>).id
  try {
    if (editingId.value !== undefined) await datasetApi.update(editingId.value, data)
    else await datasetApi.create(data)
    visible.value = false
    ElMessage.success(t('dataset.saved'))
    if (route.query.create || route.params.id) await router.replace('/models')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('common.saveFailed'))
  }
}

async function remove(id: Id) {
  try {
    await confirmBox(t('dataset.moveToTrash'), t('common.deleteConfirmTitle'))
    await datasetApi.remove(id)
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('common.deleteFailed'))
  }
}

function collectionName(id?: Id) {
  if (id === undefined || id === null || id === '') return '-'
  return collections.value.find((item) => String(item.id) === String(id))?.name || String(id)
}

function canManagePolicy(row: Dataset) {
  return userStore.isAdmin || String(row.ownerId) === String(userStore.user?.id)
}

function openPolicy(row: Dataset) {
  policyDataset.value = row
  policyVisible.value = true
}

watch(() => route.params.id, load)
watch(() => route.query.create, load)
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">{{ t('dataset.title') }}</h1>
      <el-button v-if="userStore.hasPermission('dataset:create')" type="primary" @click="open()">{{ t('dataset.create') }}</el-button>
    </div>
    <el-table :data="rows" :empty-text="t('dataset.empty')">
      <el-table-column prop="name" :label="t('common.name')" />
      <el-table-column :label="t('common.type')" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.modelType === 'SQL' ? 'warning' : 'info'" effect="plain">
            {{ row.modelType === 'SQL' ? displayLabel('SQL') : displayLabel('TABLE') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" :label="t('common.description')" show-overflow-tooltip />
      <el-table-column :label="t('common.collection')" width="160">
        <template #default="{ row }">{{ collectionName(row.collectionId) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="240">
        <template #default="{ row }">
          <el-button link @click="open(row)">{{ t('common.edit') }}</el-button>
          <el-button v-if="canManagePolicy(row)" link type="primary" @click="openPolicy(row)">
            {{ t('datasetPolicy.action') }}
          </el-button>
          <el-button link type="danger" @click="remove(row.id)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <DatasetDataPolicyPanel v-model="policyVisible" :dataset="policyDataset" />

    <el-dialog
      v-model="visible"
      :title="editingId === undefined ? t('dataset.createTitle') : t('dataset.editTitle')"
      width="960px"
      class="model-dialog"
      destroy-on-close
    >
      <el-form label-width="90px">
        <el-form-item :label="t('common.name')"><el-input v-model="form.name" :placeholder="t('dataset.modelName')" /></el-form-item>
        <el-form-item :label="t('common.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" :placeholder="t('dataset.optionalDesc')" />
        </el-form-item>
        <el-form-item :label="t('common.collection')">
          <el-select v-model="form.collectionId" class="full-width" clearable>
            <el-option v-for="item in collections" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.type')">
          <el-radio-group v-model="modelType" @change="onModelTypeChange">
            <el-radio-button value="TABLE">{{ t('dataset.tableModel') }}</el-radio-button>
            <el-radio-button value="SQL">{{ t('dataset.sqlModel') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('dataSource.title')">
          <el-select v-model="form.dataSourceId" class="full-width" filterable :placeholder="t('dataset.selectSource')" @change="sourceChanged">
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
          <el-form-item :label="t('dataset.schema')">
            <el-select v-model="form.schemaName" class="full-width" filterable @change="schemaChanged">
              <el-option v-for="item in schemas" :key="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('dataset.dataTable')">
            <el-select v-model="form.tableName" class="full-width" filterable @change="tableChanged">
              <el-option v-for="item in tables" :key="item.tableName" :label="item.tableName" :value="item.tableName" />
            </el-select>
          </el-form-item>
        </template>
      </el-form>

      <section v-if="modelType === 'SQL'" class="sql-block">
        <div class="sql-tips">
          <div class="tip tip-info">
            <strong>{{ t('dataset.defRequirements') }}</strong>
            <p>{{ t('dataset.sqlHint') }}</p>
          </div>
          <div class="tip tip-ok">
            <strong>{{ t('dataset.outputFields') }}</strong>
            <p>{{ t('dataset.outputHint') }}</p>
          </div>
          <div class="tip tip-warn">
            <strong>{{ t('dataset.completionStatus') }}</strong>
            <p>
              <template v-if="schemaLoading">{{ t('dataset.loadingCompletion') }}</template>
              <template v-else-if="!form.dataSourceId">{{ t('dataset.selectSourceFirst') }}</template>
              <template v-else-if="schemaTableCount === 0">{{ t('dataset.noCompletion') }}</template>
              <template v-else>{{ t('dataset.completionReady', { n: schemaTableCount }) }}</template>
            </p>
          </div>
          <div v-if="currentSource" class="tip tip-info">
            <strong>{{ t('dataset.crossDb') }}</strong>
            <p>
              <template v-if="currentSource.defaultDatabase">
                {{ t('dataset.defaultDbHint', { db: currentSource.defaultDatabase }) }}
              </template>
              <template v-else>{{ t('dataset.noDefaultDbHint') }}</template>
            </p>
          </div>
        </div>

        <div class="sql-toolbar">
          <span class="sql-label">{{ t('dataset.definitionSql') }}</span>
          <div class="sql-tags">
            <el-tag v-if="currentSource" size="small" effect="plain" type="info">{{ currentDialect }}</el-tag>
            <el-tag v-if="schemaLoading" size="small" type="warning">{{ t('dataset.loadingSuggest') }}</el-tag>
            <el-tag v-else-if="form.dataSourceId && schemaTableCount > 0" size="small" type="success" effect="plain">
              {{ t('dataset.tablesSuggest', { n: schemaTableCount }) }}
            </el-tag>
            <el-button
              size="small"
              plain
              :disabled="!(form.definitionSql || '').trim()"
              :title="t('sql.formatHint')"
              @click="formatSqlInEditor"
            >{{ t('sql.format') }}</el-button>
          </div>
        </div>
        <div class="editor-box">
          <SqlEditor
            ref="sqlEditorRef"
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
            <strong>{{ t('dataset.outputFields') }}</strong>
            <span class="fields-meta">{{ t('dataset.fieldCount', { n: form.fields.length }) }}</span>
          </div>
          <div class="fields-actions">
            <el-button type="primary" :loading="inferringFields" @click="inferFieldsFromSql">{{ t('dataset.inferFromSql') }}</el-button>
            <el-button plain @click="addSqlField">{{ t('dataset.addField') }}</el-button>
          </div>
        </div>
        <el-table :data="form.fields" max-height="280" :empty-text="t('dataset.addOutputField')">
          <el-table-column :label="t('dataset.columnAlias')" min-width="160">
            <template #default="{ row }">
              <el-input v-model="row.columnName" placeholder="order_id" @change="row.name = row.name || row.columnName" />
            </template>
          </el-table-column>
          <el-table-column :label="t('dataset.semanticName')" min-width="140">
            <template #default="{ row }"><el-input v-model="row.name" :placeholder="t('dataset.displayName')" /></template>
          </el-table-column>
          <el-table-column :label="t('common.type')" width="140">
            <template #default="{ row }">
              <el-select
                v-model="row.fieldType"
                @change="row.aggregation = row.fieldType === 'METRIC' ? (row.aggregation || 'SUM') : undefined"
              >
                <el-option :label="displayLabel('DIMENSION')" value="DIMENSION" />
                <el-option :label="displayLabel('METRIC')" value="METRIC" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="t('dataset.aggregation')" width="130">
            <template #default="{ row }">
              <el-select v-model="row.aggregation" clearable :disabled="row.fieldType !== 'METRIC'" :placeholder="t('dataset.aggregation')">
                <el-option :label="displayLabel('SUM')" value="SUM" />
                <el-option :label="displayLabel('AVG')" value="AVG" />
                <el-option :label="displayLabel('COUNT')" value="COUNT" />
                <el-option :label="displayLabel('MAX')" value="MAX" />
                <el-option :label="displayLabel('MIN')" value="MIN" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="" width="70" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeField($index)">{{ t('common.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <el-table v-else-if="modelType === 'TABLE'" :data="form.fields" max-height="320" :empty-text="t('dataset.autoLoadFields')">
        <el-table-column prop="columnName" :label="t('dataset.physicalField')" />
        <el-table-column :label="t('dataset.semanticName')">
          <template #default="{ row }"><el-input v-model="row.name" /></template>
        </el-table-column>
        <el-table-column :label="t('common.type')">
          <template #default="{ row }">
            <el-select
              v-model="row.fieldType"
              @change="row.aggregation = row.fieldType === 'METRIC' ? (row.aggregation || 'SUM') : undefined"
            >
              <el-option :label="displayLabel('DIMENSION')" value="DIMENSION" />
              <el-option :label="displayLabel('METRIC')" value="METRIC" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column :label="t('dataset.aggregation')">
          <template #default="{ row }">
            <el-select v-model="row.aggregation" clearable :disabled="row.fieldType !== 'METRIC'" :placeholder="t('dataset.aggregation')">
              <el-option :label="displayLabel('SUM')" value="SUM" />
              <el-option :label="displayLabel('AVG')" value="AVG" />
              <el-option :label="displayLabel('COUNT')" value="COUNT" />
              <el-option :label="displayLabel('MAX')" value="MAX" />
              <el-option :label="displayLabel('MIN')" value="MIN" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="save">{{ t('common.save') }}</el-button>
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
.fields-actions { display: flex; gap: 8px; flex-wrap: wrap; }
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

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { dataSourceApi } from '@/api'
import { useUserStore } from '@/stores/user'
import type { DataSource, Id, MetadataColumn } from '@/types'

type TableItem = {
  id: string
  schema: string
  tableName: string
  comment?: string
}

const userStore = useUserStore()
const sources = ref<DataSource[]>([])
const sourceId = ref<Id>()
const tables = ref<TableItem[]>([])
const loading = ref(false)
const columnsLoading = ref(false)
const keyword = ref('')
const selected = ref<TableItem>()
const columns = ref<MetadataColumn[]>([])
/** 多库时已展开的库名 */
const expandedSchemas = ref<Set<string>>(new Set())

const realTables = computed(() => tables.value.filter((item) => !!item.tableName))

const filteredTables = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return realTables.value
  return realTables.value.filter((item) => {
    const haystack = `${item.schema}.${item.tableName} ${item.comment || ''}`.toLowerCase()
    return haystack.includes(q)
  })
})

const allSchemas = computed(() => {
  const names = new Set<string>()
  for (const item of tables.value) {
    if (item.schema) names.add(item.schema)
  }
  return [...names].sort((a, b) => a.localeCompare(b))
})

const multiDatabase = computed(() => allSchemas.value.length > 1)

const groupedTables = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  const schemaNames = q
    ? [...new Set(filteredTables.value.map((item) => item.schema))].sort((a, b) => a.localeCompare(b))
    : allSchemas.value

  return schemaNames.map((schema) => {
    const items = filteredTables.value.filter((item) => item.schema === schema)
    const totalInSchema = realTables.value.filter((item) => item.schema === schema).length
    return { schema, items, totalInSchema }
  })
})

const selectedTitle = computed(() => {
  if (!selected.value?.tableName) return ''
  return selected.value.comment
    ? `${selected.value.schema}.${selected.value.tableName}（${selected.value.comment}）`
    : `${selected.value.schema}.${selected.value.tableName}`
})

function isExpanded(schema: string) {
  if (!multiDatabase.value) return true
  if (keyword.value.trim()) return true
  return expandedSchemas.value.has(schema)
}

function toggleSchema(schema: string) {
  if (!multiDatabase.value) return
  const next = new Set(expandedSchemas.value)
  if (next.has(schema)) next.delete(schema)
  else next.add(schema)
  expandedSchemas.value = next
}

function formatLength(column: MetadataColumn) {
  if (column.columnSize == null) return '—'
  if (column.decimalDigits != null && column.decimalDigits > 0) {
    return `${column.columnSize},${column.decimalDigits}`
  }
  return String(column.columnSize)
}

function formatForeignKey(column: MetadataColumn) {
  if (!column.foreignKey) return '否'
  if (column.fkTableName && column.fkColumnName) {
    return `是 → ${column.fkTableName}.${column.fkColumnName}`
  }
  return '是'
}

async function loadSources() {
  try {
    sources.value = await dataSourceApi.list()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '数据源加载失败')
  }
}

async function loadTables() {
  if (sourceId.value === undefined) return
  loading.value = true
  selected.value = undefined
  columns.value = []
  tables.value = []
  keyword.value = ''
  expandedSchemas.value = new Set()
  try {
    const schemas = (await dataSourceApi.schemas(sourceId.value)).filter((schema) => !!schema)
    const batches = await Promise.all(schemas.map(async (schema) => {
      const rows = await dataSourceApi.tables(sourceId.value!, schema)
      if (!rows.length) {
        return [{
          id: `${schema}.__empty__`,
          schema,
          tableName: '',
          comment: '（暂无表）',
        } satisfies TableItem]
      }
      return rows.map((table) => ({
        id: `${schema}.${table.tableName}`,
        schema,
        tableName: table.tableName,
        comment: table.comment,
      }))
    }))
    tables.value = batches.flat().sort((a, b) => a.id.localeCompare(b.id))
    // 多库默认全部折叠；单库直接展开
    if (schemas.length === 1) {
      expandedSchemas.value = new Set(schemas)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '表列表加载失败')
  } finally {
    loading.value = false
  }
}

async function syncMetadata() {
  if (sourceId.value === undefined) return
  loading.value = true
  try {
    await dataSourceApi.sync(sourceId.value)
    ElMessage.success('元数据同步完成')
    await loadTables()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '同步失败')
    loading.value = false
  }
}

async function openTable(item: TableItem) {
  if (sourceId.value === undefined || !item.tableName) return
  selected.value = item
  columnsLoading.value = true
  try {
    columns.value = await dataSourceApi.columns(sourceId.value, item.schema, item.tableName)
  } catch (error) {
    columns.value = []
    ElMessage.error(error instanceof Error ? error.message : '字段加载失败')
  } finally {
    columnsLoading.value = false
  }
}

watch(sourceId, (value) => {
  if (value === undefined) {
    tables.value = []
    selected.value = undefined
    columns.value = []
    keyword.value = ''
    expandedSchemas.value = new Set()
    return
  }
  loadTables()
})

onMounted(loadSources)
</script>

<template>
  <div class="page browser">
    <div class="page-header">
      <h1 class="page-title">数据源</h1>
      <div class="header-actions">
        <el-select
          v-model="sourceId"
          clearable
          placeholder="选择数据源"
          style="width:280px"
        >
          <el-option v-for="item in sources" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-button
          v-if="sourceId && userStore.isAdmin"
          :loading="loading"
          @click="syncMetadata"
        >同步元数据</el-button>
        <el-button
          v-if="sourceId && userStore.hasPermission('query:raw')"
          type="primary"
          @click="$router.push({ path: '/sql', query: { sourceId: String(sourceId) } })"
        >SQL 查询</el-button>
      </div>
    </div>

    <div v-loading="loading" class="browser-body">
      <el-empty v-if="!sourceId" description="请选择数据源查看表结构" />
      <template v-else>
        <aside class="tree-pane">
          <div class="pane-title">
            <template v-if="multiDatabase">
              {{ allSchemas.length }} 个库 · {{ filteredTables.length }}/{{ realTables.length }} 张表
            </template>
            <template v-else>
              表列表（{{ filteredTables.length }}/{{ realTables.length }}）
            </template>
          </div>
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索表名、库名或注释"
            class="table-search"
          />
          <div class="table-list">
            <el-empty
              v-if="!allSchemas.length"
              description="暂无库表，请先同步元数据"
              :image-size="64"
            />
            <el-empty
              v-else-if="!groupedTables.length"
              description="没有匹配的表"
              :image-size="64"
            />
            <div v-for="group in groupedTables" :key="group.schema" class="schema-group">
              <button
                v-if="multiDatabase"
                type="button"
                class="schema-toggle"
                :class="{ open: isExpanded(group.schema) }"
                @click="toggleSchema(group.schema)"
              >
                <span class="chevron" aria-hidden="true" />
                <span class="schema-label">{{ group.schema }}</span>
                <span class="schema-count">{{ group.items.length || group.totalInSchema }}</span>
              </button>
              <div v-else class="schema-name">库 · {{ group.schema }}</div>

              <div v-show="isExpanded(group.schema)" class="schema-tables">
                <el-empty
                  v-if="!group.items.length"
                  description="（暂无表）"
                  :image-size="48"
                />
                <button
                  v-for="item in group.items"
                  :key="item.id"
                  type="button"
                  class="table-item"
                  :class="{ active: selected?.id === item.id }"
                  @click="openTable(item)"
                >
                  <strong>{{ item.tableName }}</strong>
                  <span v-if="item.comment" class="muted">{{ item.comment }}</span>
                </button>
              </div>
            </div>
          </div>
        </aside>

        <section class="detail-pane">
          <div class="pane-title">{{ selectedTitle || '字段详情' }}</div>
          <el-empty v-if="!selected" description="搜索并点击左侧表，查看字段名称、类型、主键、外键、注释与长度" />
          <el-table
            v-else
            v-loading="columnsLoading"
            :data="columns"
            stripe
            empty-text="该表暂无字段信息，请重新同步元数据"
          >
            <el-table-column prop="columnName" label="字段名称" min-width="140" fixed />
            <el-table-column prop="typeName" label="类型" min-width="110" />
            <el-table-column label="长度" width="90">
              <template #default="{ row }">{{ formatLength(row) }}</template>
            </el-table-column>
            <el-table-column label="主键" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.primaryKey" size="small" type="warning">是</el-tag>
                <span v-else class="muted">否</span>
              </template>
            </el-table-column>
            <el-table-column label="外键" min-width="180">
              <template #default="{ row }">{{ formatForeignKey(row) }}</template>
            </el-table-column>
            <el-table-column label="可空" width="80">
              <template #default="{ row }">{{ row.nullable ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column prop="comment" label="字段注释" min-width="180" show-overflow-tooltip />
            <el-table-column prop="position" label="序号" width="70" />
          </el-table>
        </section>
      </template>
    </div>
  </div>
</template>

<style scoped>
.header-actions { display: flex; gap: 10px; align-items: center; }
.browser-body {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
  min-height: 520px;
}
.tree-pane,
.detail-pane {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 14px;
  min-height: 520px;
}
.pane-title {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 12px;
}
.table-search { margin-bottom: 12px; }
.table-list {
  max-height: calc(100vh - 260px);
  overflow: auto;
}
.schema-group {
  display: block;
  border: 1px solid #eef0f3;
  border-radius: 8px;
  background: #fafbfc;
  margin-bottom: 8px;
}
.schema-group:last-child { margin-bottom: 0; }
.schema-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  border: 0;
  background: #f3f5f7;
  text-align: left;
  padding: 9px 10px;
  cursor: pointer;
  color: #374151;
  flex-shrink: 0;
}
.schema-toggle:hover { background: #eaf2fa; }
.schema-toggle .chevron {
  width: 0;
  height: 0;
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
  border-left: 5px solid #9ca3af;
  transition: transform 0.15s ease;
  flex: 0 0 auto;
}
.schema-toggle.open .chevron { transform: rotate(90deg); }
.schema-label {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.schema-count {
  font-size: 11px;
  color: #9ca3af;
  font-variant-numeric: tabular-nums;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  padding: 0 7px;
  line-height: 18px;
}
.schema-name {
  font-size: 12px;
  color: #6b7280;
  font-weight: 600;
  padding: 8px 10px 4px;
}
.schema-tables {
  display: block;
  padding: 4px;
  background: #fff;
  border-top: 1px solid #eef0f3;
}
.table-item {
  display: block;
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
}
.table-item + .table-item { margin-top: 2px; }
.table-item:hover,
.table-item.active {
  background: #eef5fc;
}
.table-item strong {
  display: block;
  font-size: 13px;
  color: #111827;
  font-weight: 600;
}
.table-item .muted {
  display: block;
  margin-top: 2px;
}
.muted { color: #9ca3af; font-size: 12px; }
@media (max-width: 900px) {
  .browser-body { grid-template-columns: 1fr; }
}
</style>

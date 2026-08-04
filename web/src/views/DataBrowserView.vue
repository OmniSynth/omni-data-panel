<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()
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
  if (column.columnSize == null) return t('common.emptyDash')
  if (column.decimalDigits != null && column.decimalDigits > 0) {
    return `${column.columnSize},${column.decimalDigits}`
  }
  return String(column.columnSize)
}

function formatForeignKey(column: MetadataColumn) {
  if (!column.foreignKey) return t('common.no')
  if (column.fkTableName && column.fkColumnName) {
    return t('dataBrowser.fkYes', { table: column.fkTableName, col: column.fkColumnName })
  }
  return t('common.yes')
}

async function loadSources() {
  try {
    sources.value = await dataSourceApi.list()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('dataBrowser.loadFailed'))
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
          comment: t('dataBrowser.noTablesParen'),
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
    ElMessage.error(error instanceof Error ? error.message : t('dataBrowser.tablesFailed'))
  } finally {
    loading.value = false
  }
}

async function syncMetadata() {
  if (sourceId.value === undefined) return
  loading.value = true
  try {
    await dataSourceApi.sync(sourceId.value)
    ElMessage.success(t('dataBrowser.syncOk'))
    await loadTables()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('dataBrowser.syncFailed'))
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
    ElMessage.error(error instanceof Error ? error.message : t('dataBrowser.fieldsFailed'))
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
      <h1 class="page-title">{{ t('dataSource.title') }}</h1>
      <div class="header-actions">
        <el-select
          v-model="sourceId"
          clearable
          :placeholder="t('dataBrowser.selectSource')"
          style="width:280px"
        >
          <el-option v-for="item in sources" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-button
          v-if="sourceId && userStore.isAdmin"
          :loading="loading"
          @click="syncMetadata"
        >{{ t('dataBrowser.syncMeta') }}</el-button>
        <el-button
          v-if="sourceId && userStore.hasPermission('query:raw')"
          type="primary"
          @click="$router.push({ path: '/sql', query: { sourceId: String(sourceId) } })"
        >{{ t('dataBrowser.sqlQuery') }}</el-button>
      </div>
    </div>

    <div v-loading="loading" class="browser-body" :class="{ idle: !sourceId }">
      <div v-if="!sourceId" class="browser-empty">
        <span class="browser-empty-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
            <ellipse cx="12" cy="5" rx="8" ry="3" />
            <path d="M4 5v6c0 1.7 3.6 3 8 3s8-1.3 8-3V5" />
            <path d="M4 11v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6" />
          </svg>
        </span>
        <div class="browser-empty-copy">
          <strong>{{ t('dataBrowser.selectHintTitle') }}</strong>
          <span>{{ sources.length ? t('dataBrowser.selectHint') : t('dataBrowser.selectHintEmpty') }}</span>
        </div>
        <div v-if="sources.length" class="browser-empty-sources">
          <button
            v-for="item in sources"
            :key="item.id"
            type="button"
            class="source-chip"
            @click="sourceId = item.id"
          >
            <strong>{{ item.name }}</strong>
            <span>{{ item.dialect || 'MYSQL' }}</span>
          </button>
        </div>
      </div>
      <template v-else>
        <aside class="tree-pane">
          <div class="pane-title">
            <template v-if="multiDatabase">
              {{ t('dataBrowser.stats', { schemas: allSchemas.length, shown: filteredTables.length, total: realTables.length }) }}
            </template>
            <template v-else>
              {{ t('dataBrowser.tableList', { shown: filteredTables.length, total: realTables.length }) }}
            </template>
          </div>
          <el-input
            v-model="keyword"
            clearable
            :placeholder="t('dataBrowser.searchTables')"
            class="table-search"
          />
          <div class="table-list">
            <div v-if="!allSchemas.length" class="pane-empty">{{ t('dataBrowser.noTables') }}</div>
            <div v-else-if="!groupedTables.length" class="pane-empty">{{ t('dataBrowser.noMatch') }}</div>
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
              <div v-else class="schema-name">{{ t('dataBrowser.schemaPrefix') }} {{ group.schema }}</div>

              <div v-show="isExpanded(group.schema)" class="schema-tables">
                <div v-if="!group.items.length" class="pane-empty compact">{{ t('dataBrowser.noTablesParen') }}</div>
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
          <div class="pane-title">{{ selectedTitle || t('dataBrowser.fieldDetail') }}</div>
          <div v-if="!selected" class="detail-empty">
            <span class="browser-empty-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                <path d="M8 6h13" />
                <path d="M8 12h13" />
                <path d="M8 18h13" />
                <path d="M3 6h.01" />
                <path d="M3 12h.01" />
                <path d="M3 18h.01" />
              </svg>
            </span>
            <div class="browser-empty-copy">
              <strong>{{ t('dataBrowser.fieldHintTitle') }}</strong>
              <span>{{ t('dataBrowser.fieldHint') }}</span>
            </div>
          </div>
          <el-table
            v-else
            v-loading="columnsLoading"
            :data="columns"
            stripe
            :empty-text="t('dataBrowser.noFields')"
          >
            <el-table-column prop="columnName" :label="t('dataBrowser.fieldName')" min-width="140" fixed />
            <el-table-column prop="typeName" :label="t('common.type')" min-width="110" />
            <el-table-column :label="t('dataBrowser.length')" width="90">
              <template #default="{ row }">{{ formatLength(row) }}</template>
            </el-table-column>
            <el-table-column :label="t('dataBrowser.primaryKey')" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.primaryKey" size="small" type="warning">{{ t('common.yes') }}</el-tag>
                <span v-else class="muted">{{ t('common.no') }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('dataBrowser.foreignKey')" min-width="180">
              <template #default="{ row }">{{ formatForeignKey(row) }}</template>
            </el-table-column>
            <el-table-column :label="t('dataBrowser.nullable')" width="80">
              <template #default="{ row }">{{ row.nullable ? t('common.yes') : t('common.no') }}</template>
            </el-table-column>
            <el-table-column prop="comment" :label="t('dataBrowser.comment')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="position" :label="t('dataBrowser.ordinal')" width="70" />
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
.browser-body.idle {
  grid-template-columns: 1fr;
}
.browser-empty {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 16px;
  min-height: 360px;
  padding: 28px 32px;
  border: 1px dashed var(--omni-border, #e5e7eb);
  border-radius: 12px;
  background: var(--omni-surface, #f8fafc);
}
.browser-empty-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid var(--omni-border, #e5e7eb);
  color: var(--omni-muted, #6b7280);
}
.browser-empty-copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 420px;
}
.browser-empty-copy strong {
  font-size: 16px;
  font-weight: 600;
  color: var(--omni-text, #111827);
}
.browser-empty-copy span {
  font-size: 13px;
  color: var(--omni-muted, #6b7280);
  line-height: 1.5;
}
.browser-empty-sources {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px;
  width: 100%;
  margin-top: 4px;
}
.source-chip {
  display: flex;
  flex-direction: column;
  gap: 4px;
  text-align: left;
  padding: 12px 14px;
  border: 1px solid var(--omni-border, #e5e7eb);
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  color: inherit;
}
.source-chip:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
}
.source-chip strong {
  font-size: 13px;
  color: var(--omni-text, #111827);
}
.source-chip span {
  font-size: 11px;
  color: var(--omni-muted, #9ca3af);
  font-weight: 600;
}
.detail-empty {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-top: 24px;
  padding: 16px 18px;
  border: 1px dashed #e5e7eb;
  border-radius: 10px;
  background: #fafbfc;
}
.pane-empty {
  padding: 24px 12px;
  text-align: center;
  font-size: 13px;
  color: #9ca3af;
}
.pane-empty.compact {
  padding: 12px;
  font-size: 12px;
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

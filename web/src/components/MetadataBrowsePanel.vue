<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { dataSourceApi } from '@/api'
import type { Id, MetadataColumn, MetadataTable } from '@/types'

const props = defineProps<{
  sourceId?: Id
  defaultSchema?: string
  /** 占满父容器（弹窗右侧大栏） */
  fill?: boolean
}>()

const emit = defineEmits<{
  insert: [text: string]
}>()

const { t } = useI18n()
const schemas = ref<string[]>([])
const tables = ref<MetadataTable[]>([])
const columns = ref<MetadataColumn[]>([])
const schemaName = ref('')
const selectedTable = ref('')
const tableKeyword = ref('')
const schemasLoading = ref(false)
const tablesLoading = ref(false)
const columnsLoading = ref(false)
/** 选中表后进入字段视图，避免表/字段双滚动挤在一起 */
const showFields = ref(false)

const filteredTables = computed(() => {
  const keyword = tableKeyword.value.trim().toLowerCase()
  if (!keyword) return tables.value
  return tables.value.filter((item) => {
    const haystack = `${item.tableName} ${item.comment || ''}`.toLowerCase()
    return haystack.includes(keyword)
  })
})

const selectedTableMeta = computed(() =>
  tables.value.find((item) => item.tableName === selectedTable.value))

watch(
  () => [props.sourceId, props.defaultSchema] as const,
  async ([sourceId, defaultSchema]) => {
    schemas.value = []
    tables.value = []
    columns.value = []
    schemaName.value = ''
    selectedTable.value = ''
    tableKeyword.value = ''
    showFields.value = false
    if (sourceId === undefined || sourceId === null || sourceId === '') return
    schemasLoading.value = true
    try {
      schemas.value = await dataSourceApi.schemas(sourceId)
      const preferred = defaultSchema?.trim()
      schemaName.value = preferred && schemas.value.includes(preferred)
        ? preferred
        : (schemas.value[0] || '')
      if (schemaName.value) await loadTables()
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : t('metaBrowse.loadFailed'))
    } finally {
      schemasLoading.value = false
    }
  },
  { immediate: true },
)

async function loadTables() {
  tables.value = []
  columns.value = []
  selectedTable.value = ''
  showFields.value = false
  if (!props.sourceId || !schemaName.value) return
  tablesLoading.value = true
  try {
    tables.value = await dataSourceApi.tables(props.sourceId, schemaName.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('metaBrowse.loadTablesFailed'))
  } finally {
    tablesLoading.value = false
  }
}

async function onSchemaChange() {
  tableKeyword.value = ''
  await loadTables()
}

async function openTable(table: MetadataTable) {
  selectedTable.value = table.tableName
  columns.value = []
  showFields.value = true
  if (!props.sourceId || !schemaName.value) return
  columnsLoading.value = true
  try {
    columns.value = await dataSourceApi.columns(props.sourceId, schemaName.value, table.tableName)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('metaBrowse.loadColumnsFailed'))
  } finally {
    columnsLoading.value = false
  }
}

function backToTables() {
  showFields.value = false
}

function qualifiedTable(tableName: string) {
  if (!schemaName.value) return tableName
  return `${schemaName.value}.${tableName}`
}

function insertTable(tableName: string) {
  emit('insert', qualifiedTable(tableName))
}

function insertColumn(column: MetadataColumn) {
  emit('insert', column.columnName)
}
</script>

<template>
  <aside class="meta-browse" :class="{ fill }" :title="t('metaBrowse.insertHint')">
    <div class="panel-head">
      <strong>{{ t('metaBrowse.title') }}</strong>
    </div>

    <div v-if="!sourceId" class="empty">{{ t('metaBrowse.needSource') }}</div>

    <template v-else>
      <template v-if="!showFields">
        <div class="filters">
          <el-select
            v-model="schemaName"
            class="full-width"
            size="small"
            filterable
            :loading="schemasLoading"
            :placeholder="t('metaBrowse.selectSchema')"
            @change="onSchemaChange"
          >
            <el-option v-for="item in schemas" :key="item" :label="item" :value="item" />
          </el-select>
          <el-input
            v-model="tableKeyword"
            clearable
            size="small"
            class="table-search"
            :placeholder="t('metaBrowse.searchTables')"
          />
        </div>

        <div v-loading="tablesLoading" class="scroll-pane">
          <div v-if="!tablesLoading && !filteredTables.length" class="empty compact">
            {{ schemas.length ? t('metaBrowse.noTables') : t('metaBrowse.noSchemas') }}
          </div>
          <button
            v-for="table in filteredTables"
            :key="table.tableName"
            type="button"
            class="row-item"
            :title="table.comment || table.tableName"
            @click="openTable(table)"
            @dblclick.stop="insertTable(table.tableName)"
          >
            <strong>{{ table.tableName }}</strong>
            <span v-if="table.comment" class="muted">{{ table.comment }}</span>
          </button>
        </div>
      </template>

      <template v-else>
        <div class="fields-toolbar">
          <el-button link type="primary" @click="backToTables">{{ t('metaBrowse.backToTables') }}</el-button>
          <el-button link type="primary" @click="insertTable(selectedTable)">{{ t('metaBrowse.insertTable') }}</el-button>
        </div>
        <div class="fields-title" :title="selectedTableMeta?.comment || selectedTable">
          <strong>{{ selectedTable }}</strong>
          <span v-if="selectedTableMeta?.comment" class="muted">{{ selectedTableMeta.comment }}</span>
        </div>

        <div v-loading="columnsLoading" class="scroll-pane">
          <div v-if="!columnsLoading && !columns.length" class="empty compact">{{ t('metaBrowse.noColumns') }}</div>
          <button
            v-for="column in columns"
            :key="column.columnName"
            type="button"
            class="row-item"
            :title="t('metaBrowse.clickInsert')"
            @click="insertColumn(column)"
          >
            <div class="column-main">
              <strong>{{ column.columnName }}</strong>
              <span class="type">{{ column.typeName }}</span>
            </div>
            <span class="muted">{{ column.comment?.trim() || t('common.emptyDash') }}</span>
          </button>
        </div>
      </template>
    </template>
  </aside>
</template>

<style scoped>
.meta-browse {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 320px;
  min-width: 300px;
  flex: 0 0 320px;
  align-self: stretch;
  min-height: 280px;
  max-height: 480px;
  height: auto;
  padding: 12px;
  border: 1px solid var(--omni-border);
  border-radius: 10px;
  background: var(--omni-surface);
  box-sizing: border-box;
  overflow: hidden;
}
.meta-browse.fill {
  width: 100%;
  min-width: 0;
  flex: 1 1 auto;
  height: 100%;
  min-height: 0;
  max-height: 100%;
}
.panel-head strong {
  font-size: 13px;
  color: var(--omni-text);
}
.filters {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.full-width,
.table-search { width: 100%; }
.scroll-pane {
  flex: 1 1 auto;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  border: 1px solid var(--omni-border);
  border-radius: 8px;
  background: var(--omni-card);
  overscroll-behavior: contain;
}
.meta-browse:not(.fill) .scroll-pane {
  min-height: 120px;
}
.row-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  padding: 8px 10px;
  border: 0;
  border-bottom: 1px solid var(--omni-border);
  background: transparent;
  text-align: left;
  cursor: pointer;
  color: inherit;
}
.row-item:last-child { border-bottom: 0; }
.row-item:hover { background: var(--omni-accent-soft, rgba(64, 158, 255, 0.08)); }
.row-item strong {
  font-size: 12px;
  font-weight: 600;
  color: var(--omni-text);
  word-break: break-all;
  line-height: 1.35;
}
.muted {
  font-size: 11px;
  color: var(--omni-muted);
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.column-main {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.type {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--omni-muted);
}
.fields-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.fields-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.fields-title strong {
  font-size: 13px;
  color: var(--omni-text);
  word-break: break-all;
}
.empty {
  padding: 20px 12px;
  color: var(--omni-muted);
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
}
.empty.compact { padding: 16px 12px; }
</style>

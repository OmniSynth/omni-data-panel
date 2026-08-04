<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { dataSourceApi, dataSourceObjectAclApi, roleApi } from '@/api'
import type { Id, MetadataColumn, MetadataTable, ObjectAclColumnRef, ObjectAclTableRef, Role } from '@/types'

const props = defineProps<{
  sourceId?: Id
  sourceName?: string
}>()
const visible = defineModel<boolean>({ required: true })
const { t } = useI18n()

const roles = ref<Role[]>([])
const roleId = ref<Id | ''>('')
const schemas = ref<string[]>([])
const tablesBySchema = ref<Record<string, MetadataTable[]>>({})
const columnsByTable = ref<Record<string, MetadataColumn[]>>({})
const deniedTables = ref<string[]>([])
const deniedColumns = ref<string[]>([])
const expandedSchemas = ref<string[]>([])
const loading = ref(false)
const saving = ref(false)
const metaLoading = ref(false)

const assignableRoles = computed(() => roles.value.filter((role) => role.enabled && role.code !== 'ADMIN'))

function tableKey(schema: string, table: string) {
  return `${schema}\u0001${table}`
}

function columnKey(schema: string, table: string, column: string) {
  return `${schema}\u0001${table}\u0001${column}`
}

function parseTableKey(key: string): ObjectAclTableRef {
  const [schemaName, tableName] = key.split('\u0001')
  return { schemaName, tableName }
}

function parseColumnKey(key: string): ObjectAclColumnRef {
  const [schemaName, tableName, columnName] = key.split('\u0001')
  return { schemaName, tableName, columnName }
}

async function loadRoles() {
  roles.value = await roleApi.list()
  if (!roleId.value && assignableRoles.value.length) {
    roleId.value = assignableRoles.value[0].id
  }
}

async function loadMeta() {
  if (!props.sourceId) return
  metaLoading.value = true
  try {
    schemas.value = await dataSourceApi.schemas(props.sourceId)
    const entries = await Promise.all(schemas.value.map(async (schema) => {
      const tables = await dataSourceApi.tables(props.sourceId!, schema)
      return [schema, tables] as const
    }))
    tablesBySchema.value = Object.fromEntries(entries)
    columnsByTable.value = {}
  } finally {
    metaLoading.value = false
  }
}

async function loadAcl() {
  if (!props.sourceId || roleId.value === '') return
  const acl = await dataSourceObjectAclApi.get(props.sourceId, roleId.value)
  deniedTables.value = acl.tables.map((item) => tableKey(item.schemaName, item.tableName))
  deniedColumns.value = acl.columns.map((item) =>
    columnKey(item.schemaName, item.tableName, item.columnName))
}

async function ensureColumns(schema: string, table: string) {
  const key = tableKey(schema, table)
  if (columnsByTable.value[key] || !props.sourceId) return
  columnsByTable.value[key] = await dataSourceApi.columns(props.sourceId, schema, table)
}

async function loadAll() {
  if (!props.sourceId) return
  loading.value = true
  try {
    await Promise.all([loadRoles(), loadMeta()])
    await loadAcl()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('objectAcl.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!props.sourceId || roleId.value === '') return
  saving.value = true
  try {
    await dataSourceObjectAclApi.replace(props.sourceId, {
      roleId: roleId.value,
      deniedTables: deniedTables.value.map(parseTableKey),
      deniedColumns: deniedColumns.value.map(parseColumnKey),
    })
    ElMessage.success(t('objectAcl.saved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('objectAcl.saveFailed'))
  } finally {
    saving.value = false
  }
}

function onTableCheck(schema: string, table: string, checked: boolean) {
  const key = tableKey(schema, table)
  if (checked) {
    if (!deniedTables.value.includes(key)) deniedTables.value.push(key)
    // 表被拒后清掉该表下列拒绝（表拒绝已覆盖）
    deniedColumns.value = deniedColumns.value.filter((item) => !item.startsWith(`${key}\u0001`))
  } else {
    deniedTables.value = deniedTables.value.filter((item) => item !== key)
  }
}

function onColumnCheck(schema: string, table: string, column: string, checked: boolean) {
  const key = columnKey(schema, table, column)
  if (checked) {
    if (!deniedColumns.value.includes(key)) deniedColumns.value.push(key)
  } else {
    deniedColumns.value = deniedColumns.value.filter((item) => item !== key)
  }
}

function isTableDenied(schema: string, table: string) {
  return deniedTables.value.includes(tableKey(schema, table))
}

function isColumnDenied(schema: string, table: string, column: string) {
  return deniedColumns.value.includes(columnKey(schema, table, column))
}

watch(visible, (opened) => {
  if (opened) loadAll()
  else {
    roles.value = []
    roleId.value = ''
    schemas.value = []
    tablesBySchema.value = {}
    columnsByTable.value = {}
    deniedTables.value = []
    deniedColumns.value = []
    expandedSchemas.value = []
  }
})

watch(roleId, () => {
  if (visible.value && roleId.value !== '') {
    loadAcl().catch((error) => {
      ElMessage.error(error instanceof Error ? error.message : t('objectAcl.loadFailed'))
    })
  }
})
</script>

<template>
  <el-drawer
    v-model="visible"
    :title="t('objectAcl.title', { name: sourceName || '' })"
    size="720px"
    destroy-on-close
  >
    <div v-loading="loading" class="body">
      <p class="hint">{{ t('objectAcl.hint') }}</p>
      <el-form label-width="72px">
        <el-form-item :label="t('objectAcl.role')">
          <el-select v-model="roleId" filterable class="full-width" :placeholder="t('objectAcl.selectRole')">
            <el-option
              v-for="role in assignableRoles"
              :key="role.id"
              :label="`${role.name} (${role.code})`"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <div v-loading="metaLoading" class="tree">
        <el-collapse v-model="expandedSchemas">
          <el-collapse-item v-for="schema in schemas" :key="schema" :name="schema" :title="schema">
            <div
              v-for="table in tablesBySchema[schema] || []"
              :key="table.tableName"
              class="table-block"
            >
              <div class="table-row">
                <el-checkbox
                  :model-value="isTableDenied(schema, table.tableName)"
                  @update:model-value="(val: boolean | string | number) => onTableCheck(schema, table.tableName, val === true)"
                >
                  {{ t('objectAcl.denyTable') }} · {{ table.tableName }}
                </el-checkbox>
                <el-button
                  link
                  type="primary"
                  :disabled="isTableDenied(schema, table.tableName)"
                  @click="ensureColumns(schema, table.tableName)"
                >{{ t('objectAcl.columns') }}</el-button>
              </div>
              <div
                v-if="columnsByTable[tableKey(schema, table.tableName)] && !isTableDenied(schema, table.tableName)"
                class="column-list"
              >
                <el-checkbox
                  v-for="column in columnsByTable[tableKey(schema, table.tableName)]"
                  :key="column.columnName"
                  :model-value="isColumnDenied(schema, table.tableName, column.columnName)"
                  @update:model-value="(val: boolean | string | number) => onColumnCheck(schema, table.tableName, column.columnName, val === true)"
                >
                  {{ column.columnName }}
                  <span class="col-type">{{ column.typeName }}</span>
                </el-checkbox>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
        <el-empty v-if="!schemas.length && !metaLoading" :description="t('objectAcl.noMeta')" />
      </div>
    </div>
    <template #footer>
      <el-button @click="visible = false">{{ t('common.close') }}</el-button>
      <el-button type="primary" :loading="saving" :disabled="!roleId" @click="save">{{ t('common.save') }}</el-button>
    </template>
  </el-drawer>
</template>

<style scoped>
.body { display: flex; flex-direction: column; gap: 12px; min-height: 360px; }
.hint { margin: 0; color: var(--omni-muted); font-size: 12px; line-height: 1.5; }
.full-width { width: 100%; }
.tree { max-height: calc(100vh - 260px); overflow: auto; }
.table-block { padding: 6px 0 10px; border-bottom: 1px solid var(--omni-border); }
.table-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.column-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  padding: 8px 0 0 22px;
}
.col-type { margin-left: 6px; color: var(--omni-muted); font-size: 12px; }
</style>

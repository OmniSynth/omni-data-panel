<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { confirmBox } from '@/i18n/dialog'
import { dataSourceApi } from '@/api'
import { displayLabel } from '@/display'
import { requiredRule, validateForm } from '@/form/rules'
import { useUserStore } from '@/stores/user'
import type { DataSource, DialectInfo, Id } from '@/types'
import RoleResourcePermissionPanel from '@/components/RoleResourcePermissionPanel.vue'
import DataSourceObjectAclPanel from '@/components/DataSourceObjectAclPanel.vue'

const { t } = useI18n()
const userStore = useUserStore()
const rows = ref<DataSource[]>([])
const dialects = ref<DialectInfo[]>([])
const loading = ref(false)
const visible = ref(false)
const permissionVisible = ref(false)
const permissionSource = ref<DataSource>()
const objectAclVisible = ref(false)
const objectAclSource = ref<DataSource>()
const saving = ref(false)
const editingId = ref<Id>()
const formRef = ref<FormInstance>()

function defaultDialectCode() {
  return dialects.value[0]?.code || 'MYSQL'
}

function defaultPortFor(code: string) {
  return dialects.value.find((item) => item.code === code)?.defaultPort ?? 3306
}

const emptyForm = () => {
  const dialect = defaultDialectCode()
  return {
    name: '',
    host: '',
    port: defaultPortFor(dialect),
    defaultDatabase: '',
    username: '',
    password: '',
    dialect,
    jdbcUrl: '',
  }
}
const form = reactive(emptyForm())

const formRules = computed<FormRules>(() => {
  const rules: FormRules = {
    name: [requiredRule(t('common.pleaseEnter', { field: t('common.name') }))],
    dialect: [requiredRule(t('common.pleaseSelect', { field: t('dataSource.dialect') }), 'change')],
    host: [requiredRule(t('common.pleaseEnter', { field: t('dataSource.host') }))],
    port: [{
      required: true,
      trigger: 'change',
      validator: (_rule, value, callback) => {
        const port = Number(value)
        if (!port || port < 1 || port > 65535) callback(new Error(t('dataSource.portRange')))
        else callback()
      },
    }],
    username: [requiredRule(t('common.pleaseEnter', { field: t('dataSource.username') }))],
  }
  if (editingId.value === undefined) {
    rules.password = [requiredRule(t('common.pleaseEnter', { field: t('dataSource.password') }))]
  }
  return rules
})

async function loadDialects() {
  try {
    dialects.value = await dataSourceApi.dialects()
  } catch {
    dialects.value = [
      { code: 'MYSQL', label: 'MySQL', defaultPort: 3306 },
      { code: 'MARIADB', label: 'MariaDB', defaultPort: 3306 },
      { code: 'POSTGRESQL', label: 'PostgreSQL', defaultPort: 5432 },
      { code: 'MSSQL', label: 'SQL Server', defaultPort: 1433 },
      { code: 'ORACLE', label: 'Oracle', defaultPort: 1521 },
      { code: 'CLICKHOUSE', label: 'ClickHouse', defaultPort: 8123 },
      { code: 'HIVE', label: 'Hive', defaultPort: 10000 },
      { code: 'SPARK', label: 'Spark', defaultPort: 10000 },
    ]
  }
}

async function load() {
  loading.value = true
  try { rows.value = await dataSourceApi.list() }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : t('dataSource.loadFailed')) }
  finally { loading.value = false }
}

function open(row?: DataSource) {
  Object.assign(form, row ? {
    name: row.name,
    host: row.host || '',
    port: row.port ?? defaultPortFor(row.dialect || defaultDialectCode()),
    defaultDatabase: row.defaultDatabase || '',
    username: row.username || '',
    password: '',
    dialect: row.dialect || defaultDialectCode(),
    jdbcUrl: row.jdbcUrl || '',
  } : emptyForm())
  editingId.value = row?.id
  visible.value = true
}

function onDialectChange(code: string) {
  form.dialect = code
  if (editingId.value === undefined) {
    form.port = defaultPortFor(code)
  }
}

async function save() {
  if (!(await validateForm(formRef.value))) return
  const data: Partial<DataSource> = {
    name: form.name.trim(),
    host: form.host.trim(),
    port: Number(form.port),
    defaultDatabase: form.defaultDatabase.trim() || null,
    username: form.username.trim(),
    password: form.password || undefined,
    dialect: form.dialect || undefined,
  }
  saving.value = true
  try {
    if (editingId.value !== undefined) await dataSourceApi.update(editingId.value, data)
    else await dataSourceApi.create(data)
    visible.value = false
    ElMessage.success(t('dataSource.saved'))
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : t('common.saveFailed')) }
  finally { saving.value = false }
}

function authorize(row: DataSource) {
  permissionSource.value = row
  permissionVisible.value = true
}

function configureObjectAcl(row: DataSource) {
  objectAclSource.value = row
  objectAclVisible.value = true
}

async function test(id: Id) {
  try {
    await dataSourceApi.test(id)
    ElMessage.success(t('dataSource.testOk'))
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : t('dataSource.testFailed')) }
}

async function sync(id: Id) {
  try { await dataSourceApi.sync(id); ElMessage.success(t('dataSource.syncOk')) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : t('dataSource.syncFailed')) }
}

async function remove(id: Id) {
  try {
    await confirmBox(t('dataSource.deleteConfirm'), t('common.deleteConfirmTitle'), { type: 'warning' })
    await dataSourceApi.remove(id)
    ElMessage.success(t('dataSource.deleted'))
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('common.deleteFailed'))
  }
}

function endpointText(row: DataSource) {
  if (!row.host) return t('common.emptyDash')
  const db = row.defaultDatabase ? ` / ${row.defaultDatabase}` : ` ${t('dataSource.allDatabases')}`
  return `${row.host}:${row.port ?? defaultPortFor(row.dialect || 'MYSQL')}${db}`
}

function dialectLabel(code?: string) {
  if (!code) return t('common.emptyDash')
  return dialects.value.find((item) => item.code === code)?.label || displayLabel(code)
}

onMounted(async () => {
  await loadDialects()
  await load()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">{{ t('dataSource.title') }}</h1>
      <el-button v-if="userStore.isAdmin" type="primary" @click="open()">{{ t('dataSource.create') }}</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" :empty-text="t('dataSource.empty')">
      <el-table-column prop="name" :label="t('common.name')" />
      <el-table-column :label="t('dataSource.dialect')" width="110">
        <template #default="{ row }">{{ dialectLabel(row.dialect) }}</template>
      </el-table-column>
      <el-table-column :label="t('dataSource.connection')" min-width="260">
        <template #default="{ row }">{{ endpointText(row) }}</template>
      </el-table-column>
      <el-table-column prop="username" :label="t('dataSource.username')" />
      <el-table-column :label="t('common.status')" width="100">
        <template #default="{ row }">{{ displayLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column v-if="userStore.isAdmin" :label="t('common.actions')" width="460">
        <template #default="{ row }">
          <el-button link type="primary" @click="test(row.id)">{{ t('dataSource.test') }}</el-button>
          <el-button link type="primary" @click="sync(row.id)">{{ t('dataSource.syncMeta') }}</el-button>
          <el-button link @click="open(row)">{{ t('common.edit') }}</el-button>
          <el-button link type="primary" @click="authorize(row)">{{ t('dataSource.roleAuth') }}</el-button>
          <el-button link type="primary" @click="configureObjectAcl(row)">{{ t('objectAcl.action') }}</el-button>
          <el-button link type="danger" @click="remove(row.id)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="visible" :title="editingId === undefined ? t('dataSource.createTitle') : t('dataSource.editTitle')" width="640px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item :label="t('common.name')" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item :label="t('dataSource.dialect')" prop="dialect">
          <el-select
            :model-value="form.dialect"
            class="full-width"
            :placeholder="t('dataSource.dialectHint')"
            @change="onDialectChange"
          >
            <el-option
              v-for="item in dialects"
              :key="item.code"
              :label="item.label"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('dataSource.host')" prop="host"><el-input v-model="form.host" placeholder="127.0.0.1" /></el-form-item>
        <el-form-item :label="t('dataSource.port')" prop="port">
          <el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" class="full-width" />
        </el-form-item>
        <el-form-item :label="t('dataSource.database')">
          <el-input v-model="form.defaultDatabase" :placeholder="t('dataSource.optional')" />
          <div class="field-hint">
            {{ t('dataSource.databaseHint') }}
          </div>
        </el-form-item>
        <el-form-item :label="t('dataSource.username')" prop="username"><el-input v-model="form.username" /></el-form-item>
        <el-form-item :label="t('dataSource.password')" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="editingId === undefined ? t('dataSource.passwordPlaceholder') : t('dataSource.passwordKeep')"
          />
        </el-form-item>
        <el-form-item v-if="form.jdbcUrl" :label="t('dataSource.jdbcUrl')">
          <el-input :model-value="form.jdbcUrl" readonly />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible=false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
    <RoleResourcePermissionPanel
      v-model="permissionVisible"
      resource-type="DATA_SOURCE"
      :resource-id="permissionSource?.id"
      :allowed-permissions="['READ']"
      :title="`${t('dataSource.roleAuthTitle')}${permissionSource?.name || ''}`"
    />
    <DataSourceObjectAclPanel
      v-model="objectAclVisible"
      :source-id="objectAclSource?.id"
      :source-name="objectAclSource?.name"
    />
  </div>
</template>

<style scoped>
.full-width { width: 100%; }
.field-hint {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: #6b7280;
}
.field-hint code {
  padding: 0 4px;
  border-radius: 3px;
  background: #f3f4f6;
  color: #374151;
}
</style>

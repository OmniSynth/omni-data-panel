<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dataSourceApi } from '@/api'
import { displayLabel } from '@/display'
import { useUserStore } from '@/stores/user'
import type { DataSource, DialectInfo, Id } from '@/types'
import RoleResourcePermissionPanel from '@/components/RoleResourcePermissionPanel.vue'

const userStore = useUserStore()
const rows = ref<DataSource[]>([])
const dialects = ref<DialectInfo[]>([])
const loading = ref(false)
const visible = ref(false)
const permissionVisible = ref(false)
const permissionSource = ref<DataSource>()
const saving = ref(false)
const editingId = ref<Id>()

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

async function loadDialects() {
  try {
    dialects.value = await dataSourceApi.dialects()
  } catch {
    dialects.value = [
      { code: 'MYSQL', label: 'MySQL', defaultPort: 3306 },
      { code: 'MARIADB', label: 'MariaDB', defaultPort: 3306 },
    ]
  }
}

async function load() {
  loading.value = true
  try { rows.value = await dataSourceApi.list() }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '数据源加载失败') }
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
  if (!form.name || !form.host || !form.username) return ElMessage.warning('请填写名称、主机与用户名')
  if (!form.port || form.port < 1 || form.port > 65535) return ElMessage.warning('端口须在 1–65535 之间')
  if (!form.dialect) return ElMessage.warning('请选择方言')
  if (editingId.value === undefined && !form.password) return ElMessage.warning('请输入密码')
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
    ElMessage.success('数据源已保存')
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') }
  finally { saving.value = false }
}

function authorize(row: DataSource) {
  permissionSource.value = row
  permissionVisible.value = true
}

async function test(id: Id) {
  try {
    await dataSourceApi.test(id)
    ElMessage.success('连接测试成功')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '连接测试失败') }
}

async function sync(id: Id) {
  try { await dataSourceApi.sync(id); ElMessage.success('元数据同步完成') }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '同步失败') }
}

async function remove(id: Id) {
  try {
    await ElMessageBox.confirm('删除后不可恢复，确认删除该数据源？', '删除确认', { type: 'warning' })
    await dataSourceApi.remove(id)
    ElMessage.success('数据源已删除')
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

function endpointText(row: DataSource) {
  if (!row.host) return '—'
  const db = row.defaultDatabase ? ` / ${row.defaultDatabase}` : '（全部业务库）'
  return `${row.host}:${row.port ?? defaultPortFor(row.dialect || 'MYSQL')}${db}`
}

function dialectLabel(code?: string) {
  if (!code) return '—'
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
      <h1 class="page-title">数据源</h1>
      <el-button v-if="userStore.isAdmin" type="primary" @click="open()">新增数据源</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" empty-text="暂无可访问的数据源">
      <el-table-column prop="name" label="名称" />
      <el-table-column label="方言" width="110">
        <template #default="{ row }">{{ dialectLabel(row.dialect) }}</template>
      </el-table-column>
      <el-table-column label="连接" min-width="260">
        <template #default="{ row }">{{ endpointText(row) }}</template>
      </el-table-column>
      <el-table-column prop="username" label="用户名" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ displayLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column v-if="userStore.isAdmin" label="操作" width="390">
        <template #default="{ row }">
          <el-button link type="primary" @click="test(row.id)">测试</el-button>
          <el-button link type="primary" @click="sync(row.id)">同步元数据</el-button>
          <el-button link @click="open(row)">编辑</el-button>
          <el-button link type="primary" @click="authorize(row)">角色授权</el-button>
          <el-button link type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="visible" :title="editingId === undefined ? '新增数据源' : '编辑数据源'" width="640px">
      <el-form label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="方言">
          <el-select
            :model-value="form.dialect"
            class="full-width"
            placeholder="选择可连接的数据库方言"
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
        <el-form-item label="主机"><el-input v-model="form.host" placeholder="例如 127.0.0.1" /></el-form-item>
        <el-form-item label="端口">
          <el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" class="full-width" />
        </el-form-item>
        <el-form-item label="库名">
          <el-input v-model="form.defaultDatabase" placeholder="选填" />
          <div class="field-hint">
            未填库名时将同步该账号可见的全部业务库，SQL 可用 <code>库名.表名</code> 跨库查询。
          </div>
        </el-form-item>
        <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="editingId === undefined ? '请输入密码' : '留空表示不修改'"
          />
        </el-form-item>
        <el-form-item v-if="form.jdbcUrl" label="连接串">
          <el-input :model-value="form.jdbcUrl" readonly />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
    <RoleResourcePermissionPanel
      v-model="permissionVisible"
      resource-type="DATA_SOURCE"
      :resource-id="permissionSource?.id"
      :allowed-permissions="['READ']"
      :title="`数据源角色授权：${permissionSource?.name || ''}`"
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

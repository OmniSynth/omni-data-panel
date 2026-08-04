<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { roleApi, userApi } from '@/api'
import type { AdminUser, Id, Permission, Role } from '@/types'

const roles = ref<Role[]>([])
const users = ref<AdminUser[]>([])
const catalog = ref<Permission[]>([])
const loading = ref(false)
const formVisible = ref(false)
const permissionVisible = ref(false)
const memberVisible = ref(false)
const saving = ref(false)
const editingId = ref<Id>()
const activeRole = ref<Role>()
const selectedPermissions = ref<string[]>([])
const updatingUserId = ref('')
const emptyForm = () => ({ code: '', name: '', description: '', enabled: true })
const form = reactive(emptyForm())
const currentMembers = computed(() => activeRole.value
  ? users.value.filter((user) => user.roleIds.some((id) => String(id) === String(activeRole.value!.id)))
  : [])

async function load() {
  loading.value = true
  try {
    [roles.value, catalog.value, users.value] = await Promise.all([
      roleApi.list(), roleApi.permissions(), userApi.list(),
    ])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '角色加载失败')
  } finally {
    loading.value = false
  }
}

function openForm(role?: Role) {
  Object.assign(form, role ? {
    code: role.code,
    name: role.name,
    description: role.description || '',
    enabled: role.enabled,
  } : emptyForm())
  editingId.value = role?.id
  formVisible.value = true
}

async function saveRole() {
  if (!form.name.trim() || (editingId.value === undefined && !form.code.trim())) {
    return ElMessage.warning('请填写角色编码和名称')
  }
  saving.value = true
  try {
    if (editingId.value === undefined) {
      await roleApi.create({ ...form, code: form.code.trim(), name: form.name.trim() })
    } else {
      await roleApi.update(editingId.value, {
        name: form.name.trim(),
        description: form.description,
        enabled: form.enabled,
      })
    }
    formVisible.value = false
    ElMessage.success('角色已保存')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '角色保存失败')
  } finally {
    saving.value = false
  }
}

function openPermissions(role: Role) {
  activeRole.value = role
  selectedPermissions.value = [...role.permissions]
  permissionVisible.value = true
}

async function savePermissions() {
  if (!activeRole.value) return
  saving.value = true
  try {
    await roleApi.savePermissions(activeRole.value.id, selectedPermissions.value)
    permissionVisible.value = false
    ElMessage.success('功能权限已保存')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '功能权限保存失败')
  } finally {
    saving.value = false
  }
}

function openMembers(role: Role) {
  activeRole.value = role
  memberVisible.value = true
}

async function setMembership(user: AdminUser, selected: boolean) {
  if (!activeRole.value) return
  const roleId = activeRole.value.id
  const roleIds = selected
    ? [...user.roleIds, roleId].filter((id, index, all) =>
      all.findIndex((item) => String(item) === String(id)) === index)
    : user.roleIds.filter((id) => String(id) !== String(roleId))
  updatingUserId.value = String(user.id)
  try {
    const updated = await userApi.update(user.id, {
      displayName: user.displayName,
      enabled: user.enabled,
      roleIds,
    })
    Object.assign(user, updated)
    ElMessage.success(selected ? '成员已加入角色' : '成员已移出角色')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '成员更新失败')
  } finally {
    updatingUserId.value = ''
  }
}

async function removeRole(role: Role) {
  try {
    await ElMessageBox.confirm(`确认删除角色“${role.name}”？`, '删除确认', { type: 'warning' })
    await roleApi.remove(role.id)
    ElMessage.success('角色已删除')
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '角色删除失败')
  }
}

function resetForm() {
  editingId.value = undefined
  Object.assign(form, emptyForm())
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">角色管理</h1>
      <el-button type="primary" @click="openForm()">新增角色</el-button>
    </div>
    <el-table v-loading="loading" :data="roles" empty-text="暂无角色">
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="code" label="编码" />
      <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '禁用' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="功能权限" min-width="180">
        <template #default="{ row }">{{ row.permissions.length ? row.permissions.join('、') : '无' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button link :disabled="row.builtIn || row.code === 'ADMIN'" @click="openForm(row)">编辑</el-button>
          <el-button link type="primary" :disabled="row.builtIn || row.code === 'ADMIN'" @click="openPermissions(row)">权限</el-button>
          <el-button link :disabled="row.builtIn || row.code === 'ADMIN'" @click="openMembers(row)">成员</el-button>
          <el-button link type="danger" :disabled="row.builtIn || row.code === 'ADMIN'" @click="removeRole(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="formVisible"
      :title="editingId === undefined ? '新增角色' : '编辑角色'"
      width="520px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form label-width="90px">
        <el-form-item label="角色编码"><el-input v-model="form.code" :disabled="editingId !== undefined" placeholder="大写字母、数字或下划线" /></el-form-item>
        <el-form-item label="角色名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="permissionVisible" :title="`功能权限：${activeRole?.name || ''}`" width="620px" destroy-on-close>
      <el-checkbox-group v-model="selectedPermissions" class="permission-list">
        <el-checkbox v-for="permission in catalog" :key="permission.id" :value="permission.code">
          {{ permission.name }}（{{ permission.code }}）
        </el-checkbox>
      </el-checkbox-group>
      <el-empty v-if="!catalog.length" description="暂无功能权限" />
      <template #footer>
        <el-button @click="permissionVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePermissions">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="memberVisible" :title="`成员管理：${activeRole?.name || ''}`" width="700px" destroy-on-close>
      <el-table :data="users" empty-text="暂无用户">
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="displayName" label="显示名称" />
        <el-table-column label="当前角色" min-width="180"><template #default="{ row }">{{ row.roles.join('、') }}</template></el-table-column>
        <el-table-column label="成员" width="100">
          <template #default="{ row }">
            <el-switch
              :model-value="currentMembers.includes(row)"
              :loading="updatingUserId === String(row.id)"
              :disabled="row.roles.includes('ADMIN')"
              @change="setMembership(row, Boolean($event))"
            />
          </template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="memberVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.permission-list { display: flex; flex-direction: column; gap: 10px; }
</style>

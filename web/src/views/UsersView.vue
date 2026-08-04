<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { roleApi, userApi } from '@/api'
import type { AdminUser, Id, Role } from '@/types'

const users = ref<AdminUser[]>([])
const roles = ref<Role[]>([])
const loading = ref(false)
const visible = ref(false)
const passwordVisible = ref(false)
const saving = ref(false)
const editingId = ref<Id>()
const passwordUser = ref<AdminUser>()
const password = ref('')
const emptyForm = () => ({
  username: '',
  password: '',
  displayName: '',
  enabled: true,
  roleIds: [] as Id[],
})
const form = reactive(emptyForm())
const assignableRoles = computed(() => roles.value.filter((role) =>
  role.enabled && role.code !== 'ADMIN'))

async function load() {
  loading.value = true
  try {
    [users.value, roles.value] = await Promise.all([userApi.list(), roleApi.list()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '用户加载失败')
  } finally {
    loading.value = false
  }
}

function open(user?: AdminUser) {
  Object.assign(form, user ? {
    username: user.username,
    password: '',
    displayName: user.displayName,
    enabled: user.enabled,
    roleIds: [...user.roleIds],
  } : emptyForm())
  editingId.value = user?.id
  visible.value = true
}

async function save() {
  if (!form.username.trim() || !form.displayName.trim() || !form.roleIds.length) {
    return ElMessage.warning('请完整填写用户信息并选择角色')
  }
  if (editingId.value === undefined && form.password.length < 10) {
    return ElMessage.warning('密码至少需要10位')
  }
  saving.value = true
  try {
    if (editingId.value === undefined) {
      await userApi.create({
        username: form.username.trim(),
        password: form.password,
        displayName: form.displayName.trim(),
        roleIds: form.roleIds,
      })
    } else {
      await userApi.update(editingId.value, {
        displayName: form.displayName.trim(),
        enabled: form.enabled,
        roleIds: form.roleIds,
      })
    }
    visible.value = false
    ElMessage.success('用户已保存')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '用户保存失败')
  } finally {
    saving.value = false
  }
}

function openPassword(user: AdminUser) {
  passwordUser.value = user
  password.value = ''
  passwordVisible.value = true
}

async function resetPassword() {
  if (!passwordUser.value) return
  if (password.value.length < 10) return ElMessage.warning('密码至少需要10位')
  saving.value = true
  try {
    await userApi.resetPassword(passwordUser.value.id, password.value)
    passwordVisible.value = false
    ElMessage.success('密码已重置')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '密码重置失败')
  } finally {
    saving.value = false
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
      <h1 class="page-title">用户管理</h1>
      <el-button type="primary" @click="open()">新增用户</el-button>
    </div>
    <el-table v-loading="loading" :data="users" empty-text="暂无用户">
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="displayName" label="显示名称" />
      <el-table-column label="角色" min-width="180"><template #default="{ row }">{{ row.roles.join('、') }}</template></el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '禁用' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link :disabled="row.roles.includes('ADMIN')" @click="open(row)">编辑</el-button>
          <el-button link type="primary" :disabled="row.roles.includes('ADMIN')" @click="openPassword(row)">重置密码</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="visible"
      :title="editingId === undefined ? '新增用户' : '编辑用户'"
      width="560px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form label-width="90px">
        <el-form-item label="用户名"><el-input v-model="form.username" :disabled="editingId !== undefined" /></el-form-item>
        <el-form-item v-if="editingId === undefined" label="初始密码">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" placeholder="至少10位" />
        </el-form-item>
        <el-form-item label="显示名称"><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple class="full-width" placeholder="请选择至少一个角色">
            <el-option v-for="role in assignableRoles" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editingId !== undefined" label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordVisible" :title="`重置密码：${passwordUser?.username || ''}`" width="460px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="新密码"><el-input v-model="password" type="password" show-password autocomplete="new-password" placeholder="至少10位" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="resetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

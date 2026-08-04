<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { roleApi, userApi } from '@/api'
import type { AdminUser, Id, Role } from '@/types'

const { t } = useI18n()
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
    ElMessage.error(error instanceof Error ? error.message : t('users.loadFailed'))
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
    return ElMessage.warning(t('users.needComplete'))
  }
  if (editingId.value === undefined && form.password.length < 10) {
    return ElMessage.warning(t('users.passwordMin'))
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
    ElMessage.success(t('users.saved'))
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('users.saveFailed'))
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
  if (password.value.length < 10) return ElMessage.warning(t('users.passwordMin'))
  saving.value = true
  try {
    await userApi.resetPassword(passwordUser.value.id, password.value)
    passwordVisible.value = false
    ElMessage.success(t('users.passwordReset'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('users.passwordResetFailed'))
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
      <h1 class="page-title">{{ t('users.title') }}</h1>
      <el-button type="primary" @click="open()">{{ t('users.create') }}</el-button>
    </div>
    <el-table v-loading="loading" :data="users" :empty-text="t('users.empty')">
      <el-table-column prop="username" :label="t('users.username')" />
      <el-table-column prop="displayName" :label="t('users.displayName')" />
      <el-table-column :label="t('users.roles')" min-width="180"><template #default="{ row }">{{ row.roles.join('、') }}</template></el-table-column>
      <el-table-column :label="t('common.status')" width="90">
        <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? t('common.enabled') : t('common.disabled') }}</el-tag></template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="160">
        <template #default="{ row }">
          <el-button link :disabled="row.roles.includes('ADMIN')" @click="open(row)">{{ t('common.edit') }}</el-button>
          <el-button link type="primary" :disabled="row.roles.includes('ADMIN')" @click="openPassword(row)">{{ t('users.resetPassword') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="visible"
      :title="editingId === undefined ? t('users.createTitle') : t('users.editTitle')"
      width="560px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form label-width="90px">
        <el-form-item :label="t('users.username')"><el-input v-model="form.username" :disabled="editingId !== undefined" /></el-form-item>
        <el-form-item v-if="editingId === undefined" :label="t('users.initialPassword')">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" :placeholder="t('users.passwordHint')" />
        </el-form-item>
        <el-form-item :label="t('users.displayName')"><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item :label="t('users.roles')">
          <el-select v-model="form.roleIds" multiple class="full-width" :placeholder="t('users.needRole')">
            <el-option v-for="role in assignableRoles" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editingId !== undefined" :label="t('common.enabled')"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordVisible" :title="`${t('users.resetTitle')}${passwordUser?.username || ''}`" width="460px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item :label="t('users.newPassword')"><el-input v-model="password" type="password" show-password autocomplete="new-password" :placeholder="t('users.passwordHint')" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="resetPassword">{{ t('users.confirmReset') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

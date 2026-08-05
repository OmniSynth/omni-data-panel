<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { roleApi, settingsApi, userApi } from '@/api'
import { emailRule, minLengthRule, requiredRule, validateForm } from '@/form/rules'
import type { AdminUser, Id, Role, SiteSettings } from '@/types'

const { t } = useI18n()
const users = ref<AdminUser[]>([])
const roles = ref<Role[]>([])
const loading = ref(false)
const visible = ref(false)
const passwordVisible = ref(false)
const saving = ref(false)
const mailReady = ref(false)
const editingId = ref<Id>()
const togglingUserId = ref('')
const passwordUser = ref<AdminUser>()
const passwordForm = reactive({ password: '' })
const formRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()
const emptyForm = () => ({
  username: '',
  password: '',
  displayName: '',
  email: '',
  enabled: true,
  roleIds: [] as Id[],
})
const form = reactive(emptyForm())
const assignableRoles = computed(() => roles.value.filter((role) =>
  role.enabled && role.code !== 'ADMIN'))

const formRules = computed<FormRules>(() => {
  const rules: FormRules = {
    username: [requiredRule(t('common.pleaseEnter', { field: t('users.username') }))],
    displayName: [requiredRule(t('common.pleaseEnter', { field: t('users.displayName') }))],
    email: [
      requiredRule(t('common.pleaseEnter', { field: t('users.email') })),
      emailRule(t('users.emailInvalid')),
    ],
    roleIds: [requiredRule(t('common.pleaseSelect', { field: t('users.roles') }), 'change')],
  }
  if (editingId.value === undefined && !mailReady.value) {
    rules.password = [
      requiredRule(t('common.pleaseEnter', { field: t('users.initialPassword') })),
      minLengthRule(10, t('users.passwordMin')),
    ]
  }
  return rules
})

const passwordRules = computed<FormRules>(() => ({
  password: [
    requiredRule(t('common.pleaseEnter', { field: t('users.newPassword') })),
    minLengthRule(10, t('users.passwordMin')),
  ],
}))

async function load() {
  loading.value = true
  try {
    const [userRows, roleRows, settings] = await Promise.all([
      userApi.list(),
      roleApi.list(),
      settingsApi.get().catch((): SiteSettings => ({})),
    ])
    users.value = userRows
    roles.value = roleRows
    mailReady.value = String(settings['mail.ready']) === 'true' || settings['mail.ready'] === true
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
    email: user.email || '',
    enabled: user.enabled,
    roleIds: [...user.roleIds],
  } : emptyForm())
  editingId.value = user?.id
  visible.value = true
}

async function toggleUserEnabled(user: AdminUser, enabled: boolean) {
  if (user.roles.includes('ADMIN') || String(togglingUserId.value) === String(user.id)) return
  const previous = user.enabled
  user.enabled = enabled
  togglingUserId.value = String(user.id)
  try {
    const updated = await userApi.update(user.id, {
      displayName: user.displayName,
      email: user.email || '',
      enabled,
      roleIds: user.roleIds,
    })
    Object.assign(user, updated)
    ElMessage.success(enabled ? t('users.enabledSuccess') : t('users.disabledSuccess'))
  } catch (error) {
    user.enabled = previous
    ElMessage.error(error instanceof Error ? error.message : t('users.saveFailed'))
  } finally {
    togglingUserId.value = ''
  }
}

async function save() {
  if (!(await validateForm(formRef.value))) return
  saving.value = true
  try {
    if (editingId.value === undefined) {
      const payload: {
        username: string
        password?: string
        displayName: string
        email: string
        roleIds: Id[]
      } = {
        username: form.username.trim(),
        displayName: form.displayName.trim(),
        email: form.email.trim(),
        roleIds: form.roleIds,
      }
      if (!mailReady.value) payload.password = form.password
      await userApi.create(payload)
      ElMessage.success(mailReady.value ? t('users.inviteSent') : t('users.saved'))
    } else {
      await userApi.update(editingId.value, {
        displayName: form.displayName.trim(),
        email: form.email.trim(),
        enabled: form.enabled,
        roleIds: form.roleIds,
      })
      ElMessage.success(t('users.saved'))
    }
    visible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('users.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function openPassword(user: AdminUser) {
  if (mailReady.value) {
    try {
      await ElMessageBox.confirm(
        t('users.resetLinkConfirm', { email: user.email || '-' }),
        t('users.resetPassword'),
        { type: 'warning' },
      )
      saving.value = true
      await userApi.resetPassword(user.id)
      ElMessage.success(t('users.resetLinkSent'))
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error(error instanceof Error ? error.message : t('users.passwordResetFailed'))
      }
    } finally {
      saving.value = false
    }
    return
  }
  passwordUser.value = user
  passwordForm.password = ''
  passwordVisible.value = true
}

async function resetPassword() {
  if (!passwordUser.value) return
  if (!(await validateForm(passwordFormRef.value))) return
  saving.value = true
  try {
    await userApi.resetPassword(passwordUser.value.id, passwordForm.password)
    passwordVisible.value = false
    ElMessage.success(t('users.passwordReset'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('users.passwordResetFailed'))
  } finally {
    saving.value = false
  }
}

async function resendActivation(user: AdminUser) {
  saving.value = true
  try {
    await userApi.resendActivation(user.id)
    ElMessage.success(t('users.inviteResent'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('users.inviteResendFailed'))
  } finally {
    saving.value = false
  }
}

async function resetMfa(user: AdminUser) {
  try {
    await ElMessageBox.confirm(
      t('users.resetMfaConfirm', { name: user.displayName || user.username }),
      t('users.resetMfa'),
      { type: 'warning' },
    )
    saving.value = true
    await userApi.resetMfa(user.id)
    ElMessage.success(t('users.resetMfaSuccess'))
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : t('users.resetMfaFailed'))
    }
  } finally {
    saving.value = false
  }
}

function resetForm() {
  editingId.value = undefined
  Object.assign(form, emptyForm())
  formRef.value?.clearValidate()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">{{ t('users.title') }}</h1>
      <el-button type="primary" @click="open()">{{ t('users.create') }}</el-button>
    </div>
    <el-alert
      v-if="mailReady"
      class="mail-tip"
      type="info"
      :closable="false"
      show-icon
      :title="t('users.mailInviteTip')"
    />
    <el-table v-loading="loading" :data="users" :empty-text="t('users.empty')">
      <el-table-column prop="username" :label="t('users.username')" />
      <el-table-column prop="displayName" :label="t('users.displayName')" />
      <el-table-column prop="email" :label="t('users.email')" min-width="180" show-overflow-tooltip />
      <el-table-column :label="t('users.roles')" min-width="160"><template #default="{ row }">{{ row.roles.join('、') }}</template></el-table-column>
      <el-table-column :label="t('users.activated')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.activated ? 'success' : 'warning'">
            {{ row.activated ? t('users.activatedYes') : t('users.activatedNo') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('users.mfa')" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.totpEnabled ? 'success' : 'info'" size="small">
            {{ row.totpEnabled ? t('users.mfaOn') : t('users.mfaOff') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.status')" width="90" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled"
            :disabled="row.roles.includes('ADMIN')"
            :loading="String(togglingUserId) === String(row.id)"
            @change="(value: string | number | boolean) => toggleUserEnabled(row, Boolean(value))"
          />
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" min-width="280">
        <template #default="{ row }">
          <el-button link :disabled="row.roles.includes('ADMIN')" @click="open(row)">{{ t('common.edit') }}</el-button>
          <el-button
            v-if="mailReady && !row.activated && !row.roles.includes('ADMIN')"
            link
            type="warning"
            :loading="saving"
            @click="resendActivation(row)"
          >
            {{ t('users.resendInvite') }}
          </el-button>
          <el-button link type="primary" :disabled="row.roles.includes('ADMIN')" @click="openPassword(row)">
            {{ t('users.resetPassword') }}
          </el-button>
          <el-button
            v-if="row.totpEnabled"
            link
            type="danger"
            :loading="saving"
            @click="resetMfa(row)"
          >
            {{ t('users.resetMfa') }}
          </el-button>
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
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item :label="t('users.username')" prop="username">
          <el-input v-model="form.username" :disabled="editingId !== undefined" />
        </el-form-item>
        <el-form-item
          v-if="editingId === undefined && !mailReady"
          :label="t('users.initialPassword')"
          prop="password"
        >
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" :placeholder="t('users.passwordHint')" />
        </el-form-item>
        <el-alert
          v-if="editingId === undefined && mailReady"
          type="info"
          :closable="false"
          show-icon
          :title="t('users.createInviteHint')"
          style="margin-bottom: 16px"
        />
        <el-form-item :label="t('users.displayName')" prop="displayName">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item :label="t('users.email')" prop="email">
          <el-input v-model="form.email" autocomplete="email" />
        </el-form-item>
        <el-form-item :label="t('users.roles')" prop="roleIds">
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
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="90px">
        <el-form-item :label="t('users.newPassword')" prop="password">
          <el-input v-model="passwordForm.password" type="password" show-password autocomplete="new-password" :placeholder="t('users.passwordHint')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="resetPassword">{{ t('users.confirmReset') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mail-tip { margin-bottom: 12px; }
.full-width { width: 100%; }
</style>

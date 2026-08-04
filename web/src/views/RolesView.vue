<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmBox } from '@/i18n/dialog'
import { useI18n } from 'vue-i18n'
import { roleApi, userApi } from '@/api'
import type { AdminUser, Id, Permission, Role } from '@/types'

const { t, te } = useI18n()
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
const memberKeyword = ref('')
const emptyForm = () => ({ code: '', name: '', description: '', enabled: true })
const form = reactive(emptyForm())

const currentMembers = computed(() => activeRole.value
  ? users.value.filter((user) => user.roleIds.some((id) => String(id) === String(activeRole.value!.id)))
  : [])

const filteredUsers = computed(() => {
  const q = memberKeyword.value.trim().toLowerCase()
  if (!q) return users.value
  return users.value.filter((user) =>
    user.username.toLowerCase().includes(q) || user.displayName.toLowerCase().includes(q))
})

const permissionGroups = computed(() => {
  const map = new Map<string, Permission[]>()
  for (const permission of catalog.value) {
    const key = permission.code.includes(':') ? permission.code.split(':')[0]! : 'other'
    const list = map.get(key)
    if (list) list.push(permission)
    else map.set(key, [permission])
  }
  return [...map.entries()].map(([key, items]) => ({
    key,
    items,
    codes: items.map((item) => item.code),
  }))
})

const catalogNameMap = computed(() =>
  Object.fromEntries(catalog.value.map((item) => [item.code, item.name])))

function isLockedRole(role: Role) {
  return role.builtIn || role.code === 'ADMIN'
}

function memberCount(role: Role) {
  return users.value.filter((user) =>
    user.roleIds.some((id) => String(id) === String(role.id))).length
}

function permissionTooltip(codes: string[]) {
  return codes.map((code) => catalogNameMap.value[code] || code).join('、')
}

function groupLabel(key: string) {
  const i18nKey = `roles.groups.${key}`
  return te(i18nKey) ? t(i18nKey) : key
}

function isGroupChecked(codes: string[]) {
  return codes.length > 0 && codes.every((code) => selectedPermissions.value.includes(code))
}

function isGroupIndeterminate(codes: string[]) {
  const selected = codes.filter((code) => selectedPermissions.value.includes(code)).length
  return selected > 0 && selected < codes.length
}

function toggleGroup(codes: string[], checked: boolean) {
  if (checked) {
    selectedPermissions.value = [...new Set([...selectedPermissions.value, ...codes])]
  } else {
    const drop = new Set(codes)
    selectedPermissions.value = selectedPermissions.value.filter((code) => !drop.has(code))
  }
}

function togglePermission(code: string, checked: boolean) {
  if (checked) {
    if (!selectedPermissions.value.includes(code)) {
      selectedPermissions.value = [...selectedPermissions.value, code]
    }
  } else {
    selectedPermissions.value = selectedPermissions.value.filter((item) => item !== code)
  }
}

function selectAllPermissions() {
  selectedPermissions.value = catalog.value.map((item) => item.code)
}

function clearAllPermissions() {
  selectedPermissions.value = []
}

function onRoleAction(command: string, role: Role) {
  if (command === 'permissions') openPermissions(role)
  else if (command === 'members') openMembers(role)
  else if (command === 'delete') removeRole(role)
}

async function load() {
  loading.value = true
  try {
    [roles.value, catalog.value, users.value] = await Promise.all([
      roleApi.list(), roleApi.permissions(), userApi.list(),
    ])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('roles.loadFailed'))
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
    return ElMessage.warning(t('roles.needCodeName'))
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
    ElMessage.success(t('roles.saved'))
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('roles.saveFailed'))
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
    ElMessage.success(t('roles.permsSaved'))
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('roles.permsFailed'))
  } finally {
    saving.value = false
  }
}

function openMembers(role: Role) {
  activeRole.value = role
  memberKeyword.value = ''
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
    ElMessage.success(selected ? t('roles.memberAdded') : t('roles.memberRemoved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('roles.memberFailed'))
  } finally {
    updatingUserId.value = ''
  }
}

async function removeRole(role: Role) {
  try {
    await confirmBox(t('roles.deleteConfirm', { name: role.name }), t('common.deleteConfirmTitle'), { type: 'warning' })
    await roleApi.remove(role.id)
    ElMessage.success(t('roles.deleted'))
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('roles.deleteFailed'))
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
      <h1 class="page-title">{{ t('roles.title') }}</h1>
      <el-button type="primary" @click="openForm()">{{ t('roles.create') }}</el-button>
    </div>
    <el-table v-loading="loading" :data="roles" :empty-text="t('roles.empty')">
      <el-table-column prop="name" :label="t('common.name')" min-width="120" />
      <el-table-column prop="code" :label="t('roles.code')" min-width="120" />
      <el-table-column prop="description" :label="t('roles.remark')" min-width="180" show-overflow-tooltip />
      <el-table-column :label="t('common.status')" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? t('common.enabled') : t('common.disabled') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('roles.featurePerms')" width="110">
        <template #default="{ row }">
          <el-tooltip
            v-if="row.permissions.length"
            :content="permissionTooltip(row.permissions)"
            placement="top"
          >
            <el-tag type="info">{{ t('roles.permCount', { n: row.permissions.length }) }}</el-tag>
          </el-tooltip>
          <span v-else class="muted">{{ t('roles.none') }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('roles.members')" width="90">
        <template #default="{ row }">{{ memberCount(row) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link :disabled="isLockedRole(row)" @click="openForm(row)">{{ t('common.edit') }}</el-button>
          <el-dropdown
            trigger="click"
            :disabled="isLockedRole(row)"
            @command="(command: string) => onRoleAction(command, row)"
          >
            <el-button link :disabled="isLockedRole(row)">{{ t('common.more') }}</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="permissions">{{ t('roles.featurePerms') }}</el-dropdown-item>
                <el-dropdown-item command="members">{{ t('roles.members') }}</el-dropdown-item>
                <el-dropdown-item divided command="delete">
                  <span class="danger">{{ t('common.delete') }}</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="formVisible"
      :title="editingId === undefined ? t('roles.createTitle') : t('roles.editTitle')"
      width="520px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form label-width="90px">
        <el-form-item :label="t('roles.roleCode')">
          <el-input v-model="form.code" :disabled="editingId !== undefined" :placeholder="t('roles.codeHint')" />
        </el-form-item>
        <el-form-item :label="t('roles.roleName')"><el-input v-model="form.name" /></el-form-item>
        <el-form-item :label="t('roles.remark')">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item :label="t('common.enabled')"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="saveRole">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="permissionVisible"
      :title="`${t('roles.featurePermsTitle')}${activeRole?.name || ''}`"
      width="640px"
      destroy-on-close
    >
      <div v-if="catalog.length" class="perm-toolbar">
        <span class="perm-summary">{{ t('roles.permSelected', { n: selectedPermissions.length, total: catalog.length }) }}</span>
        <div class="perm-actions">
          <el-button link type="primary" @click="selectAllPermissions">{{ t('roles.selectAll') }}</el-button>
          <el-button link @click="clearAllPermissions">{{ t('roles.clearAll') }}</el-button>
        </div>
      </div>
      <div v-if="catalog.length" class="permission-list">
        <div v-for="group in permissionGroups" :key="group.key" class="perm-group">
          <div class="perm-group-head">
            <el-checkbox
              :model-value="isGroupChecked(group.codes)"
              :indeterminate="isGroupIndeterminate(group.codes)"
              @change="(checked: boolean | string | number) => toggleGroup(group.codes, Boolean(checked))"
            >
              {{ groupLabel(group.key) }}
            </el-checkbox>
          </div>
          <div class="perm-group-body">
            <el-checkbox
              v-for="permission in group.items"
              :key="permission.id"
              :model-value="selectedPermissions.includes(permission.code)"
              @change="(checked: boolean | string | number) => togglePermission(permission.code, Boolean(checked))"
            >
              {{ permission.name }}
              <span class="perm-code">{{ permission.code }}</span>
            </el-checkbox>
          </div>
        </div>
      </div>
      <el-empty v-else :description="t('roles.noFeaturePerms')" />
      <template #footer>
        <el-button @click="permissionVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="savePermissions">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="memberVisible"
      :title="`${t('roles.membersTitle')}${activeRole?.name || ''}`"
      width="700px"
      destroy-on-close
      @closed="memberKeyword = ''"
    >
      <el-input
        v-model="memberKeyword"
        clearable
        class="member-search"
        :placeholder="t('roles.memberSearchPlaceholder')"
      />
      <el-table :data="filteredUsers" :empty-text="t('roles.noUsers')">
        <el-table-column prop="username" :label="t('roles.username')" />
        <el-table-column prop="displayName" :label="t('roles.displayName')" />
        <el-table-column :label="t('roles.currentRoles')" min-width="180">
          <template #default="{ row }">{{ row.roles.join('、') }}</template>
        </el-table-column>
        <el-table-column :label="t('roles.members')" width="100">
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
      <template #footer><el-button @click="memberVisible = false">{{ t('common.close') }}</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.muted { color: var(--el-text-color-secondary); }
.danger { color: var(--el-color-danger); }
.perm-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.perm-summary { color: var(--el-text-color-secondary); font-size: 13px; }
.perm-actions { display: flex; gap: 4px; }
.permission-list { display: flex; flex-direction: column; gap: 14px; max-height: 420px; overflow: auto; }
.perm-group {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 10px 12px;
}
.perm-group-head { margin-bottom: 8px; font-weight: 600; }
.perm-group-body { display: flex; flex-direction: column; gap: 8px; padding-left: 22px; }
.perm-code {
  margin-left: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.member-search { margin-bottom: 12px; }
</style>

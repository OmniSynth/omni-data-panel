<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { resourcePermissionApi, roleApi } from '@/api'
import { displayLabel } from '@/display'
import type { Id, Role, RoleResourceGrant } from '@/types'

const props = defineProps<{
  resourceType: 'DATA_SOURCE' | 'DASHBOARD' | 'COLLECTION' | 'CHART' | 'DATASET' | 'METRIC'
  resourceId?: Id
  allowedPermissions: Array<'READ' | 'WRITE'>
  title?: string
  hint?: string
}>()
const visible = defineModel<boolean>({ required: true })
const { t } = useI18n()
const roles = ref<Role[]>([])
const grants = ref<RoleResourceGrant[]>([])
const selections = ref<Record<string, 'READ' | 'WRITE' | ''>>({})
const loading = ref(false)
const saving = ref(false)
const keyword = ref('')

const assignableRoles = computed(() => roles.value.filter((role) =>
  role.enabled && role.code !== 'ADMIN'))

const filteredRoles = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return assignableRoles.value
  return assignableRoles.value.filter((role) =>
    role.name.toLowerCase().includes(q) || role.code.toLowerCase().includes(q))
})

/** 加载可授权角色与当前资源授权，并回填选择状态 */
async function load() {
  if (!props.resourceId) return
  loading.value = true
  try {
    [roles.value, grants.value] = await Promise.all([
      roleApi.assignable(),
      resourcePermissionApi.list(props.resourceType, props.resourceId),
    ])
    selections.value = Object.fromEntries(assignableRoles.value.map((role) => [
      String(role.id),
      grants.value.find((grant) => String(grant.roleId) === String(role.id))?.permission || '',
    ]))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('roleGrant.loadFailed'))
  } finally {
    loading.value = false
  }
}

/** 批量保存当前表格中的授权变更（含撤销） */
async function saveAll() {
  if (!props.resourceId) return
  saving.value = true
  try {
    const tasks: Array<Promise<void>> = []
    for (const role of assignableRoles.value) {
      const key = String(role.id)
      const next = selections.value[key] || ''
      const prev = grants.value.find((grant) => String(grant.roleId) === key)?.permission || ''
      if (next === prev) continue
      if (!next) {
        tasks.push(resourcePermissionApi.revoke(props.resourceType, props.resourceId, role.id))
      } else {
        tasks.push(resourcePermissionApi.grant(props.resourceType, props.resourceId, role.id, next))
      }
    }
    if (!tasks.length) {
      ElMessage.info(t('roleGrant.noChanges'))
      return
    }
    await Promise.all(tasks)
    ElMessage.success(t('roleGrant.batchSaved'))
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('roleGrant.saveFailed'))
  } finally {
    saving.value = false
  }
}

/** 撤销角色对该资源的授权 */
async function revoke(role: Role) {
  if (!props.resourceId) return
  selections.value[String(role.id)] = ''
}

watch(visible, (opened) => {
  if (opened) {
    keyword.value = ''
    load()
  } else {
    roles.value = []
    grants.value = []
    selections.value = {}
    keyword.value = ''
  }
})
</script>

<template>
  <el-dialog v-model="visible" :title="title || t('roleGrant.title')" width="720px" destroy-on-close>
    <p v-if="hint" class="hint">{{ hint }}</p>
    <div class="toolbar">
      <el-input
        v-model="keyword"
        clearable
        :placeholder="t('roleGrant.searchPlaceholder')"
        class="search"
      />
      <el-button type="primary" :loading="saving" @click="saveAll">{{ t('roleGrant.saveAll') }}</el-button>
    </div>
    <el-table v-loading="loading" :data="filteredRoles" :empty-text="t('roleGrant.empty')">
      <el-table-column prop="name" :label="t('roleGrant.role')" min-width="140" />
      <el-table-column prop="code" :label="t('roleGrant.code')" min-width="140" />
      <el-table-column :label="t('roleGrant.permission')" width="180">
        <template #default="{ row }">
          <el-select v-model="selections[String(row.id)]" clearable :placeholder="t('roleGrant.none')">
            <el-option
              v-for="permission in allowedPermissions"
              :key="permission"
              :label="displayLabel(permission)"
              :value="permission"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="100">
        <template #default="{ row }">
          <el-button
            v-if="selections[String(row.id)]"
            link
            type="danger"
            :disabled="saving"
            @click="revoke(row)"
          >{{ t('roleGrant.revoke') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="visible = false">{{ t('common.close') }}</el-button>
      <el-button type="primary" :loading="saving" @click="saveAll">{{ t('roleGrant.saveAll') }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.hint {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  align-items: center;
}
.search { flex: 1; }
</style>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { resourcePermissionApi, roleApi } from '@/api'
import type { Id, Role, RoleResourceGrant } from '@/types'

const props = defineProps<{
  resourceType: 'DATA_SOURCE' | 'DASHBOARD'
  resourceId?: Id
  allowedPermissions: Array<'READ' | 'WRITE'>
  title?: string
}>()
const visible = defineModel<boolean>({ required: true })
const roles = ref<Role[]>([])
const grants = ref<RoleResourceGrant[]>([])
const selections = ref<Record<string, 'READ' | 'WRITE' | ''>>({})
const loading = ref(false)
const savingRoleId = ref('')
const assignableRoles = computed(() => roles.value.filter((role) =>
  role.enabled && role.code !== 'ADMIN'))

async function load() {
  if (!props.resourceId) return
  loading.value = true
  try {
    [roles.value, grants.value] = await Promise.all([
      roleApi.list(),
      resourcePermissionApi.list(props.resourceType, props.resourceId),
    ])
    selections.value = Object.fromEntries(assignableRoles.value.map((role) => [
      String(role.id),
      grants.value.find((grant) => String(grant.roleId) === String(role.id))?.permission || '',
    ]))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '角色授权加载失败')
  } finally {
    loading.value = false
  }
}

async function save(role: Role) {
  if (!props.resourceId) return
  const permission = selections.value[String(role.id)]
  if (!permission) return ElMessage.warning('请选择权限级别')
  savingRoleId.value = String(role.id)
  try {
    await resourcePermissionApi.grant(props.resourceType, props.resourceId, role.id, permission)
    ElMessage.success('角色授权已保存')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '角色授权保存失败')
  } finally {
    savingRoleId.value = ''
  }
}

async function revoke(role: Role) {
  if (!props.resourceId) return
  savingRoleId.value = String(role.id)
  try {
    await resourcePermissionApi.revoke(props.resourceType, props.resourceId, role.id)
    ElMessage.success('角色授权已撤销')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '角色授权撤销失败')
  } finally {
    savingRoleId.value = ''
  }
}

watch(visible, (opened) => {
  if (opened) load()
  else {
    roles.value = []
    grants.value = []
    selections.value = {}
  }
})
</script>

<template>
  <el-dialog v-model="visible" :title="title || '角色授权'" width="680px" destroy-on-close>
    <el-table v-loading="loading" :data="assignableRoles" empty-text="暂无可授权角色">
      <el-table-column prop="name" label="角色" min-width="140" />
      <el-table-column prop="code" label="编码" min-width="140" />
      <el-table-column label="权限" width="180">
        <template #default="{ row }">
          <el-select v-model="selections[String(row.id)]" clearable placeholder="未授权">
            <el-option
              v-for="permission in allowedPermissions"
              :key="permission"
              :label="permission === 'READ' ? '读取' : '写入'"
              :value="permission"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button link type="primary" :loading="savingRoleId === String(row.id)" @click="save(row)">保存</el-button>
          <el-button
            v-if="grants.some((grant) => String(grant.roleId) === String(row.id))"
            link
            type="danger"
            :disabled="!!savingRoleId"
            @click="revoke(row)"
          >撤销</el-button>
        </template>
      </el-table-column>
    </el-table>
    <template #footer><el-button @click="visible = false">关闭</el-button></template>
  </el-dialog>
</template>

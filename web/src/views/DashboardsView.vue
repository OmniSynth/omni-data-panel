<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dashboardApi } from '@/api'
import { displayLabel } from '@/display'
import { useUserStore } from '@/stores/user'
import type { Dashboard, Id } from '@/types'
import RoleResourcePermissionPanel from '@/components/RoleResourcePermissionPanel.vue'

const userStore = useUserStore()
const rows = ref<Dashboard[]>([])
const cardCounts = ref<Record<string, number>>({})
const loading = ref(false)
const permissionVisible = ref(false)
const permissionDashboard = ref<Dashboard>()
async function load() {
  loading.value = true
  try {
    rows.value = await dashboardApi.list()
    const entries = await Promise.all(rows.value.map(async (dashboard) =>
      [String(dashboard.id), (await dashboardApi.cards(dashboard.id)).length] as const))
    cardCounts.value = Object.fromEntries(entries)
  }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '仪表盘加载失败') }
  finally { loading.value = false }
}
async function create() {
  try {
    const { value } = await ElMessageBox.prompt('请输入仪表盘名称', '新建仪表盘', { inputPattern: /\S+/, inputErrorMessage: '名称不能为空' })
    const dashboard = await dashboardApi.create({ name: value, configJson: '{}' })
    location.href = `/dashboards/${dashboard.id}/edit`
  } catch (error) { if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '创建失败') }
}
async function remove(id: Id) {
  try { await ElMessageBox.confirm('确认删除该仪表盘？', '删除确认'); await dashboardApi.remove(id); await load() }
  catch (error) { if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除失败') }
}
function canEdit(dashboard: Dashboard) {
  return ['ADMIN', 'OWNER', 'WRITE'].includes(dashboard.accessLevel)
}
function canDelete(dashboard: Dashboard) {
  return ['ADMIN', 'OWNER', 'WRITE'].includes(dashboard.accessLevel)
}
function authorize(dashboard: Dashboard) {
  permissionDashboard.value = dashboard
  permissionVisible.value = true
}
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">仪表盘</h1>
      <el-button v-if="userStore.hasPermission('dashboard:create')" type="primary" @click="create">新建仪表盘</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" empty-text="暂无可访问的仪表盘">
      <el-table-column prop="name" label="名称" />
      <el-table-column label="图表数"><template #default="{ row }">{{ cardCounts[String(row.id)] || 0 }}</template></el-table-column>
      <el-table-column label="访问级别" width="110">
        <template #default="{ row }">{{ displayLabel(row.accessLevel) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="260">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/dashboards/${row.id}/view`)">查看</el-button>
          <el-button v-if="canEdit(row)" link @click="$router.push(`/dashboards/${row.id}/edit`)">编辑</el-button>
          <el-button v-if="userStore.isAdmin" link type="primary" @click="authorize(row)">角色共享</el-button>
          <el-button v-if="canDelete(row)" link type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <RoleResourcePermissionPanel
      v-model="permissionVisible"
      resource-type="DASHBOARD"
      :resource-id="permissionDashboard?.id"
      :allowed-permissions="['READ', 'WRITE']"
      :title="`仪表盘角色共享：${permissionDashboard?.name || ''}`"
    />
  </div>
</template>

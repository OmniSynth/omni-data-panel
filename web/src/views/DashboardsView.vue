<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmBox, promptBox } from '@/i18n/dialog'
import { useI18n } from 'vue-i18n'
import { dashboardApi } from '@/api'
import { displayLabel } from '@/display'
import { useUserStore } from '@/stores/user'
import type { Dashboard, Id } from '@/types'
import RoleResourcePermissionPanel from '@/components/RoleResourcePermissionPanel.vue'

const { t } = useI18n()
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
  catch (error) { ElMessage.error(error instanceof Error ? error.message : t('dashboard.loadFailed')) }
  finally { loading.value = false }
}

async function create() {
  try {
    const { value } = await promptBox(t('dashboard.namePrompt'), t('dashboard.createTitle'), {
      inputPattern: /\S+/,
      inputErrorMessage: t('common.nameRequired'),
    })
    const dashboard = await dashboardApi.create({ name: value, configJson: '{}' })
    location.href = `/dashboards/${dashboard.id}/edit`
  } catch (error) { if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('common.createFailed')) }
}

async function remove(id: Id) {
  try {
    await confirmBox(t('dashboard.deleteConfirm'), t('common.deleteConfirmTitle'))
    await dashboardApi.remove(id)
    await load()
  }
  catch (error) { if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('common.deleteFailed')) }
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
      <h1 class="page-title">{{ t('dashboard.title') }}</h1>
      <el-button v-if="userStore.hasPermission('dashboard:create')" type="primary" @click="create">{{ t('dashboard.create') }}</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" :empty-text="t('dashboard.empty')">
      <el-table-column prop="name" :label="t('common.name')" />
      <el-table-column :label="t('dashboard.chartCount')"><template #default="{ row }">{{ cardCounts[String(row.id)] || 0 }}</template></el-table-column>
      <el-table-column :label="t('dashboard.accessLevel')" width="110">
        <template #default="{ row }">{{ displayLabel(row.accessLevel) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" min-width="260">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/dashboards/${row.id}/view`)">{{ t('common.view') }}</el-button>
          <el-button v-if="canEdit(row)" link @click="$router.push(`/dashboards/${row.id}/edit`)">{{ t('common.edit') }}</el-button>
          <el-button v-if="userStore.isAdmin" link type="primary" @click="authorize(row)">{{ t('dashboard.roleShare') }}</el-button>
          <el-button v-if="canDelete(row)" link type="danger" @click="remove(row.id)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <RoleResourcePermissionPanel
      v-model="permissionVisible"
      resource-type="DASHBOARD"
      :resource-id="permissionDashboard?.id"
      :allowed-permissions="['READ', 'WRITE']"
      :title="`${t('dashboard.roleShareTitle')}${permissionDashboard?.name || ''}`"
    />
  </div>
</template>

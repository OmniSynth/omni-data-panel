<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmBox } from '@/i18n/dialog'
import { useI18n } from 'vue-i18n'
import { trashApi } from '@/api'
import { formatDateTime } from '@/display'
import { resourceTypeLabel } from '@/nav'
import type { TrashItem } from '@/types'

const { t } = useI18n()
const loading = ref(false)
const rows = ref<TrashItem[]>([])

async function load() {
  loading.value = true
  try {
    rows.value = await trashApi.list()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('trash.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function restore(row: TrashItem) {
  try {
    await trashApi.restore({ resourceType: row.resourceType, resourceId: row.resourceId })
    ElMessage.success(t('trash.restored'))
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('trash.restoreFailed'))
  }
}

async function purge(row: TrashItem) {
  try {
    await confirmBox(t('trash.purgeConfirm'), t('trash.purgeTitle'), { type: 'warning' })
    await trashApi.purge({ resourceType: row.resourceType, resourceId: row.resourceId })
    ElMessage.success(t('trash.purged'))
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('common.deleteFailed'))
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header"><h1 class="page-title">{{ t('trash.title') }}</h1></div>
    <el-table v-loading="loading" :data="rows" :empty-text="t('trash.empty')">
      <el-table-column prop="name" :label="t('common.name')" />
      <el-table-column :label="t('common.type')" width="120">
        <template #default="{ row }">{{ resourceTypeLabel(row.resourceType) }}</template>
      </el-table-column>
      <el-table-column prop="description" :label="t('common.description')" show-overflow-tooltip />
      <el-table-column :label="t('trash.deletedAt')" width="180">
        <template #default="{ row }">{{ formatDateTime(row.deletedAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="restore(row)">{{ t('trash.restore') }}</el-button>
          <el-button link type="danger" @click="purge(row)">{{ t('trash.purgeTitle') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

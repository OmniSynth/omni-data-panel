<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { trashApi } from '@/api'
import { formatDateTime } from '@/display'
import { resourceTypeLabel } from '@/nav'
import type { TrashItem } from '@/types'

const loading = ref(false)
const rows = ref<TrashItem[]>([])

async function load() {
  loading.value = true
  try {
    rows.value = await trashApi.list()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '废纸篓加载失败')
  } finally {
    loading.value = false
  }
}

async function restore(row: TrashItem) {
  try {
    await trashApi.restore({ resourceType: row.resourceType, resourceId: row.resourceId })
    ElMessage.success('已恢复')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '恢复失败')
  }
}

async function purge(row: TrashItem) {
  try {
    await ElMessageBox.confirm('永久删除后不可恢复，确认继续？', '永久删除', { type: 'warning' })
    await trashApi.purge({ resourceType: row.resourceType, resourceId: row.resourceId })
    ElMessage.success('已永久删除')
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header"><h1 class="page-title">废纸篓</h1></div>
    <el-table v-loading="loading" :data="rows" empty-text="废纸篓是空的">
      <el-table-column prop="name" label="名称" />
      <el-table-column label="类型" width="120">
        <template #default="{ row }">{{ resourceTypeLabel(row.resourceType) }}</template>
      </el-table-column>
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column label="删除时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.deletedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="restore(row)">恢复</el-button>
          <el-button link type="danger" @click="purge(row)">永久删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

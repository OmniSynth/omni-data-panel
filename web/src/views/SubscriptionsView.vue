<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmBox } from '@/i18n/dialog'
import { useI18n } from 'vue-i18n'
import { dashboardApi, subscriptionApi } from '@/api'
import type { Dashboard, Id, Subscription } from '@/types'

const { t } = useI18n()
const rows = ref<Subscription[]>([])
const dashboards = ref<Dashboard[]>([])
const visible = ref(false)
const editingId = ref<Id>()
const form = reactive<Subscription>({ id: '', name: '', dashboardId: '', cronExpression: '0 0 9 * * ?', recipients: '', enabled: true })

async function load() {
  try {
    const [page, dashboardPage] = await Promise.all([subscriptionApi.list(), dashboardApi.list()])
    rows.value = page
    dashboards.value = dashboardPage
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : t('subscriptions.loadFailed')) }
}

function open(row?: Subscription) {
  Object.assign(form, row || { id: '', name: '', dashboardId: '', cronExpression: '0 0 9 * * ?', recipients: '', enabled: true })
  editingId.value = row?.id
  visible.value = true
}

async function save() {
  if (!form.name || !form.dashboardId || !form.cronExpression || !form.recipients) return ElMessage.warning(t('subscriptions.needComplete'))
  const data = { ...form }
  delete (data as Partial<Subscription>).id
  try {
    if (editingId.value !== undefined) await subscriptionApi.update(editingId.value, data)
    else await subscriptionApi.create(data)
    visible.value = false
    await load()
    ElMessage.success(t('subscriptions.saved'))
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : t('common.saveFailed')) }
}

async function remove(id: Id) {
  try {
    await confirmBox(t('subscriptions.deleteConfirm'), t('common.deleteConfirmTitle'))
    await subscriptionApi.remove(id)
    await load()
  }
  catch (error) { if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('common.deleteFailed')) }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">{{ t('subscriptions.title') }}</h1>
      <el-button type="primary" @click="open()">{{ t('subscriptions.create') }}</el-button>
    </div>
    <el-table :data="rows">
      <el-table-column prop="name" :label="t('common.name')" />
      <el-table-column prop="dashboardId" :label="t('subscriptions.dashboardId')" />
      <el-table-column prop="cronExpression" :label="t('subscriptions.cron')" />
      <el-table-column prop="recipients" :label="t('subscriptions.recipients')" />
      <el-table-column :label="t('common.status')">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? t('common.enabled') : t('subscriptions.stopped') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')">
        <template #default="{ row }">
          <el-button link @click="open(row)">{{ t('common.edit') }}</el-button>
          <el-button link type="danger" @click="remove(row.id)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="visible" :title="editingId === undefined ? t('subscriptions.createTitle') : t('subscriptions.editTitle')" width="600px">
      <el-form label-width="100px">
        <el-form-item :label="t('common.name')"><el-input v-model="form.name" /></el-form-item>
        <el-form-item :label="t('subscriptions.dashboard')">
          <el-select v-model="form.dashboardId" class="full-width">
            <el-option v-for="item in dashboards" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('subscriptions.cron')"><el-input v-model="form.cronExpression" /></el-form-item>
        <el-form-item :label="t('subscriptions.recipients')">
          <el-input v-model="form.recipients" :placeholder="t('subscriptions.recipientsHint')" />
        </el-form-item>
        <el-form-item :label="t('common.enabled')"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible=false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="save">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

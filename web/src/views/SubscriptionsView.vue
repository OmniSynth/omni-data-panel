<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dashboardApi, subscriptionApi } from '@/api'
import type { Dashboard, Id, Subscription } from '@/types'

const rows = ref<Subscription[]>([])
const dashboards = ref<Dashboard[]>([])
const visible = ref(false)
const editingId = ref<Id>()
const form = reactive<Subscription>({ id: '', name: '', dashboardId: '', cronExpression: '0 0 9 * * ?', recipients: '', enabled: true })
async function load() {
  try {
    const [page, dashboardPage] = await Promise.all([subscriptionApi.list(), dashboardApi.list()])
    rows.value = page; dashboards.value = dashboardPage
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '订阅加载失败') }
}
function open(row?: Subscription) {
  Object.assign(form, row || { id: '', name: '', dashboardId: '', cronExpression: '0 0 9 * * ?', recipients: '', enabled: true })
  editingId.value = row?.id; visible.value = true
}
async function save() {
  if (!form.name || !form.dashboardId || !form.cronExpression || !form.recipients) return ElMessage.warning('请填写完整订阅信息')
  const data = { ...form }; delete (data as Partial<Subscription>).id
  try {
    if (editingId.value !== undefined) await subscriptionApi.update(editingId.value, data)
    else await subscriptionApi.create(data)
    visible.value = false; await load(); ElMessage.success('订阅已保存')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') }
}
async function remove(id: Id) {
  try { await ElMessageBox.confirm('确认删除该订阅？', '删除确认'); await subscriptionApi.remove(id); await load() }
  catch (error) { if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除失败') }
}
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header"><h1 class="page-title">订阅</h1><el-button type="primary" @click="open()">新增订阅</el-button></div>
    <el-table :data="rows"><el-table-column prop="name" label="名称" /><el-table-column prop="dashboardId" label="仪表盘 ID" /><el-table-column prop="cronExpression" label="Cron 表达式" /><el-table-column prop="recipients" label="接收人" /><el-table-column label="状态"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column label="操作"><template #default="{ row }"><el-button link @click="open(row)">编辑</el-button><el-button link type="danger" @click="remove(row.id)">删除</el-button></template></el-table-column></el-table>
    <el-dialog v-model="visible" :title="editingId === undefined ? '新增订阅' : '编辑订阅'" width="600px"><el-form label-width="100px"><el-form-item label="名称"><el-input v-model="form.name" /></el-form-item><el-form-item label="仪表盘"><el-select v-model="form.dashboardId" class="full-width"><el-option v-for="item in dashboards" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item label="Cron 表达式"><el-input v-model="form.cronExpression" /></el-form-item><el-form-item label="接收人"><el-input v-model="form.recipients" placeholder="多个邮箱用逗号分隔" /></el-form-item><el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item></el-form><template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template></el-dialog>
  </div>
</template>

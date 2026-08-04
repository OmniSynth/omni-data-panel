<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps<{
  cleaning?: boolean
}>()

const emit = defineEmits<{
  cleanup: [payload: { mode: 'ALL' | 'BEFORE_DAYS' | 'BEFORE_DATE'; days?: number; before?: string }]
}>()

const dateVisible = ref(false)
const beforeDate = ref('')

async function clearAll() {
  try {
    await ElMessageBox.confirm('将删除全部日志且不可恢复，确认继续？', '清空全部', { type: 'warning' })
    emit('cleanup', { mode: 'ALL' })
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '操作取消')
  }
}

async function clearDays(days: number, label: string) {
  try {
    await ElMessageBox.confirm(`将删除 ${label} 的日志且不可恢复，确认继续？`, '清理确认', { type: 'warning' })
    emit('cleanup', { mode: 'BEFORE_DAYS', days })
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '操作取消')
  }
}

function openDateDialog() {
  beforeDate.value = ''
  dateVisible.value = true
}

async function confirmDate() {
  if (!beforeDate.value) return ElMessage.warning('请选择日期')
  const before = `${beforeDate.value}T00:00:00`
  try {
    await ElMessageBox.confirm(`将删除 ${beforeDate.value} 之前的日志且不可恢复，确认继续？`, '清理确认', { type: 'warning' })
    dateVisible.value = false
    emit('cleanup', { mode: 'BEFORE_DATE', before })
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '操作取消')
  }
}
</script>

<template>
  <div class="cleanup-actions">
    <el-button :loading="cleaning" type="danger" plain @click="clearAll">清空全部</el-button>
    <el-button :loading="cleaning" @click="clearDays(3, '3 天前')">清除 3 天前</el-button>
    <el-button :loading="cleaning" @click="clearDays(30, '一个月前')">清除一个月前</el-button>
    <el-button :loading="cleaning" @click="openDateDialog">指定日期之前</el-button>

    <el-dialog v-model="dateVisible" title="清除指定日期之前的日志" width="420px">
      <el-form label-width="90px">
        <el-form-item label="截止日期">
          <el-date-picker
            v-model="beforeDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            class="full-width"
          />
        </el-form-item>
        <p class="hint">将删除该日期 00:00:00 之前的全部日志。</p>
      </el-form>
      <template #footer>
        <el-button @click="dateVisible = false">取消</el-button>
        <el-button type="primary" :loading="cleaning" @click="confirmDate">确认清理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.cleanup-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.hint {
  margin: 0;
  font-size: 12px;
  color: #9ca3af;
}
.full-width { width: 100%; }
</style>

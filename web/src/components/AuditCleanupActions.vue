<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmBox } from '@/i18n/dialog'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  cleaning?: boolean
}>()

const emit = defineEmits<{
  cleanup: [payload: { mode: 'ALL' | 'BEFORE_DAYS' | 'BEFORE_DATE'; days?: number; before?: string }]
}>()

const { t } = useI18n()
const dateVisible = ref(false)
const beforeDate = ref('')

/** 确认后清空全部审计日志 */
async function clearAll() {
  try {
    await confirmBox(t('auditCleanup.clearAllConfirm'), t('auditCleanup.clearAll'), { type: 'warning' })
    emit('cleanup', { mode: 'ALL' })
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('auditCleanup.cancelled'))
  }
}

/**
 * 确认后按相对天数清理。
 * @param days 保留最近 N 天，删除更早记录
 * @param labelKey 确认文案用的 i18n 键
 */
async function clearDays(days: number, labelKey: 'auditCleanup.clear3d' | 'auditCleanup.clear1m') {
  const label = t(labelKey)
  try {
    await confirmBox(t('auditCleanup.clearLabelConfirm', { label }), t('auditCleanup.confirmTitle'), { type: 'warning' })
    emit('cleanup', { mode: 'BEFORE_DAYS', days })
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('auditCleanup.cancelled'))
  }
}

/** 打开「指定日期之前」对话框 */
function openDateDialog() {
  beforeDate.value = ''
  dateVisible.value = true
}

/** 确认按截止日期清理（删除该日 00:00:00 之前） */
async function confirmDate() {
  if (!beforeDate.value) return ElMessage.warning(t('auditCleanup.needDate'))
  try {
    await confirmBox(t('auditCleanup.clearBeforeConfirm', { date: beforeDate.value }), t('auditCleanup.confirmTitle'), { type: 'warning' })
    dateVisible.value = false
    emit('cleanup', { mode: 'BEFORE_DATE', before: `${beforeDate.value}T00:00:00` })
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('auditCleanup.cancelled'))
  }
}
</script>

<template>
  <div class="cleanup-actions">
    <el-button :loading="cleaning" type="danger" plain @click="clearAll">{{ t('auditCleanup.clearAll') }}</el-button>
    <el-button :loading="cleaning" @click="clearDays(3, 'auditCleanup.clear3d')">{{ t('auditCleanup.clear3d') }}</el-button>
    <el-button :loading="cleaning" @click="clearDays(30, 'auditCleanup.clear1m')">{{ t('auditCleanup.clear1m') }}</el-button>
    <el-button :loading="cleaning" @click="openDateDialog">{{ t('auditCleanup.clearBefore') }}</el-button>

    <el-dialog v-model="dateVisible" :title="t('auditCleanup.dialogTitle')" width="420px">
      <el-form label-width="90px">
        <el-form-item :label="t('auditCleanup.beforeDate')">
          <el-date-picker
            v-model="beforeDate"
            type="date"
            value-format="YYYY-MM-DD"
            :placeholder="t('auditCleanup.selectDate')"
            class="full-width"
          />
        </el-form-item>
        <p class="hint">{{ t('auditCleanup.dialogHint') }}</p>
      </el-form>
      <template #footer>
        <el-button @click="dateVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="cleaning" @click="confirmDate">{{ t('auditCleanup.confirm') }}</el-button>
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

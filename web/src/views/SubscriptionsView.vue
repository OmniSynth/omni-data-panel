<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { confirmBox } from '@/i18n/dialog'
import { useI18n } from 'vue-i18n'
import { dashboardApi, subscriptionApi, userApi } from '@/api'
import {
  CRON_PRESET_CUSTOM,
  CRON_PRESETS,
  cronFromPreset,
  resolveCronPreset,
} from '@/dashboard/cronPresets'
import { requiredRule, validateForm } from '@/form/rules'
import type { Dashboard, Id, Subscription, UserDirectoryItem } from '@/types'

const { t } = useI18n()
const rows = ref<Subscription[]>([])
const dashboards = ref<Dashboard[]>([])
const users = ref<UserDirectoryItem[]>([])
const visible = ref(false)
const editingId = ref<Id>()
const runningId = ref<Id>()
const togglingId = ref<Id>()
const cronPreset = ref('daily9')
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  dashboardId: undefined as Id | undefined,
  cronExpression: '0 0 9 * * ?',
  recipientUserIds: [] as Id[],
  enabled: true,
})

const isCustomCron = computed(() => cronPreset.value === CRON_PRESET_CUSTOM)

const cronPresetOptions = computed(() =>
  CRON_PRESETS.map((item) => ({
    value: item.value,
    label: t(item.labelKey),
    cron: item.cron,
  })))

watch(cronPreset, (preset) => {
  if (preset === CRON_PRESET_CUSTOM) return
  const cron = cronFromPreset(preset)
  if (cron) form.cronExpression = cron
})

function cronLabel(cron: string | undefined) {
  const preset = resolveCronPreset(cron)
  if (preset === CRON_PRESET_CUSTOM) return cron || '—'
  const hit = CRON_PRESETS.find((item) => item.value === preset)
  return hit ? t(hit.labelKey) : (cron || '—')
}

const mailableUsers = computed(() =>
  users.value.filter((user) => !!user.email && user.email.trim()))

const formRules = computed<FormRules>(() => ({
  name: [requiredRule(t('common.pleaseEnter', { field: t('common.name') }))],
  dashboardId: [requiredRule(t('common.pleaseSelect', { field: t('subscriptions.dashboard') }), 'change')],
  cronExpression: [requiredRule(t('common.pleaseEnter', { field: t('subscriptions.cron') }))],
  recipientUserIds: [{
    type: 'array',
    required: true,
    min: 1,
    message: t('common.pleaseSelect', { field: t('subscriptions.recipients') }),
    trigger: 'change',
  }],
}))

function dashboardName(id: Id) {
  return dashboards.value.find((item) => String(item.id) === String(id))?.name || String(id)
}

function userOptionLabel(user: UserDirectoryItem) {
  const name = user.displayName || user.username
  return user.email ? `${name}（${user.email}）` : name
}

function resetForm() {
  Object.assign(form, {
    name: '',
    dashboardId: undefined,
    cronExpression: '0 0 9 * * ?',
    recipientUserIds: [],
    enabled: true,
  })
  cronPreset.value = 'daily9'
}

async function load() {
  try {
    const [page, dashboardPage, directory] = await Promise.all([
      subscriptionApi.list(),
      dashboardApi.list(),
      userApi.directory(),
    ])
    rows.value = page
    dashboards.value = dashboardPage
    users.value = directory
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('subscriptions.loadFailed'))
  }
}

function open(row?: Subscription) {
  if (row) {
    Object.assign(form, {
      name: row.name,
      dashboardId: row.dashboardId,
      cronExpression: row.cronExpression,
      recipientUserIds: [...(row.recipientUserIds || [])],
      enabled: row.enabled,
    })
    cronPreset.value = resolveCronPreset(row.cronExpression)
    editingId.value = row.id
  } else {
    resetForm()
    editingId.value = undefined
  }
  visible.value = true
}

async function save() {
  if (!(await validateForm(formRef.value))) return
  const data = {
    name: form.name.trim(),
    dashboardId: form.dashboardId,
    cronExpression: form.cronExpression.trim(),
    recipientUserIds: form.recipientUserIds,
    enabled: form.enabled,
  }
  try {
    if (editingId.value !== undefined) await subscriptionApi.update(editingId.value, data)
    else await subscriptionApi.create(data)
    visible.value = false
    await load()
    ElMessage.success(t('subscriptions.saved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('common.saveFailed'))
  }
}

async function toggleEnabled(row: Subscription, enabled: boolean) {
  if (String(togglingId.value) === String(row.id)) return
  if (!row.recipientUserIds?.length) {
    ElMessage.warning(t('subscriptions.toggleNeedRecipients'))
    return
  }
  const previous = row.enabled
  row.enabled = enabled
  togglingId.value = row.id
  try {
    const updated = await subscriptionApi.update(row.id, {
      name: row.name,
      dashboardId: row.dashboardId,
      cronExpression: row.cronExpression,
      recipientUserIds: row.recipientUserIds,
      enabled,
    })
    Object.assign(row, updated)
    ElMessage.success(enabled ? t('subscriptions.enabledSuccess') : t('subscriptions.disabledSuccess'))
  } catch (error) {
    row.enabled = previous
    ElMessage.error(error instanceof Error ? error.message : t('common.saveFailed'))
  } finally {
    togglingId.value = undefined
  }
}

async function remove(id: Id) {
  try {
    await confirmBox(t('subscriptions.deleteConfirm'), t('common.deleteConfirmTitle'))
    await subscriptionApi.remove(id)
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('common.deleteFailed'))
  }
}

async function runNow(row: Subscription) {
  try {
    await confirmBox(t('subscriptions.runConfirm'), t('subscriptions.runTitle'))
    runningId.value = row.id
    await subscriptionApi.runNow(row.id)
    ElMessage.success(t('subscriptions.runSuccess'))
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('subscriptions.runFailed'))
  } finally {
    runningId.value = undefined
  }
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
      <el-table-column prop="name" :label="t('common.name')" min-width="140" />
      <el-table-column :label="t('subscriptions.dashboard')" min-width="160">
        <template #default="{ row }">{{ dashboardName(row.dashboardId) }}</template>
      </el-table-column>
      <el-table-column :label="t('subscriptions.schedule')" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ cronLabel(row.cronExpression) }}</span>
          <span v-if="resolveCronPreset(row.cronExpression) !== CRON_PRESET_CUSTOM" class="cron-raw">{{ row.cronExpression }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('subscriptions.recipients')" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.recipientsLabel || '—' }}</template>
      </el-table-column>
      <el-table-column :label="t('common.status')" width="90" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled"
            :loading="String(togglingId) === String(row.id)"
            @change="(value: string | number | boolean) => toggleEnabled(row, Boolean(value))"
          />
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="220">
        <template #default="{ row }">
          <el-button link :loading="String(runningId) === String(row.id)" @click="runNow(row)">{{ t('subscriptions.runNow') }}</el-button>
          <el-button link @click="open(row)">{{ t('common.edit') }}</el-button>
          <el-button link type="danger" @click="remove(row.id)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="visible"
      :title="editingId === undefined ? t('subscriptions.createTitle') : t('subscriptions.editTitle')"
      width="600px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item :label="t('common.name')" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="t('subscriptions.dashboard')" prop="dashboardId">
          <el-select v-model="form.dashboardId" class="full-width" filterable>
            <el-option v-for="item in dashboards" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('subscriptions.schedule')" prop="cronExpression">
          <el-select v-model="cronPreset" class="full-width" :placeholder="t('subscriptions.cronPresetHint')">
            <el-option
              v-for="item in cronPresetOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            >
              <div class="cron-option">
                <span>{{ item.label }}</span>
                <small v-if="item.cron">{{ item.cron }}</small>
              </div>
            </el-option>
          </el-select>
          <el-input
            v-if="isCustomCron"
            v-model="form.cronExpression"
            class="cron-custom"
            :placeholder="t('subscriptions.cronCustomHint')"
          />
          <p v-else class="hint">{{ t('subscriptions.cronPreview', { cron: form.cronExpression }) }}</p>
        </el-form-item>
        <el-form-item :label="t('subscriptions.recipients')" prop="recipientUserIds">
          <el-select
            v-model="form.recipientUserIds"
            class="full-width"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            :placeholder="t('subscriptions.recipientsHint')"
          >
            <el-option
              v-for="user in mailableUsers"
              :key="user.id"
              :label="userOptionLabel(user)"
              :value="user.id"
            />
          </el-select>
          <p class="hint">{{ t('subscriptions.recipientsHelp') }}</p>
        </el-form-item>
        <el-form-item :label="t('common.enabled')">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="save">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hint {
  margin: 6px 0 0;
  color: var(--omni-muted);
  font-size: 12px;
  line-height: 1.4;
}
.cron-custom { margin-top: 8px; }
.cron-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.cron-option small {
  color: var(--omni-muted);
  font-size: 12px;
}
.cron-raw {
  display: block;
  margin-top: 2px;
  color: var(--omni-muted);
  font-size: 12px;
}
</style>

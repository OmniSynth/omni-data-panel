<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { confirmBox } from '@/i18n/dialog'
import { useI18n } from 'vue-i18n'
import { dashboardApi, dataSourceApi, scheduleApi, subscriptionApi } from '@/api'
import {
  CRON_PRESET_CUSTOM,
  CRON_PRESETS,
  cronFromPreset,
  resolveCronPreset,
} from '@/dashboard/cronPresets'
import { requiredRule, validateForm } from '@/form/rules'
import type { Dashboard, DataSource, Id, Schedule, ScheduleType, Subscription } from '@/types'

const { t } = useI18n()
const rows = ref<Schedule[]>([])
const dataSources = ref<DataSource[]>([])
const dashboards = ref<Dashboard[]>([])
const subscriptions = ref<Subscription[]>([])
const visible = ref(false)
const editingId = ref<Id>()
const togglingId = ref<Id>()
const cronPreset = ref('daily9')
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  scheduleType: 'METADATA_SYNC' as ScheduleType,
  targetId: undefined as Id | undefined,
  cronExpression: '0 0 9 * * ?',
  enabled: true,
})

const scheduleTypes: ScheduleType[] = ['METADATA_SYNC', 'DASHBOARD_REFRESH', 'SUBSCRIPTION']

const isCustomCron = computed(() => cronPreset.value === CRON_PRESET_CUSTOM)

const cronPresetOptions = computed(() =>
  CRON_PRESETS.map((item) => ({
    value: item.value,
    label: t(item.labelKey),
    cron: item.cron,
  })))

const targetOptions = computed(() => {
  switch (form.scheduleType) {
    case 'METADATA_SYNC':
      return dataSources.value.map((item) => ({ id: item.id, label: item.name }))
    case 'DASHBOARD_REFRESH':
      return dashboards.value.map((item) => ({ id: item.id, label: item.name }))
    case 'SUBSCRIPTION':
      return subscriptions.value.map((item) => ({ id: item.id, label: item.name }))
    default:
      return []
  }
})

const formRules = computed<FormRules>(() => ({
  name: [requiredRule(t('common.pleaseEnter', { field: t('common.name') }))],
  scheduleType: [requiredRule(t('common.pleaseSelect', { field: t('schedules.type') }), 'change')],
  targetId: [requiredRule(t('common.pleaseSelect', { field: t('schedules.target') }), 'change')],
  cronExpression: [requiredRule(t('common.pleaseEnter', { field: t('subscriptions.cron') }))],
}))

watch(cronPreset, (preset) => {
  if (preset === CRON_PRESET_CUSTOM) return
  const cron = cronFromPreset(preset)
  if (cron) form.cronExpression = cron
})

function onTypeChange() {
  form.targetId = undefined
}

function cronLabel(cron: string | undefined) {
  const preset = resolveCronPreset(cron)
  if (preset === CRON_PRESET_CUSTOM) return cron || '—'
  const hit = CRON_PRESETS.find((item) => item.value === preset)
  return hit ? t(hit.labelKey) : (cron || '—')
}

function typeLabel(type: ScheduleType) {
  return t(`schedules.types.${type}`)
}

function targetName(row: Schedule) {
  const id = String(row.targetId)
  switch (row.scheduleType) {
    case 'METADATA_SYNC':
      return dataSources.value.find((item) => String(item.id) === id)?.name || id
    case 'DASHBOARD_REFRESH':
      return dashboards.value.find((item) => String(item.id) === id)?.name || id
    case 'SUBSCRIPTION':
      return subscriptions.value.find((item) => String(item.id) === id)?.name || id
    default:
      return id
  }
}

function resetForm() {
  Object.assign(form, {
    name: '',
    scheduleType: 'METADATA_SYNC' as ScheduleType,
    targetId: undefined,
    cronExpression: '0 0 9 * * ?',
    enabled: true,
  })
  cronPreset.value = 'daily9'
}

async function load() {
  try {
    const [schedules, sources, dashboardPage, subs] = await Promise.all([
      scheduleApi.list(),
      dataSourceApi.list(),
      dashboardApi.list(),
      subscriptionApi.list(),
    ])
    rows.value = schedules
    dataSources.value = sources
    dashboards.value = dashboardPage
    subscriptions.value = subs
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('schedules.loadFailed'))
  }
}

function open(row?: Schedule) {
  if (row) {
    Object.assign(form, {
      name: row.name,
      scheduleType: row.scheduleType,
      targetId: row.targetId,
      cronExpression: row.cronExpression,
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
    scheduleType: form.scheduleType,
    targetId: form.targetId,
    cronExpression: form.cronExpression.trim(),
    enabled: form.enabled,
  }
  try {
    if (editingId.value !== undefined) await scheduleApi.update(editingId.value, data)
    else await scheduleApi.create(data)
    visible.value = false
    await load()
    ElMessage.success(t('schedules.saved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('common.saveFailed'))
  }
}

async function toggleEnabled(row: Schedule, enabled: boolean) {
  if (String(togglingId.value) === String(row.id)) return
  const previous = row.enabled
  row.enabled = enabled
  togglingId.value = row.id
  try {
    const updated = await scheduleApi.update(row.id, {
      name: row.name,
      scheduleType: row.scheduleType,
      targetId: row.targetId,
      cronExpression: row.cronExpression,
      enabled,
    })
    Object.assign(row, updated)
    ElMessage.success(enabled ? t('schedules.enabledSuccess') : t('schedules.disabledSuccess'))
  } catch (error) {
    row.enabled = previous
    ElMessage.error(error instanceof Error ? error.message : t('common.saveFailed'))
  } finally {
    togglingId.value = undefined
  }
}

async function remove(id: Id) {
  try {
    await confirmBox(t('schedules.deleteConfirm'), t('common.deleteConfirmTitle'))
    await scheduleApi.remove(id)
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : t('common.deleteFailed'))
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">{{ t('schedules.title') }}</h1>
      <el-button type="primary" @click="open()">{{ t('schedules.create') }}</el-button>
    </div>
    <p class="page-hint">{{ t('schedules.hint') }}</p>
    <el-table :data="rows">
      <el-table-column prop="name" :label="t('common.name')" min-width="140" />
      <el-table-column :label="t('schedules.type')" min-width="140">
        <template #default="{ row }">{{ typeLabel(row.scheduleType) }}</template>
      </el-table-column>
      <el-table-column :label="t('schedules.target')" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ targetName(row) }}</template>
      </el-table-column>
      <el-table-column :label="t('schedules.cron')" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ cronLabel(row.cronExpression) }}</span>
          <span v-if="resolveCronPreset(row.cronExpression) !== CRON_PRESET_CUSTOM" class="cron-raw">{{ row.cronExpression }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('schedules.lastRunAt')" min-width="160">
        <template #default="{ row }">{{ row.lastRunAt || '—' }}</template>
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
      <el-table-column :label="t('common.actions')" width="140">
        <template #default="{ row }">
          <el-button link @click="open(row)">{{ t('common.edit') }}</el-button>
          <el-button link type="danger" @click="remove(row.id)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="visible"
      :title="editingId === undefined ? t('schedules.createTitle') : t('schedules.editTitle')"
      width="600px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item :label="t('common.name')" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="t('schedules.type')" prop="scheduleType">
          <el-select v-model="form.scheduleType" class="full-width" @change="onTypeChange">
            <el-option
              v-for="type in scheduleTypes"
              :key="type"
              :label="typeLabel(type)"
              :value="type"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('schedules.target')" prop="targetId">
          <el-select
            v-model="form.targetId"
            class="full-width"
            filterable
            :placeholder="t('schedules.targetHint')"
          >
            <el-option
              v-for="item in targetOptions"
              :key="item.id"
              :label="item.label"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('schedules.cron')" prop="cronExpression">
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
.page-hint {
  margin: 0 0 12px;
  color: var(--omni-muted);
  font-size: 13px;
  line-height: 1.5;
}
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

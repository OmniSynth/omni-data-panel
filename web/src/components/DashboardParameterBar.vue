<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { datasetApi } from '@/api'
import type { DashboardParameter, DashboardParameterOption } from '@/types'

const props = defineProps<{
  parameters: DashboardParameter[]
  modelValue: Record<string, unknown>
  readonly?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [Record<string, unknown>]
  apply: []
}>()

const { t } = useI18n()
const parameters = computed(() => props.parameters || [])
const dynamicOptions = reactive<Record<string, DashboardParameterOption[]>>({})
const loadingOptions = reactive<Record<string, boolean>>({})

/** 写入单个参数值 */
function setValue(id: string, value: unknown) {
  emit('update:modelValue', { ...props.modelValue, [id]: value })
}

/** 将存储值规范为日期区间选择器可用的二元组 */
function rangeValue(id: string): [string, string] | undefined {
  const raw = props.modelValue[id]
  if (Array.isArray(raw) && raw.length >= 2) return [String(raw[0] ?? ''), String(raw[1] ?? '')]
  if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
    const map = raw as Record<string, unknown>
    const start = map.start ?? map.from
    const end = map.end ?? map.to
    if (start != null || end != null) return [String(start ?? ''), String(end ?? '')]
  }
  return undefined
}

/** 将区间选择结果写为 `{ start, end }` */
function setRange(id: string, value: [string, string] | null) {
  if (!value) {
    setValue(id, undefined)
    return
  }
  setValue(id, { start: value[0], end: value[1] })
}

/** 解析参数下拉选项：优先动态 DISTINCT，否则静态 options */
function resolvedOptions(parameter: DashboardParameter): DashboardParameterOption[] {
  if (parameter.optionsFrom) {
    return dynamicOptions[parameter.id] || parameter.options || []
  }
  return parameter.options || []
}

/** 按 optionsFrom 拉取模型字段去重取值 */
async function loadDynamicOptions(parameter: DashboardParameter) {
  const from = parameter.optionsFrom
  if (!from?.datasetId || !from.field) return
  loadingOptions[parameter.id] = true
  try {
    const values = await datasetApi.distinct(from.datasetId, from.field, from.limit)
    dynamicOptions[parameter.id] = values.map((value) => ({
      label: String(value),
      value,
    }))
  } catch {
    dynamicOptions[parameter.id] = parameter.options || []
  } finally {
    loadingOptions[parameter.id] = false
  }
}

/** 刷新所有配置了 optionsFrom 的选择类参数选项 */
async function refreshDynamicOptions() {
  const tasks = parameters.value
    .filter((item) =>
      (item.type === 'select' || item.type === 'multi-select') && item.optionsFrom?.datasetId && item.optionsFrom.field)
    .map((item) => loadDynamicOptions(item))
  await Promise.all(tasks)
}

onMounted(refreshDynamicOptions)
watch(
  () => parameters.value.map((item) =>
    `${item.id}:${item.optionsFrom?.datasetId}:${item.optionsFrom?.field}:${item.optionsFrom?.limit || ''}`).join('|'),
  () => { void refreshDynamicOptions() },
)
</script>

<template>
  <div v-if="parameters.length" class="param-bar">
    <div class="param-fields">
      <div v-for="parameter in parameters" :key="parameter.id" class="param-item">
        <label>{{ parameter.label || parameter.id }}</label>
        <el-input
          v-if="parameter.type === 'text'"
          :model-value="String(modelValue[parameter.id] ?? '')"
          :disabled="readonly"
          clearable
          @update:model-value="setValue(parameter.id, $event)"
        />
        <el-input-number
          v-else-if="parameter.type === 'number'"
          :model-value="Number(modelValue[parameter.id] ?? 0)"
          :disabled="readonly"
          controls-position="right"
          @update:model-value="setValue(parameter.id, $event)"
        />
        <el-date-picker
          v-else-if="parameter.type === 'date'"
          :model-value="(modelValue[parameter.id] as string) || ''"
          type="date"
          value-format="YYYY-MM-DD"
          :disabled="readonly"
          @update:model-value="setValue(parameter.id, $event)"
        />
        <el-date-picker
          v-else-if="parameter.type === 'date-range'"
          :model-value="rangeValue(parameter.id)"
          type="daterange"
          value-format="YYYY-MM-DD"
          :disabled="readonly"
          :start-placeholder="t('common.start')"
          :end-placeholder="t('common.end')"
          @update:model-value="setRange(parameter.id, $event as [string, string] | null)"
        />
        <el-select
          v-else-if="parameter.type === 'select'"
          :model-value="modelValue[parameter.id]"
          :disabled="readonly"
          :loading="!!loadingOptions[parameter.id]"
          clearable
          filterable
          style="width:180px"
          @update:model-value="setValue(parameter.id, $event)"
        >
          <el-option
            v-for="option in resolvedOptions(parameter)"
            :key="String(option.value)"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-else-if="parameter.type === 'multi-select'"
          :model-value="(modelValue[parameter.id] as unknown[]) || []"
          :disabled="readonly"
          :loading="!!loadingOptions[parameter.id]"
          multiple
          clearable
          filterable
          collapse-tags
          style="width:220px"
          @update:model-value="setValue(parameter.id, $event)"
        >
          <el-option
            v-for="option in resolvedOptions(parameter)"
            :key="String(option.value)"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input
          v-else
          :model-value="String(modelValue[parameter.id] ?? '')"
          :disabled="readonly"
          @update:model-value="setValue(parameter.id, $event)"
        />
      </div>
    </div>
    <el-button v-if="!readonly" type="primary" @click="emit('apply')">{{ t('common.apply') }}</el-button>
  </div>
</template>

<style scoped>
.param-bar {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  padding: 12px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}
.param-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  flex: 1;
}
.param-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 140px;
}
.param-item label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

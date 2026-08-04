<script setup lang="ts">
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ChartEncoding } from '@/dashboard/config'
import { isSingleValueChart } from '@/dashboard/config'

const props = defineProps<{
  columns: string[]
  chartType: string
  modelValue: ChartEncoding
  drillPath?: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [ChartEncoding]
  'update:drillPath': [string[]]
}>()

const { t } = useI18n()

const isMap = computed(() => props.chartType === 'map')
const singleValue = computed(() => isSingleValueChart(props.chartType))

const showMapping = computed(() =>
  !['table'].includes(props.chartType) && props.columns.length > 0)

const showCategory = computed(() => showMapping.value && !isMap.value)

const showDrillPath = computed(() =>
  props.columns.length >= 2 && !['table', 'kpi'].includes(props.chartType))

const drillPathModel = computed({
  get: () => props.drillPath || [],
  set: (values: string[]) => emit('update:drillPath', values),
})

/** 读取数值字段列表（单值图表折叠为单元素数组） */
const valueList = computed({
  get: () => {
    const value = props.modelValue.value
    if (Array.isArray(value)) return value
    return value ? [value] : []
  },
  set: (values: string[]) => {
    emit('update:modelValue', {
      ...props.modelValue,
      value: singleValue.value ? values[0] : values,
    })
  },
})

watch(
  () => [props.columns, props.chartType] as const,
  () => {
    if (!props.columns.length) return
    const next: ChartEncoding = { ...props.modelValue }
    if (isMap.value) {
      if (!next.lng || !props.columns.includes(next.lng)) {
        next.lng = props.columns.find((c) => /lng|lon|longitude/i.test(c)) || props.columns[0]
      }
      if (!next.lat || !props.columns.includes(next.lat)) {
        next.lat = props.columns.find((c) => /lat|latitude/i.test(c))
          || props.columns.find((c) => c !== next.lng)
          || props.columns[0]
      }
      const values = Array.isArray(next.value) ? next.value : next.value ? [next.value] : []
      const valid = values.filter((column) => props.columns.includes(column))
      next.value = valid[0]
        || props.columns.find((c) => c !== next.lng && c !== next.lat)
        || props.columns[props.columns.length - 1]
      if (next.category && !props.columns.includes(next.category)) {
        next.category = undefined
      }
    } else {
      if (!next.category || !props.columns.includes(next.category)) {
        next.category = props.columns[0]
      }
      const values = Array.isArray(next.value) ? next.value : next.value ? [next.value] : []
      const valid = values.filter((column) => props.columns.includes(column))
      if (!valid.length) {
        next.value = props.columns.slice(1)
        if (!Array.isArray(next.value) || !next.value.length) {
          next.value = props.columns[props.columns.length - 1]
        }
      } else if (singleValue.value) {
        next.value = valid[0]
      } else {
        next.value = valid
      }
    }
    emit('update:modelValue', next)

    if (props.drillPath?.length) {
      const kept = props.drillPath.filter((col) => props.columns.includes(col))
      if (kept.length !== props.drillPath.length) {
        emit('update:drillPath', kept)
      }
    }
  },
  { immediate: true },
)

/** 组合图中指定数值列的系列类型，缺省为柱 */
function seriesType(column: string): 'bar' | 'line' {
  return props.modelValue.seriesTypes?.[column] || 'bar'
}

/** 更新组合图数值列的柱/线类型 */
function setSeriesType(column: string, type: 'bar' | 'line') {
  emit('update:modelValue', {
    ...props.modelValue,
    seriesTypes: {
      ...(props.modelValue.seriesTypes || {}),
      [column]: type,
    },
  })
}
</script>

<template>
  <div v-if="showMapping" class="encoding">
    <div v-if="isMap" class="row">
      <span>{{ t('encoding.lng') }}</span>
      <el-select
        :model-value="modelValue.lng"
        style="width:140px"
        @update:model-value="emit('update:modelValue', { ...modelValue, lng: $event })"
      >
        <el-option v-for="column in columns" :key="column" :label="column" :value="column" />
      </el-select>
      <span>{{ t('encoding.lat') }}</span>
      <el-select
        :model-value="modelValue.lat"
        style="width:140px"
        @update:model-value="emit('update:modelValue', { ...modelValue, lat: $event })"
      >
        <el-option v-for="column in columns" :key="column" :label="column" :value="column" />
      </el-select>
      <span>{{ t('encoding.value') }}</span>
      <el-select
        :model-value="Array.isArray(modelValue.value) ? modelValue.value[0] : modelValue.value"
        style="width:160px"
        @update:model-value="emit('update:modelValue', { ...modelValue, value: $event })"
      >
        <el-option v-for="column in columns" :key="column" :label="column" :value="column" />
      </el-select>
      <span>{{ t('encoding.name') }}</span>
      <el-select
        clearable
        :model-value="modelValue.category"
        style="width:140px"
        @update:model-value="emit('update:modelValue', { ...modelValue, category: $event || undefined })"
      >
        <el-option v-for="column in columns" :key="column" :label="column" :value="column" />
      </el-select>
    </div>
    <div v-else-if="showCategory" class="row">
      <span>{{ t('encoding.category') }}</span>
      <el-select
        :model-value="modelValue.category"
        style="width:160px"
        @update:model-value="emit('update:modelValue', { ...modelValue, category: $event })"
      >
        <el-option v-for="column in columns" :key="column" :label="column" :value="column" />
      </el-select>
      <span>{{ t('encoding.value') }}</span>
      <el-select
        v-model="valueList"
        :multiple="!singleValue"
        collapse-tags
        style="width:220px"
      >
        <el-option v-for="column in columns" :key="column" :label="column" :value="column" />
      </el-select>
    </div>
    <div v-if="chartType === 'combo' && valueList.length" class="combo-types">
      <div v-for="column in valueList" :key="column" class="combo-row">
        <span>{{ column }}</span>
        <el-select :model-value="seriesType(column)" style="width:100px" @update:model-value="setSeriesType(column, $event)">
          <el-option :label="t('encoding.bar')" value="bar" />
          <el-option :label="t('encoding.line')" value="line" />
        </el-select>
      </div>
    </div>
    <div v-if="showDrillPath" class="drill-row">
      <span>{{ t('encoding.drillPath') }}</span>
      <el-select
        v-model="drillPathModel"
        multiple
        collapse-tags
        collapse-tags-tooltip
        style="width:320px"
      >
        <el-option v-for="column in columns" :key="column" :label="column" :value="column" />
      </el-select>
      <span class="hint">{{ t('encoding.drillPathHint') }}</span>
    </div>
  </div>
</template>

<style scoped>
.encoding { margin: 8px 0 12px; }
.row, .drill-row { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.drill-row { margin-top: 8px; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; }
.combo-types { margin-top: 8px; display: flex; flex-direction: column; gap: 6px; }
.combo-row { display: flex; gap: 8px; align-items: center; }
</style>

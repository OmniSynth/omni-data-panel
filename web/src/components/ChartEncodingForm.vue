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
const isCombo = computed(() => props.chartType === 'combo')

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
    <div v-if="isMap" class="grid map-grid">
      <label class="field">
        <span class="label">{{ t('encoding.lng') }}</span>
        <el-select
          class="control"
          :model-value="modelValue.lng"
          @update:model-value="emit('update:modelValue', { ...modelValue, lng: $event })"
        >
          <el-option v-for="column in columns" :key="column" :label="column" :value="column" />
        </el-select>
      </label>
      <label class="field">
        <span class="label">{{ t('encoding.lat') }}</span>
        <el-select
          class="control"
          :model-value="modelValue.lat"
          @update:model-value="emit('update:modelValue', { ...modelValue, lat: $event })"
        >
          <el-option v-for="column in columns" :key="column" :label="column" :value="column" />
        </el-select>
      </label>
      <label class="field">
        <span class="label">{{ t('encoding.value') }}</span>
        <el-select
          class="control"
          :model-value="Array.isArray(modelValue.value) ? modelValue.value[0] : modelValue.value"
          @update:model-value="emit('update:modelValue', { ...modelValue, value: $event })"
        >
          <el-option v-for="column in columns" :key="column" :label="column" :value="column" />
        </el-select>
      </label>
      <label class="field">
        <span class="label">{{ t('encoding.name') }}</span>
        <el-select
          class="control"
          clearable
          :model-value="modelValue.category"
          @update:model-value="emit('update:modelValue', { ...modelValue, category: $event || undefined })"
        >
          <el-option v-for="column in columns" :key="column" :label="column" :value="column" />
        </el-select>
      </label>
    </div>

    <div v-else-if="showCategory" class="grid main-grid">
      <label class="field">
        <span class="label">{{ t('encoding.category') }}</span>
        <el-select
          class="control"
          :model-value="modelValue.category"
          @update:model-value="emit('update:modelValue', { ...modelValue, category: $event })"
        >
          <el-option v-for="column in columns" :key="column" :label="column" :value="column" />
        </el-select>
      </label>
      <div class="field">
        <span class="label">{{ t('encoding.value') }}</span>
        <el-select
          v-model="valueList"
          class="control"
          :multiple="!singleValue"
          collapse-tags
          collapse-tags-tooltip
        >
          <el-option v-for="column in columns" :key="column" :label="column" :value="column" />
        </el-select>
        <div v-if="isCombo && valueList.length" class="series-list">
          <div v-for="column in valueList" :key="column" class="series-row">
            <span class="series-name" :title="column">{{ column }}</span>
            <el-select
              class="series-type"
              :model-value="seriesType(column)"
              @update:model-value="setSeriesType(column, $event)"
            >
              <el-option :label="t('encoding.bar')" value="bar" />
              <el-option :label="t('encoding.line')" value="line" />
            </el-select>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showDrillPath" class="field drill-field">
      <span class="label">{{ t('encoding.drillPath') }}</span>
      <el-select
        v-model="drillPathModel"
        class="control"
        multiple
        collapse-tags
        collapse-tags-tooltip
        filterable
        clearable
      >
        <el-option v-for="column in columns" :key="column" :label="column" :value="column" />
      </el-select>
      <p class="hint">{{ t('encoding.drillPathHint') }}</p>
    </div>
  </div>
</template>

<style scoped>
.encoding {
  margin: 4px 0 16px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
}
.grid {
  display: grid;
  gap: 12px 16px;
}
.main-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.map-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.label {
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.2;
}
.control {
  width: 100%;
}
.series-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 2px;
  padding: 8px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}
.series-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 88px;
  gap: 8px;
  align-items: center;
}
.series-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.series-type {
  width: 88px;
}
.drill-field {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-extra-light);
}
.hint {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.4;
}
@media (max-width: 720px) {
  .main-grid,
  .map-grid {
    grid-template-columns: 1fr;
  }
}
</style>

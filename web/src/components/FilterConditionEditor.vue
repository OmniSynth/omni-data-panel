<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { displayLabel } from '@/display'
import type { FilterCondition, FilterOperator } from '@/types'

const conditions = defineModel<FilterCondition[]>({ required: true })
defineProps<{
  fields?: Array<{ name: string }>
}>()

const { t } = useI18n()
const operators: FilterOperator[] = ['EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE', 'LIKE', 'IN']

function add() {
  conditions.value.push({ field: '', operator: 'EQ', value: '' })
}

function remove(index: number) {
  conditions.value.splice(index, 1)
}
</script>

<template>
  <div class="filter-editor">
    <div v-for="(item, index) in conditions" :key="index" class="row">
      <el-select
        v-if="fields?.length"
        v-model="item.field"
        filterable
        allow-create
        default-first-option
        :placeholder="t('workbench.field')"
        class="field"
      >
        <el-option v-for="field in fields" :key="field.name" :label="field.name" :value="field.name" />
      </el-select>
      <el-input v-else v-model="item.field" :placeholder="t('workbench.field')" class="field" />
      <el-select v-model="item.operator" :placeholder="t('workbench.operator')" class="operator">
        <el-option v-for="op in operators" :key="op" :label="displayLabel(op)" :value="op" />
      </el-select>
      <el-input v-model="item.value" :placeholder="t('workbench.value')" class="value" />
      <el-button type="danger" link @click="remove(index)">{{ t('common.delete') }}</el-button>
    </div>
    <el-button link type="primary" @click="add">{{ t('workbench.addFilter') }}</el-button>
  </div>
</template>

<style scoped>
.filter-editor { width: 100%; }
.row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.field { width: 160px; }
.operator { width: 120px; }
.value { flex: 1; min-width: 140px; }
</style>

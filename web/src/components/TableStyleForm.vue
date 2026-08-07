<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type {
  TableColumnFormat,
  TableColumnStyle,
  TableRowRule,
  TableStyle,
} from '@/dashboard/config'

const props = defineProps<{
  columns: string[]
  modelValue: TableStyle
}>()

const emit = defineEmits<{
  'update:modelValue': [TableStyle]
}>()

const { t } = useI18n()

const FORMAT_OPTIONS: TableColumnFormat[] = [
  'auto', 'text', 'number', 'percent', 'datetime', 'boolean', 'link',
]
const ALIGN_OPTIONS = ['left', 'center', 'right'] as const
const OP_OPTIONS = ['EQ', 'NE', 'LIKE'] as const

const TEXT_COLORS = [
  '#111827', '#374151', '#6b7280', '#9ca3af',
  '#dc2626', '#ea580c', '#d97706', '#ca8a04',
  '#16a34a', '#059669', '#0d9488',
  '#2563eb', '#0284c7', '#7c3aed', '#db2777',
]
const ROW_BACKGROUNDS = [
  '#fef2f2', '#fee2e2', '#fff7ed', '#ffedd5',
  '#fefce8', '#fef9c3', '#f0fdf4', '#dcfce7',
  '#ecfdf5', '#eff6ff', '#dbeafe', '#f5f3ff',
  '#ede9fe', '#fdf2f8', '#fce7f3', '#f3f4f6',
  '#e5e7eb', '#fef3c7', '#cffafe', '#e0e7ff',
]
const DEFAULT_ROW_BACKGROUND = ROW_BACKGROUNDS[0]

const columnEntries = computed(() =>
  props.columns.map((name) => ({
    name,
    style: props.modelValue.columns?.[name] || {},
  })))

const rowRules = computed(() => props.modelValue.rowRules || [])

const customizedCount = computed(() =>
  columnEntries.value.filter((entry) =>
    entry.style.format || entry.style.align || entry.style.color).length)

/** 更新单列样式；空配置时删除该列键 */
function patchColumn(name: string, patch: Partial<TableColumnStyle>) {
  const columns = { ...(props.modelValue.columns || {}) }
  const next: TableColumnStyle = { ...(columns[name] || {}), ...patch }
  if (!next.format || next.format === 'auto') delete next.format
  if (!next.align || next.align === 'left') delete next.align
  if (!next.color) delete next.color
  if (!next.format && !next.align && !next.color) {
    delete columns[name]
  } else {
    columns[name] = next
  }
  emit('update:modelValue', {
    ...props.modelValue,
    columns: Object.keys(columns).length ? columns : undefined,
  })
}

function setRowRules(rules: TableRowRule[]) {
  emit('update:modelValue', {
    ...props.modelValue,
    rowRules: rules.length ? rules : undefined,
  })
}

function addRowRule() {
  const field = props.columns[0] || ''
  if (!field) return
  setRowRules([
    ...rowRules.value,
    { field, op: 'EQ', value: '', background: DEFAULT_ROW_BACKGROUND },
  ])
}

function updateRowRule(index: number, patch: Partial<TableRowRule>) {
  const next = rowRules.value.map((rule, i) => (i === index ? { ...rule, ...patch } : rule))
  setRowRules(next)
}

function removeRowRule(index: number) {
  setRowRules(rowRules.value.filter((_, i) => i !== index))
}

function isCustomized(style: TableColumnStyle) {
  return !!(style.format || style.align || style.color)
}
</script>

<template>
  <div v-if="columns.length" class="table-style">
    <section class="panel">
      <header class="panel-head">
        <div class="panel-title">
          <strong>{{ t('tableStyle.columns') }}</strong>
          <span v-if="customizedCount" class="badge">{{ customizedCount }}</span>
        </div>
        <p class="hint">{{ t('tableStyle.columnsHint') }}</p>
      </header>

      <div class="col-table">
        <div class="col-head" aria-hidden="true">
          <span>{{ t('tableStyle.colName') }}</span>
          <span>{{ t('tableStyle.colFormat') }}</span>
          <span>{{ t('tableStyle.colAlign') }}</span>
          <span>{{ t('tableStyle.colColor') }}</span>
        </div>
        <div
          v-for="entry in columnEntries"
          :key="entry.name"
          class="col-row"
          :class="{ customized: isCustomized(entry.style) }"
        >
          <code class="col-name" :title="entry.name">{{ entry.name }}</code>
          <el-select
            :model-value="entry.style.format || 'auto'"
            class="ctrl"
            size="small"
            @update:model-value="(v: string) => patchColumn(entry.name, { format: v as TableColumnFormat })"
          >
            <el-option
              v-for="format in FORMAT_OPTIONS"
              :key="format"
              :label="t(`tableStyle.format.${format}`)"
              :value="format"
            />
          </el-select>
          <el-select
            :model-value="entry.style.align || 'left'"
            class="ctrl"
            size="small"
            @update:model-value="(v: string) => patchColumn(entry.name, { align: v as TableColumnStyle['align'] })"
          >
            <el-option
              v-for="align in ALIGN_OPTIONS"
              :key="align"
              :label="t(`tableStyle.align.${align}`)"
              :value="align"
            />
          </el-select>
          <el-color-picker
            class="ctrl color"
            size="small"
            :model-value="entry.style.color || undefined"
            :predefine="TEXT_COLORS"
            show-alpha
            @update:model-value="(v: string | null) => patchColumn(entry.name, { color: v || undefined })"
          />
        </div>
      </div>
    </section>

    <section class="panel">
      <header class="panel-head row">
        <div>
          <div class="panel-title">
            <strong>{{ t('tableStyle.rowRules') }}</strong>
            <span v-if="rowRules.length" class="badge">{{ rowRules.length }}</span>
          </div>
          <p class="hint">{{ t('tableStyle.rowRulesHint') }}</p>
        </div>
        <el-button type="primary" plain size="small" @click="addRowRule">
          {{ t('tableStyle.addRule') }}
        </el-button>
      </header>

      <div v-if="!rowRules.length" class="empty">
        <p class="empty-title">{{ t('tableStyle.noRules') }}</p>
        <p class="empty-hint">{{ t('tableStyle.noRulesHint') }}</p>
        <el-button size="small" @click="addRowRule">{{ t('tableStyle.addRule') }}</el-button>
      </div>

      <div v-else class="rule-list">
        <div v-for="(rule, index) in rowRules" :key="index" class="rule-card">
          <span class="rule-index">{{ index + 1 }}</span>
          <el-select
            :model-value="rule.field"
            class="ctrl field"
            size="small"
            filterable
            @update:model-value="(v: string) => updateRowRule(index, { field: v })"
          >
            <el-option v-for="column in columns" :key="column" :label="column" :value="column" />
          </el-select>
          <el-select
            :model-value="rule.op"
            class="ctrl op"
            size="small"
            @update:model-value="(v: string) => updateRowRule(index, { op: v as TableRowRule['op'] })"
          >
            <el-option
              v-for="op in OP_OPTIONS"
              :key="op"
              :label="t(`tableStyle.op.${op}`)"
              :value="op"
            />
          </el-select>
          <el-input
            :model-value="rule.value"
            class="ctrl value"
            size="small"
            :placeholder="t('tableStyle.ruleValue')"
            @update:model-value="(v: string) => updateRowRule(index, { value: v })"
          />
          <el-color-picker
            class="ctrl bg"
            size="small"
            :model-value="rule.background"
            :predefine="ROW_BACKGROUNDS"
            show-alpha
            @update:model-value="(v: string | null) => updateRowRule(index, { background: v || DEFAULT_ROW_BACKGROUND })"
          />
          <el-button class="rule-del" link type="danger" size="small" @click="removeRowRule(index)">
            {{ t('common.delete') }}
          </el-button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.table-style {
  margin: 8px 0 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.panel {
  border: 1px solid var(--omni-border, var(--el-border-color-lighter));
  border-radius: var(--omni-radius-sm, 8px);
  background: var(--omni-card, var(--el-fill-color-blank));
  padding: 12px 14px 14px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.panel-head {
  margin-bottom: 10px;
}
.panel-head.row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.panel-title strong {
  font-size: 14px;
  font-weight: 600;
  color: var(--omni-text, var(--el-text-color-primary));
}
.badge {
  min-width: 18px;
  height: 18px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--omni-accent-soft, var(--el-color-primary-light-9));
  color: var(--omni-accent-strong, var(--el-color-primary));
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  text-align: center;
}
.hint {
  margin: 0;
  color: var(--omni-muted, var(--el-text-color-secondary));
  font-size: 12px;
  line-height: 1.45;
}
.col-table {
  display: flex;
  flex-direction: column;
  gap: 4px;
  border: 1px solid var(--omni-border, var(--el-border-color-lighter));
  border-radius: 8px;
  overflow: hidden;
  background: var(--omni-surface, var(--el-fill-color-blank));
}
.col-head,
.col-row {
  display: grid;
  grid-template-columns: minmax(140px, 1fr) 112px 96px 40px;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
}
.col-head > :last-child,
.col-row .color {
  justify-self: end;
}
.col-head {
  background: var(--omni-surface, var(--el-fill-color-light));
  border-bottom: 1px solid var(--omni-border, var(--el-border-color-lighter));
  color: var(--omni-muted, var(--el-text-color-secondary));
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
  text-transform: none;
}
.col-row {
  background: var(--omni-card, #fff);
  border-top: 1px solid var(--omni-border, var(--el-border-color-extra-light));
}
.col-row:first-of-type {
  border-top: 0;
}
.col-row.customized {
  background: color-mix(in srgb, var(--omni-accent-soft, #eef6fc) 55%, transparent);
}
.col-name {
  margin: 0;
  padding: 3px 8px;
  border-radius: 6px;
  background: var(--omni-bg, var(--el-fill-color-light));
  border: 1px solid var(--omni-border, var(--el-border-color-lighter));
  color: var(--omni-text, var(--el-text-color-primary));
  font-family: Consolas, "Courier New", "PingFang SC", monospace;
  font-size: 12px;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
  justify-self: start;
}
.ctrl {
  width: 100%;
}
.ctrl.color,
.ctrl.bg {
  width: auto;
}
.ctrl.bg {
  justify-self: start;
}
.rule-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.rule-card {
  display: grid;
  grid-template-columns: 28px minmax(120px, 1.1fr) 96px minmax(100px, 1fr) auto auto;
  gap: 8px;
  align-items: center;
  padding: 10px;
  border: 1px solid var(--omni-border, var(--el-border-color-lighter));
  border-radius: 8px;
  background: var(--omni-surface, var(--el-fill-color-blank));
}
.rule-index {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--omni-accent-soft, var(--el-color-primary-light-9));
  color: var(--omni-accent-strong, var(--el-color-primary));
  font-size: 11px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.rule-del {
  justify-self: end;
}
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 20px 16px;
  border: 1px dashed var(--omni-border, var(--el-border-color));
  border-radius: 8px;
  background: var(--omni-surface, var(--el-fill-color-blank));
  text-align: center;
}
.empty-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--omni-text, var(--el-text-color-primary));
}
.empty-hint {
  margin: 0 0 6px;
  font-size: 12px;
  color: var(--omni-muted, var(--el-text-color-secondary));
}
@media (max-width: 960px) {
  .col-head { display: none; }
  .col-row {
    grid-template-columns: 1fr 1fr;
    gap: 8px;
  }
  .col-name {
    grid-column: 1 / -1;
  }
  .rule-card {
    grid-template-columns: 28px 1fr 1fr;
  }
  .rule-card .value,
  .rule-card .bg {
    grid-column: 2 / -1;
  }
  .rule-del {
    grid-column: 1 / -1;
    justify-self: end;
  }
}
</style>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { EditorState, Compartment } from '@codemirror/state'
import { EditorView, keymap, placeholder, lineNumbers, highlightActiveLine, highlightActiveLineGutter } from '@codemirror/view'
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands'
import { sql } from '@codemirror/lang-sql'
import { autocompletion, completionKeymap, startCompletion } from '@codemirror/autocomplete'
import { resolveSqlDialect, type SqlDialectId } from '@/sql/dialects'
import { formatSql } from '@/sql/format'
import { catalogFromEditorSchema, createColumnCompletionSource } from '@/sql/columnCompletion'
import type { EditorSqlSchema } from '@/sql/schema'
import { sqlNavicatHighlighting } from '@/sql/highlight'

const props = withDefaults(defineProps<{
  modelValue: string
  dialect?: SqlDialectId | string
  jdbcUrl?: string
  /** CodeMirror 嵌套 schema：库 → 表 → 字段 */
  schema?: EditorSqlSchema
  /** 默认库名；设置后未限定表名可直接联想该库下的表 */
  defaultSchema?: string
  placeholderText?: string
}>(), {
  schema: () => ({}),
})

const { t } = useI18n()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const host = ref<HTMLElement>()
let editor: EditorView | undefined
const language = new Compartment()

const completionTheme = EditorView.theme({
  '.cm-tooltip.cm-tooltip-autocomplete': {
    border: '1px solid var(--omni-border)',
    borderRadius: '10px',
    backgroundColor: 'var(--omni-card)',
    boxShadow: 'var(--omni-shadow)',
    overflow: 'hidden',
    fontFamily: '"Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
  },
  '.cm-tooltip.cm-tooltip-autocomplete > ul': {
    fontFamily: 'Consolas, "Courier New", "PingFang SC", monospace',
    fontSize: '13px',
    maxHeight: '280px',
    padding: '6px',
    margin: '0',
  },
  '.cm-tooltip.cm-tooltip-autocomplete > ul > li': {
    display: 'flex',
    alignItems: 'center',
    gap: '2px',
    padding: '7px 10px',
    margin: '1px 0',
    borderRadius: '7px',
    lineHeight: '1.35',
    color: 'var(--omni-text)',
  },
  '.cm-tooltip.cm-tooltip-autocomplete > ul > li[aria-selected]': {
    backgroundColor: 'var(--omni-accent-soft)',
    color: 'var(--omni-accent-strong)',
  },
  '.cm-completionIcon': {
    width: '1.1em',
    opacity: '0.55',
    color: 'var(--omni-muted)',
    paddingRight: '0.55em',
  },
  '.cm-tooltip.cm-tooltip-autocomplete > ul > li[aria-selected] .cm-completionIcon': {
    opacity: '0.85',
    color: 'var(--omni-accent)',
  },
  '.cm-completionLabel': {
    flex: '1 1 auto',
    minWidth: '0',
  },
  '.cm-completionMatchedText': {
    textDecoration: 'none',
    fontWeight: '700',
    color: 'var(--omni-accent-strong)',
  },
  '.cm-tooltip.cm-tooltip-autocomplete > ul > li[aria-selected] .cm-completionMatchedText': {
    color: 'var(--omni-accent-strong)',
  },
  '.cm-completionDetail': {
    marginLeft: '10px',
    fontStyle: 'normal',
    fontSize: '11px',
    fontFamily: '"Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
    color: 'var(--omni-muted)',
    letterSpacing: '0.02em',
  },
  '.cm-tooltip.cm-tooltip-autocomplete > ul > li[aria-selected] .cm-completionDetail': {
    color: 'var(--omni-muted)',
  },
  '.cm-tooltip.cm-completionInfo': {
    border: '1px solid var(--omni-border)',
    borderRadius: '8px',
    backgroundColor: 'var(--omni-card)',
    color: 'var(--omni-muted)',
    boxShadow: 'var(--omni-shadow)',
    padding: '8px 10px',
    fontSize: '12px',
  },
})

/** 按方言与 schema 构建 CodeMirror SQL 语言扩展 */
function languageExtension() {
  const adapter = resolveSqlDialect(props.dialect, props.jdbcUrl)
  const catalog = catalogFromEditorSchema(props.schema)
  const support = sql({
    dialect: adapter.language,
    schema: props.schema,
    defaultSchema: props.defaultSchema || undefined,
    upperCaseKeywords: true,
  })
  return [
    support,
    support.language.data.of({
      autocomplete: createColumnCompletionSource(
        () => catalog,
        () => props.defaultSchema,
      ),
    }),
  ]
}

/** 将补全类型映射为界面展示标签 */
function completionTypeLabel(type?: string) {
  const normalized = (type || '').toLowerCase()
  if (normalized.includes('keyword')) return t('sqlEditor.keyword')
  if (normalized.includes('namespace') || normalized.includes('schema')) return t('sqlEditor.schema')
  if (normalized.includes('type') || normalized.includes('class')) return t('sqlEditor.table')
  if (normalized.includes('property') || normalized.includes('variable') || normalized.includes('field')) return t('sqlEditor.column')
  if (normalized.includes('function') || normalized.includes('method')) return t('sqlEditor.function')
  if (normalized.includes('constant')) return t('sqlEditor.constant')
  if (normalized.includes('text')) return t('sqlEditor.text')
  return t('sqlEditor.suggestion')
}

/** 按补全类型返回选项 CSS class，用于区分关键字/表/字段等 */
function optionClass(completion: { type?: string }) {
  const type = (completion.type || '').toLowerCase()
  if (type.includes('keyword')) return 'omni-cmp-keyword'
  if (type.includes('namespace') || type.includes('schema')) return 'omni-cmp-schema'
  if (type.includes('type') || type.includes('class')) return 'omni-cmp-table'
  if (type.includes('property') || type.includes('variable') || type.includes('field')) return 'omni-cmp-column'
  if (type.includes('function') || type.includes('method')) return 'omni-cmp-function'
  return 'omni-cmp-other'
}

/** 一键格式化当前文档；失败时保持原文。 */
function formatDocument(): boolean {
  if (!editor) return false
  const current = editor.state.doc.toString()
  if (!current.trim()) return false
  try {
    const next = formatSql(current, props.dialect, props.jdbcUrl)
    if (next === current) return true
    editor.dispatch({
      changes: { from: 0, to: current.length, insert: next },
      userEvent: 'input.format',
    })
    return true
  } catch {
    return false
  }
}

onMounted(() => {
  editor = new EditorView({
    parent: host.value,
    state: EditorState.create({
      doc: props.modelValue,
      extensions: [
        history(),
        lineNumbers(),
        highlightActiveLine(),
        highlightActiveLineGutter(),
        keymap.of([
          ...defaultKeymap,
          ...historyKeymap,
          ...completionKeymap,
          { key: 'Ctrl-Space', run: startCompletion },
          { key: 'Shift-Alt-f', run: () => formatDocument() },
          { key: 'Mod-Shift-f', run: () => formatDocument() },
        ]),
        autocompletion({
          activateOnTyping: true,
          icons: true,
          optionClass,
          tooltipClass: () => 'omni-sql-completion',
          addToOptions: [{
            render(completion) {
              const detail = document.createElement('span')
              detail.className = 'cm-completionDetail omni-completion-type'
              detail.textContent = completionTypeLabel(completion.type)
              return detail
            },
            position: 90,
          }],
        }),
        language.of(languageExtension()),
        sqlNavicatHighlighting,
        completionTheme,
        EditorView.lineWrapping,
        placeholder(props.placeholderText ?? t('sqlEditor.placeholder')),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) emit('update:modelValue', update.state.doc.toString())
        }),
      ],
    }),
  })
})

watch(() => props.modelValue, (value) => {
  if (editor && value !== editor.state.doc.toString()) {
    editor.dispatch({ changes: { from: 0, to: editor.state.doc.length, insert: value } })
  }
})

watch(() => [props.dialect, props.jdbcUrl, props.schema, props.defaultSchema] as const, () => {
  if (!editor) return
  editor.dispatch({ effects: language.reconfigure(languageExtension()) })
}, { deep: true })

onBeforeUnmount(() => editor?.destroy())

defineExpose({ format: formatDocument })
</script>

<template><div ref="host" class="sql-editor" /></template>

<style scoped>
.sql-editor {
  min-height: 280px;
  background: var(--omni-editor-bg);
}
:deep(.cm-editor) {
  min-height: 280px;
  font-size: 14px;
  font-family: Consolas, "Courier New", "PingFang SC", monospace;
  color: var(--omni-editor-fg);
  background: var(--omni-editor-bg);
}
:deep(.cm-focused) {
  outline: none;
}
:deep(.cm-scroller) {
  font-family: inherit;
  line-height: 1.55;
  padding: 8px 0;
}
:deep(.cm-content) {
  padding: 4px 14px;
  caret-color: var(--omni-editor-fg);
}
:deep(.cm-gutters) {
  background: var(--omni-editor-gutter);
  border-right: 1px solid var(--omni-border);
  color: var(--omni-muted);
}
:deep(.cm-activeLineGutter) {
  background: var(--omni-editor-gutter-active);
  color: var(--omni-text);
}
:deep(.cm-activeLine) {
  background: rgba(80, 158, 227, 0.06);
}
:deep(.cm-selectionBackground),
:deep(.cm-editor ::selection) {
  background: rgba(80, 158, 227, 0.28) !important;
}
:deep(.cm-placeholder) {
  color: var(--omni-muted);
}
</style>

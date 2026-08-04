<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { EditorState, Compartment } from '@codemirror/state'
import { EditorView, keymap, placeholder, lineNumbers, highlightActiveLine, highlightActiveLineGutter } from '@codemirror/view'
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands'
import { sql } from '@codemirror/lang-sql'
import { autocompletion, completionKeymap, startCompletion } from '@codemirror/autocomplete'
import { resolveSqlDialect, type SqlDialectId } from '@/sql/dialects'
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
  placeholderText: '输入 SQL，支持关键字、库名、表名与字段联想（Ctrl+Space）',
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const host = ref<HTMLElement>()
let editor: EditorView | undefined
const language = new Compartment()

const completionTheme = EditorView.theme({
  '.cm-tooltip.cm-tooltip-autocomplete': {
    border: '1px solid #e5e7eb',
    borderRadius: '10px',
    backgroundColor: '#ffffff',
    boxShadow: '0 12px 32px rgba(15, 23, 42, 0.12), 0 2px 8px rgba(15, 23, 42, 0.06)',
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
    color: '#1f2937',
  },
  '.cm-tooltip.cm-tooltip-autocomplete > ul > li[aria-selected]': {
    backgroundColor: '#eef6fc',
    color: '#0f3f6e',
  },
  '.cm-completionIcon': {
    width: '1.1em',
    opacity: '0.55',
    color: '#6b7280',
    paddingRight: '0.55em',
  },
  '.cm-tooltip.cm-tooltip-autocomplete > ul > li[aria-selected] .cm-completionIcon': {
    opacity: '0.85',
    color: '#509ee3',
  },
  '.cm-completionLabel': {
    flex: '1 1 auto',
    minWidth: '0',
  },
  '.cm-completionMatchedText': {
    textDecoration: 'none',
    fontWeight: '700',
    color: '#1d4f91',
  },
  '.cm-tooltip.cm-tooltip-autocomplete > ul > li[aria-selected] .cm-completionMatchedText': {
    color: '#0b5cab',
  },
  '.cm-completionDetail': {
    marginLeft: '10px',
    fontStyle: 'normal',
    fontSize: '11px',
    fontFamily: '"Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
    color: '#9ca3af',
    letterSpacing: '0.02em',
  },
  '.cm-tooltip.cm-tooltip-autocomplete > ul > li[aria-selected] .cm-completionDetail': {
    color: '#64748b',
  },
  '.cm-tooltip.cm-completionInfo': {
    border: '1px solid #e5e7eb',
    borderRadius: '8px',
    backgroundColor: '#ffffff',
    color: '#4b5563',
    boxShadow: '0 8px 20px rgba(15, 23, 42, 0.1)',
    padding: '8px 10px',
    fontSize: '12px',
  },
})

function languageExtension() {
  const adapter = resolveSqlDialect(props.dialect, props.jdbcUrl)
  return sql({
    dialect: adapter.language,
    schema: props.schema,
    defaultSchema: props.defaultSchema || undefined,
    upperCaseKeywords: true,
  })
}

function completionTypeLabel(type?: string) {
  const normalized = (type || '').toLowerCase()
  if (normalized.includes('keyword')) return '关键字'
  if (normalized.includes('namespace') || normalized.includes('schema')) return '库'
  if (normalized.includes('type') || normalized.includes('class')) return '表'
  if (normalized.includes('property') || normalized.includes('variable') || normalized.includes('field')) return '字段'
  if (normalized.includes('function') || normalized.includes('method')) return '函数'
  if (normalized.includes('constant')) return '常量'
  if (normalized.includes('text')) return '文本'
  return '建议'
}

function optionClass(completion: { type?: string }) {
  const type = (completion.type || '').toLowerCase()
  if (type.includes('keyword')) return 'omni-cmp-keyword'
  if (type.includes('namespace') || type.includes('schema')) return 'omni-cmp-schema'
  if (type.includes('type') || type.includes('class')) return 'omni-cmp-table'
  if (type.includes('property') || type.includes('variable') || type.includes('field')) return 'omni-cmp-column'
  if (type.includes('function') || type.includes('method')) return 'omni-cmp-function'
  return 'omni-cmp-other'
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
        placeholder(props.placeholderText),
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
</script>

<template><div ref="host" class="sql-editor" /></template>

<style scoped>
.sql-editor {
  min-height: 280px;
  background: #ffffff;
}
:deep(.cm-editor) {
  min-height: 280px;
  font-size: 14px;
  font-family: Consolas, "Courier New", "PingFang SC", monospace;
  color: #000000;
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
  caret-color: #000000;
}
:deep(.cm-gutters) {
  background: #f7f7f7;
  border-right: 1px solid #e5e7eb;
  color: #9ca3af;
}
:deep(.cm-activeLineGutter) {
  background: #eef5fc;
  color: #4b5563;
}
:deep(.cm-activeLine) {
  background: rgba(80, 158, 227, 0.06);
}
:deep(.cm-selectionBackground),
:deep(.cm-editor ::selection) {
  background: rgba(80, 158, 227, 0.28) !important;
}
:deep(.cm-placeholder) {
  color: #9ca3af;
}
</style>

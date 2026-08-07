<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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
import { sqlHighlightingFor } from '@/sql/highlight'
import { useThemeStore } from '@/stores/theme'
import { useFullscreen } from '@/composables/useFullscreen'

const props = withDefaults(defineProps<{
  modelValue: string
  dialect?: SqlDialectId | string
  jdbcUrl?: string
  /** CodeMirror 嵌套 schema：库 → 表 → 字段 */
  schema?: EditorSqlSchema
  /** 默认库名；设置后未限定表名可直接联想该库下的表 */
  defaultSchema?: string
  placeholderText?: string
  /** 是否显示编辑器自身全屏按钮（外层已整块全屏时可关闭） */
  showFullscreen?: boolean
}>(), {
  schema: () => ({}),
  showFullscreen: true,
})

const { t } = useI18n()
const themeStore = useThemeStore()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const shellRef = ref<HTMLElement | null>(null)
const host = ref<HTMLElement>()
const { isFullscreen, toggle: toggleFullscreen } = useFullscreen(shellRef)
let editor: EditorView | undefined
const language = new Compartment()
const highlighting = new Compartment()

/** 切换全屏并让 CodeMirror 重新测量高度 */
async function onToggleFullscreen() {
  await toggleFullscreen()
  await nextTick()
  editor?.requestMeasure()
  editor?.focus()
}

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

/** 在光标处插入文本；两侧按需补空格，并聚焦编辑器。 */
function insertText(text: string): boolean {
  if (!editor || !text) return false
  const { from, to } = editor.state.selection.main
  const doc = editor.state.doc
  const before = from > 0 ? doc.sliceString(from - 1, from) : ''
  const after = to < doc.length ? doc.sliceString(to, to + 1) : ''
  const needsLeading = !!before && !/\s/.test(before) && !/[.(,=[+]/.test(before)
  const needsTrailing = !!after && !/\s/.test(after) && !/[.),;]/.test(after)
  const insert = `${needsLeading ? ' ' : ''}${text}${needsTrailing ? ' ' : ''}`
  const cursor = from + insert.length
  editor.dispatch({
    changes: { from, to, insert },
    selection: { anchor: cursor },
    userEvent: 'input.type',
  })
  editor.focus()
  return true
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
          ...(props.showFullscreen
            ? [{
                key: 'F10',
                run: () => {
                  void onToggleFullscreen()
                  return true
                },
              }]
            : []),
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
        highlighting.of(sqlHighlightingFor(themeStore.isDark)),
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

watch(() => themeStore.isDark, (dark) => {
  if (!editor) return
  editor.dispatch({ effects: highlighting.reconfigure(sqlHighlightingFor(dark)) })
})

watch(isFullscreen, async () => {
  await nextTick()
  editor?.requestMeasure()
})

onBeforeUnmount(() => editor?.destroy())

defineExpose({
  format: formatDocument,
  insertText,
  requestMeasure: () => editor?.requestMeasure(),
})
</script>

<template>
  <div
    ref="shellRef"
    class="sql-editor-shell"
    :class="{ 'is-fullscreen': showFullscreen && isFullscreen }"
  >
    <div v-if="showFullscreen" class="sql-editor-chrome">
      <el-button
        class="fs-btn"
        text
        size="small"
        :title="t('sqlEditor.fullscreenHint')"
        @click="onToggleFullscreen"
      >
        {{ isFullscreen ? t('sqlEditor.exitFullscreen') : t('sqlEditor.fullscreen') }}
      </el-button>
    </div>
    <div ref="host" class="sql-editor" />
  </div>
</template>

<style scoped>
.sql-editor-shell {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 280px;
  background: var(--omni-editor-bg);
  border-radius: inherit;
}
.sql-editor-chrome {
  position: absolute;
  top: 6px;
  right: 8px;
  z-index: 2;
  display: flex;
  gap: 4px;
}
.fs-btn {
  color: var(--omni-muted);
  background: color-mix(in srgb, var(--omni-card) 88%, transparent);
  border: 1px solid var(--omni-border);
  border-radius: 6px;
  padding: 2px 8px;
}
.fs-btn:hover {
  color: var(--omni-accent-strong);
  border-color: var(--omni-accent);
}
.sql-editor {
  flex: 1;
  min-height: 280px;
  background: var(--omni-editor-bg);
}
:deep(.cm-editor) {
  min-height: 280px;
  height: 100%;
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
  background: var(--omni-editor-active-line);
}
:deep(.cm-selectionBackground),
:deep(.cm-editor ::selection) {
  background: var(--omni-editor-selection) !important;
}
:deep(.cm-placeholder) {
  color: var(--omni-muted);
}

.sql-editor-shell.is-fullscreen,
.sql-editor-shell:fullscreen,
.sql-editor-shell:-webkit-full-screen {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: 12px 16px 16px;
  background: var(--omni-bg);
}
.sql-editor-shell.is-fullscreen .sql-editor-chrome,
.sql-editor-shell:fullscreen .sql-editor-chrome,
.sql-editor-shell:-webkit-full-screen .sql-editor-chrome {
  position: static;
  justify-content: flex-end;
  margin-bottom: 8px;
}
.sql-editor-shell.is-fullscreen .sql-editor,
.sql-editor-shell:fullscreen .sql-editor,
.sql-editor-shell:-webkit-full-screen .sql-editor {
  min-height: 0;
  border: 1px solid var(--omni-border);
  border-radius: var(--omni-radius-sm);
  overflow: hidden;
}
.sql-editor-shell.is-fullscreen :deep(.cm-editor),
.sql-editor-shell:fullscreen :deep(.cm-editor),
.sql-editor-shell:-webkit-full-screen :deep(.cm-editor) {
  min-height: 0;
  height: 100%;
}
</style>

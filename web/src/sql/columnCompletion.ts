import type { Completion, CompletionContext, CompletionSource } from '@codemirror/autocomplete'
import { syntaxTree } from '@codemirror/language'
import type { CompletionSchemaPayload, EditorSqlSchema } from '@/sql/schema'

/** 扁平字段目录，用于顶层/限定名列补全。 */
export interface ColumnCatalogEntry {
  schema: string
  table: string
  column: string
}

/** 从补全 payload 构建字段目录。 */
export function buildColumnCatalog(payload?: CompletionSchemaPayload | null): ColumnCatalogEntry[] {
  if (!payload?.schemas) return []
  const catalog: ColumnCatalogEntry[] = []
  for (const [schema, tables] of Object.entries(payload.schemas)) {
    for (const [table, columns] of Object.entries(tables || {})) {
      for (const column of columns || []) {
        if (column) catalog.push({ schema, table, column })
      }
    }
  }
  return catalog
}

/** 从编辑器嵌套 schema 还原字段目录。 */
export function catalogFromEditorSchema(schema?: EditorSqlSchema | null): ColumnCatalogEntry[] {
  if (!schema) return []
  const catalog: ColumnCatalogEntry[] = []

  for (const [name, value] of Object.entries(schema)) {
    if (!value || typeof value !== 'object') continue

    if (Array.isArray(value)) {
      for (const col of value) {
        const column = typeof col === 'string' ? col : col.label
        if (column) catalog.push({ schema: '', table: name, column })
      }
      continue
    }

    if (!('children' in value)) continue
    const children = value.children

    if (Array.isArray(children)) {
      for (const col of children) {
        const column = typeof col === 'string' ? col : col.label
        if (column) catalog.push({ schema: '', table: name, column })
      }
      continue
    }

    if (!children || typeof children !== 'object') continue
    for (const [table, tableValue] of Object.entries(children)) {
      if (!tableValue || typeof tableValue !== 'object') continue
      const cols = Array.isArray(tableValue)
        ? tableValue
        : ('children' in tableValue && Array.isArray(tableValue.children) ? tableValue.children : [])
      for (const col of cols) {
        const column = typeof col === 'string' ? col : col.label
        if (column) catalog.push({ schema: name, table, column })
      }
    }
  }

  return catalog
}

function stripQuotes(name: string) {
  return name.replace(/^[`"\[]|[`"\]]$/g, '')
}

/** 解析当前语句 FROM/JOIN 中的表名。 */
function tablesInStatement(context: CompletionContext): string[] {
  const inner = syntaxTree(context.state).resolveInner(context.pos, -1)
  let statement: typeof inner | null = inner
  while (statement && statement.name !== 'Statement') statement = statement.parent
  if (!statement) return []

  const text = context.state.doc.sliceString(statement.from, statement.to)
  const tables: string[] = []
  const seen = new Set<string>()
  const re = /\b(?:from|join)\s+([`"]?)(\w+)\1(?:\s*\.\s*([`"]?)(\w+)\3)?/gi
  let match: RegExpExecArray | null
  while ((match = re.exec(text))) {
    const table = match[4] || match[2]
    const key = table.toLowerCase()
    if (seen.has(key)) continue
    seen.add(key)
    tables.push(table)
  }
  return tables
}

function matchTable(catalog: ColumnCatalogEntry[], tableName: string, defaultSchema?: string) {
  const needle = stripQuotes(tableName).toLowerCase()
  const preferred = defaultSchema?.toLowerCase()
  const hits = catalog.filter((item) => item.table.toLowerCase() === needle)
  if (!hits.length) return []
  if (preferred) {
    const inDefault = hits.filter((item) => !item.schema || item.schema.toLowerCase() === preferred)
    if (inDefault.length) return inDefault
  }
  return hits
}

function toCompletion(entry: ColumnCatalogEntry, boost: number): Completion {
  const detail = entry.schema ? `${entry.schema}.${entry.table}` : entry.table
  return {
    label: entry.column,
    type: 'property',
    detail,
    boost,
  }
}

/**
 * 顶层字段补全：FROM/JOIN 中的表优先；否则提供默认库字段（较低权重）。
 * 同时在 `表名.` 后做大小写不敏感的列提示。
 */
export function createColumnCompletionSource(
  getCatalog: () => ColumnCatalogEntry[],
  getDefaultSchema: () => string | undefined,
): CompletionSource {
  return (context) => {
    const catalog = getCatalog()
    if (!catalog.length) return null

    const word = context.matchBefore(/`?\w*$/)
    if (!word && !context.explicit) return null
    const from = word?.from ?? context.pos
    const typed = word ? stripQuotes(word.text) : ''

    // `table.` / `schema.table.` 限定补全（大小写不敏感）
    if (from > 0 && context.state.sliceDoc(from - 1, from) === '.') {
      const parent = context.state.sliceDoc(Math.max(0, from - 128), from - 1)
      const parts = parent.match(/(`?\w+`?)(?:\.(`?\w+`?))?$/)
      if (!parts) return null
      const tableToken = parts[2] || parts[1]
      const schemaToken = parts[2] ? parts[1] : undefined
      let entries = matchTable(catalog, tableToken, getDefaultSchema())
      if (schemaToken) {
        const schemaNeedle = stripQuotes(schemaToken).toLowerCase()
        entries = entries.filter((item) => item.schema.toLowerCase() === schemaNeedle)
      }
      if (!entries.length) return null
      return {
        from,
        options: entries.map((item) => toCompletion(item, 2)),
        validFor: /^`?\w*$/,
      }
    }

    // 顶层：至少输入一个字符，或显式触发（Ctrl+Space）
    if (!typed && !context.explicit) return null

    const referenced = tablesInStatement(context)
    const defaultSchema = getDefaultSchema()
    const options: Completion[] = []
    const seen = new Set<string>()

    const push = (entry: ColumnCatalogEntry, boost: number) => {
      const key = `${entry.schema}.${entry.table}.${entry.column}`.toLowerCase()
      if (seen.has(key)) return
      if (typed && !entry.column.toLowerCase().startsWith(typed.toLowerCase())) return
      seen.add(key)
      options.push(toCompletion(entry, boost))
    }

    if (referenced.length) {
      for (const table of referenced) {
        for (const entry of matchTable(catalog, table, defaultSchema)) push(entry, 1)
      }
    }

    // 无 FROM 表时仍提示字段，权重低于关键字/表
    if (!options.length) {
      const preferred = defaultSchema?.toLowerCase()
      for (const entry of catalog) {
        if (preferred && entry.schema && entry.schema.toLowerCase() !== preferred) continue
        push(entry, -20)
        if (options.length >= 800) break
      }
    }

    if (!options.length) return null
    return { from, options, validFor: /^`?\w*$/ }
  }
}

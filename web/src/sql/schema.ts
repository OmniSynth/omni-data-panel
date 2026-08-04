import type { Completion } from '@codemirror/autocomplete'
import type { SqlDialectId } from './dialects'

/** 后端返回的 SQL 补全目录。 */
export interface CompletionSchemaPayload {
  dialect: SqlDialectId | string
  schemas: Record<string, Record<string, string[]>>
}

/** CodeMirror SQL 嵌套命名空间（库 → 表 → 字段）。 */
export type EditorSqlSchema = {
  [name: string]: EditorSqlSchema | readonly string[] | { self: Completion; children: EditorSqlSchema | readonly string[] }
}

/**
 * 将后端模式目录转换为 CodeMirror 嵌套 schema。
 * 库名使用 type=namespace（显示为「库」），表名使用 type=type（显示为「表」）。
 */
export function toEditorSchema(
  payload?: CompletionSchemaPayload | null,
): EditorSqlSchema {
  if (!payload?.schemas) return {}
  const result: EditorSqlSchema = {}

  for (const [schemaName, tables] of Object.entries(payload.schemas)) {
    const tableChildren: EditorSqlSchema = {}
    for (const [tableName, columns] of Object.entries(tables || {})) {
      tableChildren[tableName] = {
        self: { label: tableName, type: 'type' },
        children: columns || [],
      }
    }

    if (!schemaName) {
      Object.assign(result, tableChildren)
      continue
    }

    result[schemaName] = {
      self: { label: schemaName, type: 'namespace' },
      children: tableChildren,
    }
  }

  return result
}

/** 统计补全目录中的表数量。 */
export function countCompletionTables(payload?: CompletionSchemaPayload | null): number {
  if (!payload?.schemas) return 0
  let count = 0
  for (const tables of Object.values(payload.schemas)) {
    count += Object.keys(tables || {}).length
  }
  return count
}

/**
 * 推断编辑器 defaultSchema：优先数据源默认库，其次仅有一个业务库时使用该库。
 */
export function inferDefaultSchema(
  payload?: CompletionSchemaPayload | null,
  preferred?: string | null,
): string | undefined {
  const preferredName = preferred?.trim()
  if (preferredName) return preferredName
  const names = Object.keys(payload?.schemas || {}).filter(Boolean)
  return names.length === 1 ? names[0] : undefined
}

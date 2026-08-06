/** SQL 占位符解析：支持 `:name` 命名参数与裸 `?` 顺序参数。 */

const IDENT_START = /[A-Za-z_]/
const IDENT_PART = /[A-Za-z0-9_]/

type ScanState = {
  inSingle: boolean
  inDouble: boolean
  inBacktick: boolean
}

function advanceString(sql: string, i: number, quote: "'" | '"' | '`', state: ScanState): number {
  const ch = sql[i]
  if (quote === "'" && state.inSingle) {
    if (ch === "'") {
      if (sql[i + 1] === "'") return i + 1
      state.inSingle = false
    }
    return i
  }
  if (quote === '"' && state.inDouble) {
    if (ch === '"') {
      if (sql[i + 1] === '"') return i + 1
      state.inDouble = false
    }
    return i
  }
  if (quote === '`' && state.inBacktick) {
    if (ch === '`') {
      if (sql[i + 1] === '`') return i + 1
      state.inBacktick = false
    }
    return i
  }
  return i
}

/** 粗略统计 SQL 中的裸 `?` 个数（忽略字符串字面量）。 */
export function countSqlPlaceholders(sql: string): number {
  let count = 0
  const state: ScanState = { inSingle: false, inDouble: false, inBacktick: false }
  for (let i = 0; i < sql.length; i++) {
    const ch = sql[i]
    if (state.inSingle) {
      i = advanceString(sql, i, "'", state)
      continue
    }
    if (state.inDouble) {
      i = advanceString(sql, i, '"', state)
      continue
    }
    if (state.inBacktick) {
      i = advanceString(sql, i, '`', state)
      continue
    }
    if (ch === "'") {
      state.inSingle = true
      continue
    }
    if (ch === '"') {
      state.inDouble = true
      continue
    }
    if (ch === '`') {
      state.inBacktick = true
      continue
    }
    if (ch === '?') count++
  }
  return count
}

/**
 * 按出现顺序提取 `:name`（去重保序），忽略字符串与 `::`。
 */
export function extractNamedPlaceholders(sql: string): string[] {
  const names: string[] = []
  const seen = new Set<string>()
  const state: ScanState = { inSingle: false, inDouble: false, inBacktick: false }
  for (let i = 0; i < sql.length; i++) {
    const ch = sql[i]
    if (state.inSingle) {
      i = advanceString(sql, i, "'", state)
      continue
    }
    if (state.inDouble) {
      i = advanceString(sql, i, '"', state)
      continue
    }
    if (state.inBacktick) {
      i = advanceString(sql, i, '`', state)
      continue
    }
    if (ch === "'") {
      state.inSingle = true
      continue
    }
    if (ch === '"') {
      state.inDouble = true
      continue
    }
    if (ch === '`') {
      state.inBacktick = true
      continue
    }
    if (ch === ':' && sql[i + 1] === ':') {
      i += 1
      continue
    }
    if (ch === ':' && sql[i + 1] && IDENT_START.test(sql[i + 1])) {
      let end = i + 2
      while (end < sql.length && IDENT_PART.test(sql[end])) end += 1
      const name = sql.slice(i + 1, end)
      if (!seen.has(name)) {
        seen.add(name)
        names.push(name)
      }
      i = end - 1
    }
  }
  return names
}

/** 将顺序参数数组对齐到裸 `?` 个数。 */
export function alignSqlParameters(sql: string, parameters: unknown[]): unknown[] {
  const size = countSqlPlaceholders(sql)
  const next = parameters.slice(0, size)
  while (next.length < size) next.push('')
  return next
}

/** 按命名列表对齐命名参数映射。 */
export function alignNamedParameters(
  sql: string,
  named: Record<string, unknown> | undefined | null,
): Record<string, unknown> {
  const names = extractNamedPlaceholders(sql)
  const source = named && typeof named === 'object' ? named : {}
  const next: Record<string, unknown> = {}
  for (const name of names) {
    next[name] = source[name] ?? ''
  }
  return next
}

export function hasNamedPlaceholders(sql: string): boolean {
  return extractNamedPlaceholders(sql).length > 0
}

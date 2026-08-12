/**
 * 将 Metabase 模板 SQL 转为 Omni `:name` 命名占位写法。
 * 可选块 [[...]] 始终保留内层；Field Filter / Snippet / 嵌套卡片仅告警。
 */

const IDENT = /^[A-Za-z_][A-Za-z0-9_]*$/
const OPTIONAL_COMMENT = '-- omni: 原 Metabase 可选块，已始终保留'

export type MetabaseConvertResult = {
  sql: string
  defaults: Record<string, string>
  warnings: string[]
  changed: boolean
}

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

/** 在非字符串位置扫描，调用 onCode 处理普通字符下标 */
function scanOutsideStrings(sql: string, onCode: (i: number, ch: string) => void) {
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
    onCode(i, ch)
  }
}

/** 查找下一个匹配的 `]]`（支持嵌套 `[[`），均在非字符串位置 */
function findClosingOptional(sql: string, openEnd: number): number {
  let depth = 1
  let closeAt = -1
  scanOutsideStrings(sql.slice(openEnd), (rel, ch) => {
    if (closeAt >= 0) return
    const i = openEnd + rel
    if (ch === '[' && sql[i + 1] === '[') {
      depth += 1
      return
    }
    if (ch === ']' && sql[i + 1] === ']') {
      depth -= 1
      if (depth === 0) closeAt = i
    }
  })
  return closeAt
}

/** 查找下一个匹配的 `}}`（从 `{{` 内容起点起） */
function findClosingMustache(sql: string, contentStart: number): number {
  let closeAt = -1
  scanOutsideStrings(sql.slice(contentStart), (rel, ch) => {
    if (closeAt >= 0) return
    const i = contentStart + rel
    if (ch === '}' && sql[i + 1] === '}') closeAt = i
  })
  return closeAt
}

function collectOptionalOpens(sql: string): number[] {
  const opens: number[] = []
  scanOutsideStrings(sql, (i, ch) => {
    if (ch === '[' && sql[i + 1] === '[') opens.push(i)
  })
  return opens
}

/**
 * 展开可选块：去掉 [[ ]]，块前加注释；由内向外处理以免下标错乱。
 */
function expandOptionalBlocks(sql: string, warnings: string[]): { sql: string; changed: boolean } {
  let next = sql
  let changed = false
  // 反复处理最内层，直到没有可选块
  while (true) {
    const opens = collectOptionalOpens(next)
    if (!opens.length) break

    // 选最右侧仍能闭合的开括号作为最内层候选：从后往前找第一个能闭合的
    let open = -1
    let close = -1
    for (let k = opens.length - 1; k >= 0; k--) {
      const o = opens[k]
      const c = findClosingOptional(next, o + 2)
      if (c < 0) continue
      // 确认中间没有未闭合的更内层开括号——若 o 到 c 之间还有 [[，则不是最内层
      const inner = next.slice(o + 2, c)
      const innerOpens = collectOptionalOpens(inner)
      if (innerOpens.length) continue
      open = o
      close = c
      break
    }

    if (open < 0 || close < 0) {
      warnings.push('存在未闭合的 Metabase 可选块 [[...]]，已跳过')
      break
    }

    const inner = next.slice(open + 2, close)
    const before = next.slice(0, open)
    const after = next.slice(close + 2)
    const needNewline = before.length > 0 && !/\n\s*$/.test(before)
    const prefix = `${needNewline ? '\n' : ''}${OPTIONAL_COMMENT}\n`
    next = `${before}${prefix}${ensureOptionalConjunction(inner)}${after}`
    changed = true
  }
  return { sql: next, changed }
}

/**
 * Metabase 可选块常写成 `[[col = {{x}}]]`（无 AND）；展开后接到 WHERE 会语法错误。
 * 已有 AND/OR 或以逗号开头（SELECT 列表）的保持原样。
 */
function ensureOptionalConjunction(inner: string): string {
  const trimmed = inner.replace(/^\s+/, '')
  if (!trimmed) return inner
  if (trimmed.startsWith(',')) return inner
  if (/^(and|or)\b/i.test(trimmed)) return inner
  const leading = inner.length - trimmed.length
  return `${inner.slice(0, leading)}AND ${trimmed}`
}

type TagKind =
  | { kind: 'variable'; name: string; defaultValue?: string }
  | { kind: 'unsupported'; raw: string; reason: string }

/** 解析 `{{...}}` 内文本 */
function classifyTag(raw: string): TagKind {
  const body = raw.trim()
  if (!body) {
    return { kind: 'unsupported', raw, reason: '空模板标签' }
  }
  const lower = body.toLowerCase()
  if (body.startsWith('#')) {
    return { kind: 'unsupported', raw: body, reason: `嵌套卡片/引用「${body}」不支持，请手改` }
  }
  if (lower.startsWith('snippet:') || lower.includes('snippet:')) {
    return { kind: 'unsupported', raw: body, reason: `Snippet「${body}」不支持，请手改` }
  }
  // Field Filter / 特殊类型：含空格且非 name=default 简单形式，或显式 type 前缀
  if (/^(dimension|field|date|number|text|temporal-unit)\s*:/i.test(body)) {
    return { kind: 'unsupported', raw: body, reason: `Field Filter「${body}」不支持，请手改` }
  }

  const eq = body.indexOf('=')
  const namePart = (eq >= 0 ? body.slice(0, eq) : body).trim()
  const defaultValue = eq >= 0 ? body.slice(eq + 1).trim() : undefined

  if (!IDENT.test(namePart)) {
    return {
      kind: 'unsupported',
      raw: body,
      reason: `非法参数名「${namePart || body}」，仅支持字母/数字/下划线`,
    }
  }
  // 名称合法但整体含奇怪分隔（如 name:xxx）且无 = 时，可能是特殊标签
  if (eq < 0 && body.includes(':')) {
    return { kind: 'unsupported', raw: body, reason: `特殊模板标签「${body}」不支持，请手改` }
  }

  return {
    kind: 'variable',
    name: namePart,
    defaultValue: defaultValue !== undefined && defaultValue !== '' ? defaultValue : undefined,
  }
}

/**
 * 将 `{{var}}` / `{{var=默认}}` 转为 `:var`；不支持标签保留原文并告警。
 */
function convertMustacheTags(
  sql: string,
  defaults: Record<string, string>,
  warnings: string[],
): { sql: string; changed: boolean } {
  const parts: string[] = []
  let last = 0
  let changed = false
  const opens: number[] = []
  scanOutsideStrings(sql, (i, ch) => {
    if (ch === '{' && sql[i + 1] === '{') opens.push(i)
  })

  for (const open of opens) {
    if (open < last) continue
    const contentStart = open + 2
    const close = findClosingMustache(sql, contentStart)
    if (close < 0) {
      warnings.push('存在未闭合的 Metabase 模板标签 {{...}}，已跳过')
      continue
    }
    const raw = sql.slice(contentStart, close)
    const tag = classifyTag(raw)
    parts.push(sql.slice(last, open))
    if (tag.kind === 'variable') {
      parts.push(`:${tag.name}`)
      if (tag.defaultValue !== undefined && defaults[tag.name] === undefined) {
        defaults[tag.name] = tag.defaultValue
      }
      changed = true
    } else {
      parts.push(`{{${raw}}}`)
      warnings.push(tag.reason)
    }
    last = close + 2
  }
  parts.push(sql.slice(last))
  return { sql: parts.join(''), changed }
}

/** Metabase 模板 SQL → Omni 命名占位 */
export function convertMetabaseSql(sql: string): MetabaseConvertResult {
  const warnings: string[] = []
  const defaults: Record<string, string> = {}
  if (!sql) {
    return { sql, defaults, warnings, changed: false }
  }

  const optional = expandOptionalBlocks(sql, warnings)
  const mustache = convertMustacheTags(optional.sql, defaults, warnings)
  const next = mustache.sql

  // 去重 warnings 保序
  const seen = new Set<string>()
  const uniqueWarnings: string[] = []
  for (const w of warnings) {
    if (seen.has(w)) continue
    seen.add(w)
    uniqueWarnings.push(w)
  }

  return {
    sql: next,
    defaults,
    warnings: uniqueWarnings,
    changed: next !== sql,
  }
}

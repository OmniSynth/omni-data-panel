import { formatDateTime } from '@/display'
import type { TableColumnFormat, TableColumnStyle, TableRowRule, TableStyle } from '@/dashboard/config'

const ISO_LIKE = /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}/

/** 将单元格值格式化为展示文本 */
export function formatTableCell(value: unknown, format: TableColumnFormat = 'auto'): string {
  if (value == null) return ''
  switch (format) {
    case 'text':
      return String(value)
    case 'number':
      return formatNumber(value)
    case 'percent':
      return formatPercent(value)
    case 'datetime':
      return formatDateTime(value as string | number | Date)
    case 'boolean':
      return formatBoolean(value)
    case 'link':
      return String(value).trim()
    case 'auto':
    default:
      if (typeof value === 'string' && ISO_LIKE.test(value)) return formatDateTime(value)
      if (typeof value === 'boolean') return formatBoolean(value)
      return String(value)
  }
}

function formatNumber(value: unknown): string {
  const num = typeof value === 'number' ? value : Number(String(value).replace(/,/g, ''))
  if (!Number.isFinite(num)) return String(value)
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 6 }).format(num)
}

function formatPercent(value: unknown): string {
  const num = typeof value === 'number' ? value : Number(String(value).replace(/,/g, '').replace(/%$/, ''))
  if (!Number.isFinite(num)) return String(value)
  const ratio = Math.abs(num) > 1 ? num / 100 : num
  return new Intl.NumberFormat(undefined, { style: 'percent', maximumFractionDigits: 2 }).format(ratio)
}

function formatBoolean(value: unknown): string {
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  const text = String(value).trim().toLowerCase()
  if (['1', 'true', 'yes', 'y', '是'].includes(text)) return 'true'
  if (['0', 'false', 'no', 'n', '否'].includes(text)) return 'false'
  return String(value)
}

/**
 * 将单元格值解析为可点击链接；仅允许 http/https。
 * 无协议的域名会补全为 https://；非法值返回 null。
 */
export function resolveLinkHref(value: unknown): string | null {
  if (value == null) return null
  const raw = String(value).trim()
  if (!raw) return null
  let href = raw
  if (!/^[a-z][a-z0-9+.-]*:/i.test(href)) {
    href = `https://${href}`
  }
  try {
    const url = new URL(href)
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return null
    return url.toString()
  } catch {
    return null
  }
}

/** 读取列样式（无配置时返回空对象） */
export function columnStyleOf(style: TableStyle | null | undefined, column: string): TableColumnStyle {
  return style?.columns?.[column] || {}
}

function cellText(value: unknown): string {
  if (value == null) return ''
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  return String(value).trim()
}

/** 将布尔语义值规范为 true/false；无法识别则返回 null */
function asBoolToken(value: unknown): 'true' | 'false' | null {
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  if (typeof value === 'number') {
    if (value === 1) return 'true'
    if (value === 0) return 'false'
    return null
  }
  const text = String(value ?? '').trim().toLowerCase()
  if (['1', 'true', 'yes', 'y', '是'].includes(text)) return 'true'
  if (['0', 'false', 'no', 'n', '否'].includes(text)) return 'false'
  return null
}

/** 判断行是否命中单条规则 */
export function matchRowRule(row: Record<string, unknown>, rule: TableRowRule): boolean {
  const rawValue = row[rule.field]
  const raw = cellText(rawValue)
  const expected = (rule.value ?? '').trim()
  const rawBool = asBoolToken(rawValue)
  const expectedBool = asBoolToken(expected)
  switch (rule.op) {
    case 'EQ':
      if (rawBool && expectedBool) return rawBool === expectedBool
      return raw === expected || raw.toLowerCase() === expected.toLowerCase()
    case 'NE':
      if (rawBool && expectedBool) return rawBool !== expectedBool
      return raw !== expected && raw.toLowerCase() !== expected.toLowerCase()
    case 'LIKE':
      return raw.toLowerCase().includes(expected.toLowerCase())
    default:
      return false
  }
}

/** 按规则顺序返回首条命中的行背景色 */
export function resolveRowBackground(
  row: Record<string, unknown>,
  style?: TableStyle | null,
): string | undefined {
  const rules = style?.rowRules
  if (!rules?.length) return undefined
  for (const rule of rules) {
    if (matchRowRule(row, rule)) return rule.background
  }
  return undefined
}
